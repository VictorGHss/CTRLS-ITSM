package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord;

import java.util.List;
import java.util.ArrayList;
import java.util.EnumSet;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.DiscordTicketPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Adapter that implements DiscordTicketPort using JDA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordTicketAdapter implements DiscordTicketPort {

    private final ObjectProvider<JDA> jdaProvider;
    private final TicketRepositoryPort ticketRepository;

    @Value("${discord.bot.guild-id:}")
    private String discordGuildId;

    private static final String ACTIVE_CATEGORY_ID = "1526959210863001751";
    private static final String ARCHIVED_CATEGORY_ID = "1526959741585063957";
    private static final int CLINIC_BRAND_COLOR = 0xF97316; // Cor Laranja de Destaque Inovare TI (#F97316)

    private final java.util.Set<java.util.UUID> processingResolution = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    @Override
    @Transactional(readOnly = true)
    public void createTicketChannel(Ticket ticketParam, List<String> discordUserIds) {
        final Ticket ticket = ticketRepository.findByIdWithRelations(ticketParam.getId()).orElse(ticketParam);
        log.info("[DISCORD-TICKET] Iniciando criação de canal para chamado #{} com {} usuários designados.",
                ticket.getNumber(), discordUserIds.size());

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("[DISCORD-TICKET] JDA indisponível. Criação do canal para chamado #{} ignorada.", ticket.getNumber());
            return;
        }

        Guild guild = resolveGuild(jda);
        if (guild == null) {
            log.warn("[DISCORD-TICKET] Guilda não encontrada. Criação do canal para chamado #{} abortada.", ticket.getNumber());
            return;
        }

        Category activeCategory = guild.getCategoryById(ACTIVE_CATEGORY_ID);
        if (activeCategory == null) {
            log.warn("[DISCORD-TICKET] Categoria de chamados ativos ({}) não encontrada.", ACTIVE_CATEGORY_ID);
            return;
        }

        // Pre-resolve dados na thread síncrona com transação ativa para evitar LazyInitializationException no worker do Discord
        final String preResolvedRequesterName = safeGetRequesterName(ticket);
        final String preResolvedSectorName = safeGetSectorName(ticket);
        final String preResolvedCategoryName = safeGetCategoryName(ticket);
        final String preResolvedRelatedTicketsSummary = safeGetRelatedTicketsSummary(ticket);

        // Resolve membros a serem permitidos no canal
        Member requesterMember = null;
        br.dev.ctrls.inovareti.modules.user.domain.model.User requester = ticket.getRequester();
        if (requester != null) {
            String requesterDiscordId = requester.getDiscordUserId();
            if (requesterDiscordId != null && !requesterDiscordId.isBlank()) {
                try {
                    requesterMember = guild.retrieveMemberById(java.util.Objects.requireNonNull(requesterDiscordId.trim())).complete();
                } catch (Exception ex) {
                    log.warn("[DISCORD-TICKET] Não foi possível carregar criador do chamado no Discord: {}", requesterDiscordId, ex);
                }
            }
        }

        List<Member> allowedMembers = new ArrayList<>();
        for (String discordId : discordUserIds) {
            if (discordId != null && !discordId.isBlank()) {
                try {
                    Member m = guild.retrieveMemberById(java.util.Objects.requireNonNull(discordId.trim())).complete();
                    if (m != null) {
                        allowedMembers.add(m);
                    }
                } catch (Exception ex) {
                    log.warn("[DISCORD-TICKET] Não foi possível carregar membro designado no Discord: {}", discordId, ex);
                }
            }
        }

        String shortNum = ticket.getNumber() != null ? ticket.getNumber().toLowerCase() : "ticket";
        String suffix = "";
        if (ticket.getTitle() != null && !ticket.getTitle().isBlank()) {
            String normalized = java.text.Normalizer.normalize(ticket.getTitle(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9\\s-]", "")
                    .replaceAll("\\s+", "-")
                    .replaceAll("-+", "-")
                    .trim();
            if (!normalized.isEmpty()) {
                suffix = "-" + normalized;
            }
        }

        String channelName = "ticket-" + shortNum + suffix;
        if (channelName.length() > 30) {
            channelName = channelName.substring(0, 30).replaceAll("-$", "");
        }

        // Configura ações de override de permissão
        var channelAction = activeCategory.createTextChannel(java.util.Objects.requireNonNull(channelName))
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL));

        if (requesterMember != null) {
            channelAction = channelAction.addPermissionOverride(java.util.Objects.requireNonNull(requesterMember),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null);
        }

        for (Member member : allowedMembers) {
            channelAction = channelAction.addPermissionOverride(java.util.Objects.requireNonNull(member),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null);
        }

        channelAction.queue(
                channel -> {
                    log.info("[DISCORD-TICKET] Canal privado criado com sucesso: #{} (ID: {}) para chamado #{}",
                            channel.getName(), channel.getId(), ticket.getNumber());
                    sendAndPinInitialTicketMessage(channel, ticket, preResolvedRequesterName, preResolvedSectorName, preResolvedCategoryName, preResolvedRelatedTicketsSummary);
                },
                error -> log.error("[DISCORD-TICKET] Falha ao criar canal privado para chamado #{}", ticket.getNumber(), error)
        );
    }

    @SuppressWarnings("null")
    private void sendAndPinInitialTicketMessage(
            TextChannel channel,
            Ticket ticket,
            String preResolvedRequesterName,
            String preResolvedSectorName,
            String preResolvedCategoryName,
            String preResolvedRelatedTicketsSummary) {
        try {
            net.dv8tion.jda.api.EmbedBuilder eb = new net.dv8tion.jda.api.EmbedBuilder();
            String ticketNum = ticket.getNumber() != null ? ticket.getNumber() : "-";
            String ticketTitle = ticket.getTitle() != null ? ticket.getTitle() : "Sem título";
            eb.setTitle("🎫 Chamado #" + ticketNum + " - " + DiscordLgpdSanitizer.sanitize(ticketTitle));
            eb.setColor(CLINIC_BRAND_COLOR);

            String rawDescription = ticket.getDescription();
            if (rawDescription == null || rawDescription.isBlank()) {
                rawDescription = ticket.getTitle();
            }
            String sanitizedDescription = DiscordLgpdSanitizer.sanitize(rawDescription);
            eb.setDescription("**Descrição do Problema:**\n" + (sanitizedDescription != null ? sanitizedDescription : "-"));

            String requesterName = preResolvedRequesterName != null ? preResolvedRequesterName : safeGetRequesterName(ticket);
            String requesterSector = preResolvedSectorName != null ? preResolvedSectorName : safeGetSectorName(ticket);
            String categoryName = preResolvedCategoryName != null ? preResolvedCategoryName : safeGetCategoryName(ticket);
            String priority = ticket.getPriority() != null ? ticket.getPriority().toString() : "-";
            String status = ticket.getStatus() != null ? ticket.getStatus().toString() : "OPEN";

            eb.addField("Solicitante", requesterName, true);
            eb.addField("Setor", requesterSector, true);
            eb.addField("Categoria", categoryName, true);
            eb.addField("Prioridade", priority, true);
            eb.addField("Status", status, true);

            if (ticket.getSlaDeadline() != null) {
                String formattedSla = java.util.Objects.requireNonNullElse(
                        ticket.getSlaDeadline().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), "-");
                eb.addField("Prazo SLA", formattedSla, true);
            }

            String relatedSummary = preResolvedRelatedTicketsSummary != null ? preResolvedRelatedTicketsSummary : safeGetRelatedTicketsSummary(ticket);
            if (relatedSummary != null && !relatedSummary.isBlank()) {
                eb.addField("🔗 Chamados Vinculados", relatedSummary, false);
            }

            String openedAt = ticket.getCreatedAt() != null
                    ? ticket.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "-";
            eb.setFooter("Inovare TI • Chamado aberto em: " + openedAt);
            eb.setTimestamp(java.time.Instant.now());

            net.dv8tion.jda.api.interactions.components.buttons.Button btnAssumir =
                    net.dv8tion.jda.api.interactions.components.buttons.Button.primary("ticket:assumir:" + ticket.getId(), "👤 Assumir Chamado");
            net.dv8tion.jda.api.interactions.components.buttons.Button btnResolver =
                    net.dv8tion.jda.api.interactions.components.buttons.Button.success("ticket:resolver:" + ticket.getId(), "✅ Resolver Chamado");

            channel.sendMessageEmbeds(eb.build())
                   .setActionRow(btnAssumir, btnResolver)
                   .queue(
                    message -> message.pin().queue(
                            v -> log.info("[DISCORD-TICKET] Mensagem inicial de detalhes fixada no canal #{}", channel.getName()),
                            pinErr -> log.warn("[DISCORD-TICKET] Falha ao fixar mensagem inicial no canal #{}: {}", channel.getName(), pinErr.getMessage())
                    ),
                    sendErr -> log.error("[DISCORD-TICKET] Falha ao enviar embed inicial para o canal #{}: {}", channel.getName(), sendErr.getMessage())
            );
        } catch (Exception ex) {
            log.error("[DISCORD-TICKET] Erro ao montar ou enviar mensagem inicial fixada no canal #{}", channel.getName(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public void archiveTicketChannel(Ticket ticketParam) {
        if (ticketParam == null || ticketParam.getId() == null) {
            return;
        }

        java.util.UUID ticketId = ticketParam.getId();
        if (!processingResolution.add(ticketId)) {
            log.info("[DISCORD-TICKET] Arquivamento do chamado #{} (ID: {}) já está em andamento ou foi concluído. Ignorando execução duplicada.",
                    ticketParam.getNumber(), ticketId);
            return;
        }

        try {
            Ticket dbTicket = ticketRepository.findByIdWithRelations(ticketParam.getId()).orElse(ticketParam);
            log.info("[DISCORD-TICKET] Iniciando arquivamento de canal para chamado #{}.", dbTicket.getNumber());

            JDA jda = jdaProvider.getIfAvailable();
            if (jda == null) {
                log.warn("[DISCORD-TICKET] JDA indisponível. Arquivamento do canal para chamado #{} ignorado.", dbTicket.getNumber());
                return;
            }

            Guild guild = resolveGuild(jda);
            if (guild == null) {
                log.warn("[DISCORD-TICKET] Guilda não encontrada. Arquivamento do canal para chamado #{} abortado.", dbTicket.getNumber());
                return;
            }

            Category baseArchivedCategory = guild.getCategoryById(ARCHIVED_CATEGORY_ID);
            Category targetArchivedCategory = resolveAvailableArchivedCategory(guild, baseArchivedCategory);
            if (targetArchivedCategory == null) {
                log.warn("[DISCORD-TICKET] Nenhuma categoria de arquivados disponível para o chamado #{}.", dbTicket.getNumber());
                return;
            }

            String prefix = "ticket-" + dbTicket.getNumber().toLowerCase();
            List<TextChannel> channels = new java.util.ArrayList<>();
            for (TextChannel tc : guild.getTextChannels()) {
                if (tc.getName().startsWith(prefix)) {
                    channels.add(tc);
                }
            }

            if (channels.isEmpty()) {
                log.warn("[DISCORD-TICKET] Nenhum canal encontrado com o prefixo '{}' para o chamado #{}.",
                        prefix, dbTicket.getNumber());
                return;
            }

            // Recupera o texto de solução preferencialmente da memória do evento (ticketParam) ou do banco (dbTicket)
            String rawSolution = ticketParam.getSolutionText();
            if (rawSolution == null || rawSolution.isBlank()) {
                rawSolution = dbTicket.getSolutionText();
            }
            if (rawSolution == null || rawSolution.isBlank()) {
                if (dbTicket.getAsset() != null) {
                    rawSolution = "Ativo baixado: Patrimônio " + dbTicket.getAsset().getPatrimonyCode();
                } else if (ticketParam.getAsset() != null) {
                    rawSolution = "Ativo baixado: Patrimônio " + ticketParam.getAsset().getPatrimonyCode();
                } else {
                    rawSolution = "Chamado resolvido com sucesso.";
                }
            }

            for (TextChannel channel : channels) {
                // Envia e fixa embed de solução do chamado com botão de reabertura
                net.dv8tion.jda.api.EmbedBuilder eb = new net.dv8tion.jda.api.EmbedBuilder();
                String ticketNum = dbTicket.getNumber() != null ? dbTicket.getNumber() : "-";
                eb.setTitle("✅ Chamado #" + ticketNum + " - Solução do Chamado");
                eb.setColor(CLINIC_BRAND_COLOR);

                String sanitizedSolution = DiscordLgpdSanitizer.sanitize(rawSolution);
                eb.setDescription("**Solução Registrada:**\n" + (sanitizedSolution != null ? sanitizedSolution : "-"));

                br.dev.ctrls.inovareti.modules.user.domain.model.User assignedUser = dbTicket.getAssignedTo() != null ? dbTicket.getAssignedTo() : ticketParam.getAssignedTo();
                String assignedName = java.util.Objects.requireNonNullElse(
                        assignedUser != null ? DiscordLgpdSanitizer.sanitize(assignedUser.getName()) : "Equipe Inovare TI",
                        "Equipe Inovare TI");
                eb.addField("Atendido por", java.util.Objects.requireNonNullElse(assignedName, "Equipe Inovare TI"), true);

                var assetObj = dbTicket.getAsset() != null ? dbTicket.getAsset() : ticketParam.getAsset();
                if (assetObj != null) {
                    eb.addField("Ativo Baixado", "Patrimônio " + assetObj.getPatrimonyCode(), true);
                }

                java.time.LocalDateTime closedAt = dbTicket.getClosedAt() != null ? dbTicket.getClosedAt() : ticketParam.getClosedAt();
                String closedAtStr = closedAt != null
                        ? closedAt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        : java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                eb.setFooter("Inovare TI • Chamado resolvido em: " + closedAtStr);
                eb.setTimestamp(java.time.Instant.now());

                net.dv8tion.jda.api.interactions.components.buttons.Button btnReabrir =
                        net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("ticket:reabrir:" + dbTicket.getId(), "🔄 Reabrir Chamado");

                channel.sendMessageEmbeds(eb.build())
                       .setActionRow(btnReabrir)
                       .queue(
                        message -> message.pin().queue(
                                v -> log.info("[DISCORD-TICKET] Embed de solução do chamado #{} enviado e fixado no canal #{}", ticketNum, channel.getName()),
                                pinErr -> log.warn("[DISCORD-TICKET] Embed de solução enviado, mas falhou ao fixar no canal #{}: {}", channel.getName(), pinErr.getMessage())
                        ),
                        err -> log.error("[DISCORD-TICKET] Falha ao enviar embed de resolução para canal #{}: {}", channel.getName(), err.getMessage())
                );

                // Remove permissão de escrita de todos os membros humanos vinculados
                for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                    long targetId = override.getIdLong();
                    if (targetId != jda.getSelfUser().getIdLong()) {
                        try {
                            channel.getManager().putMemberPermissionOverride(
                                    targetId,
                                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                                    EnumSet.of(Permission.MESSAGE_SEND)
                            ).queue();
                        } catch (Exception ex) {
                            log.warn("[DISCORD-TICKET] Falha ao remover permissões de escrita para membro no canal #{}: {}", channel.getName(), ex.getMessage());
                        }
                    }
                }

                // Move para a categoria semanal de arquivados disponível com tratamento defensivo
                try {
                    channel.getManager().setParent(targetArchivedCategory).queue(
                            v -> log.info("[DISCORD-TICKET] Canal #{} movido com sucesso para a categoria de arquivados '{}' (ID: {}).",
                                    channel.getName(), targetArchivedCategory.getName(), targetArchivedCategory.getId()),
                            error -> {
                                log.error("[DISCORD-TICKET] Falha ao mover canal #{} para arquivados: {}", channel.getName(), error.getMessage());
                                if (error.getMessage() != null && error.getMessage().contains("50035")) {
                                    log.warn("[DISCORD-TICKET] Erro 50035 (limite 50 canais). Tentando criar/obter nova categoria de arquivados secundária.");
                                    Category fallbackCategory = resolveAvailableArchivedCategory(guild, null);
                                    if (fallbackCategory != null && !fallbackCategory.getId().equals(targetArchivedCategory.getId())) {
                                        channel.getManager().setParent(fallbackCategory).queue(
                                                v -> log.info("[DISCORD-TICKET] Canal #{} movido com sucesso para categoria alternativa '{}'", channel.getName(), fallbackCategory.getName()),
                                                e -> log.error("[DISCORD-TICKET] Falha final ao mover canal #{}: {}", channel.getName(), e.getMessage())
                                        );
                                    }
                                }
                            }
                    );
                } catch (Exception ex) {
                    log.error("[DISCORD-TICKET] Exceção ao agendar movimento do canal #{}: {}", channel.getName(), ex.getMessage());
                }
            }
        } finally {
            // Remove a trava de idempotência após 30 segundos
            java.util.concurrent.CompletableFuture.delayedExecutor(30, java.util.concurrent.TimeUnit.SECONDS)
                    .execute(() -> processingResolution.remove(ticketId));
        }
    }

    private String getWeeklyArchivedCategoryName(java.time.LocalDate date) {
        java.time.LocalDate sunday = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY));
        java.time.LocalDate saturday = date.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SATURDAY));

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        return "📁 ⁃ ARQUIVADOS (" + sunday.format(fmt) + " a " + saturday.format(fmt) + ")";
    }

    private synchronized Category resolveAvailableArchivedCategory(Guild guild, Category baseCategory) {
        String baseWeeklyName = getWeeklyArchivedCategoryName(java.time.LocalDate.now());

        // 1. Procura se a categoria semanal já existe na guilda com menos de 50 canais
        List<Category> matchingCategories = guild.getCategories().stream()
                .filter(cat -> cat.getName() != null && cat.getName().startsWith(baseWeeklyName))
                .sorted((c1, c2) -> c1.getName().compareTo(c2.getName()))
                .toList();

        for (Category cat : matchingCategories) {
            if (cat.getChannels().size() < 50) {
                log.info("[DISCORD-TICKET] Utilizando categoria semanal de arquivados existente '{}' (ID: {}, canais: {}/50)",
                        cat.getName(), cat.getId(), cat.getChannels().size());
                return cat;
            }
        }

        // 2. Se não existir ou todas estiverem com 50 canais, cria uma nova categoria semanal
        String newCategoryName;
        if (matchingCategories.isEmpty()) {
            newCategoryName = baseWeeklyName;
        } else {
            int nextIndex = matchingCategories.size() + 1;
            newCategoryName = baseWeeklyName + " - " + nextIndex;
        }

        if (newCategoryName.length() > 32) {
            newCategoryName = newCategoryName.substring(0, 32).trim();
        }
        log.info("[DISCORD-TICKET] Criando automaticamente nova categoria semanal de arquivados: '{}'", newCategoryName);

        try {
            Category newCategory = guild.createCategory(java.util.Objects.requireNonNull(newCategoryName)).complete();
            log.info("[DISCORD-TICKET] Categoria semanal de arquivados criada com sucesso: '{}' (ID: {})",
                    newCategory.getName(), newCategory.getId());
            return newCategory;
        } catch (Exception ex) {
            log.error("[DISCORD-TICKET] Falha ao criar categoria semanal '{}': {}", newCategoryName, ex.getMessage(), ex);
            return baseCategory != null ? baseCategory : guild.getCategoryById(ARCHIVED_CATEGORY_ID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void reopenTicketChannel(Ticket ticketParam) {
        Ticket ticket = ticketRepository.findByIdWithRelations(ticketParam.getId()).orElse(ticketParam);
        log.info("[DISCORD-TICKET] Reabrindo canal para o chamado #{}.", ticket.getNumber());

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) return;

        Guild guild = resolveGuild(jda);
        if (guild == null) return;

        Category activeCategory = guild.getCategoryById(ACTIVE_CATEGORY_ID);
        if (activeCategory == null) {
            log.warn("[DISCORD-TICKET] Categoria de chamados ativos não encontrada.");
            return;
        }

        String prefix = "ticket-" + ticket.getNumber().toLowerCase();
        List<TextChannel> channels = guild.getTextChannels().stream()
                .filter(tc -> tc.getName().startsWith(prefix))
                .toList();

        for (TextChannel channel : channels) {
            // Move de volta para a categoria de ativos
            try {
                channel.getManager().setParent(activeCategory).queue(
                        v -> log.info("[DISCORD-TICKET] Canal #{} movido de volta para a categoria de ativos.", channel.getName()),
                        err -> log.error("[DISCORD-TICKET] Falha ao mover canal #{} para ativos: {}", channel.getName(), err.getMessage())
                );
            } catch (Exception ex) {
                log.error("[DISCORD-TICKET] Erro ao mover canal #{} para categoria ativa: {}", channel.getName(), ex.getMessage());
            }

            // Restaura permissão de escrita de membros no canal
            for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                long targetId = override.getIdLong();
                if (targetId != jda.getSelfUser().getIdLong()) {
                    try {
                        channel.getManager().putMemberPermissionOverride(
                                targetId,
                                EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY),
                                null
                        ).queue();
                    } catch (Exception ex) {
                        log.warn("[DISCORD-TICKET] Falha ao restaurar permissão de escrita no canal #{}: {}", channel.getName(), ex.getMessage());
                    }
                }
            }

            // Envia notificação de reabertura no canal
            channel.sendMessage("🔄 **Chamado Reaberto!** Este chamado foi reaberto e está novamente em atendimento.").queue();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void notifyMerged(Ticket childTicket, Ticket parentTicket) {
        childTicket = ticketRepository.findById(childTicket.getId()).orElse(childTicket);
        parentTicket = ticketRepository.findById(parentTicket.getId()).orElse(parentTicket);
        log.info("[DISCORD-TICKET] Enviando notificação de unificação para canal do chamado filho #{}.", childTicket.getNumber());

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("[DISCORD-TICKET] JDA indisponível para notificar unificação.");
            return;
        }

        Guild guild = resolveGuild(jda);
        if (guild == null) {
            log.warn("[DISCORD-TICKET] Guilda não encontrada para notificar unificação.");
            return;
        }

        String prefix = "ticket-" + childTicket.getNumber().toLowerCase();
        List<TextChannel> channels = new java.util.ArrayList<>();
        for (TextChannel tc : guild.getTextChannels()) {
            if (tc.getName().startsWith(prefix)) {
                channels.add(tc);
            }
        }

        for (TextChannel channel : channels) {
            channel.sendMessage("🚨 Este chamado foi unificado ao Chamado Mestre #" + parentTicket.getNumber()).queue(
                    v -> log.info("[DISCORD-TICKET] Notificação de unificação enviada no canal #{}", channel.getName()),
                    err -> log.error("[DISCORD-TICKET] Erro ao enviar notificação de unificação no canal #{}", channel.getName(), err)
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void syncTicketChannelPermissions(Ticket ticket) {
        ticket = ticketRepository.findById(ticket.getId()).orElse(ticket);
        log.info("[DISCORD-TICKET] Sincronizando permissões do canal para chamado #{}.", ticket.getNumber());

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("[DISCORD-TICKET] JDA indisponível. Sincronização de permissões do canal para chamado #{} ignorada.", ticket.getNumber());
            return;
        }

        Guild guild = resolveGuild(jda);
        if (guild == null) {
            log.warn("[DISCORD-TICKET] Guilda não encontrada. Sincronização de permissões do canal para chamado #{} abortada.", ticket.getNumber());
            return;
        }

        String prefix = "ticket-" + ticket.getNumber().toLowerCase();
        List<TextChannel> channels = new java.util.ArrayList<>();
        for (TextChannel tc : guild.getTextChannels()) {
            if (tc.getName().startsWith(prefix)) {
                channels.add(tc);
            }
        }

        if (channels.isEmpty()) {
            log.warn("[DISCORD-TICKET] Nenhum canal encontrado com o prefixo '{}' para o chamado #{}.",
                    prefix, ticket.getNumber());
            return;
        }

        List<br.dev.ctrls.inovareti.modules.user.domain.model.User> candidates = new java.util.ArrayList<>();
        if (ticket.getRequester() != null) {
            candidates.add(ticket.getRequester());
        }
        if (ticket.getAssignedTo() != null) {
            candidates.add(ticket.getAssignedTo());
        }
        if (ticket.getAdditionalUsers() != null) {
            candidates.addAll(ticket.getAdditionalUsers());
        }

        List<Member> membersToPermit = new java.util.ArrayList<>();
        for (br.dev.ctrls.inovareti.modules.user.domain.model.User u : candidates) {
            if (u != null && u.getDiscordUserId() != null && !u.getDiscordUserId().isBlank()) {
                try {
                    Member m = guild.retrieveMemberById(java.util.Objects.requireNonNull(u.getDiscordUserId().trim())).complete();
                    if (m != null) {
                        membersToPermit.add(m);
                    }
                } catch (Exception ex) {
                    log.warn("[DISCORD-TICKET] Não foi possível carregar membro {} ({}) no Discord para sincronização de permissões.",
                            u.getName(), u.getDiscordUserId(), ex);
                }
            }
        }

        for (TextChannel channel : channels) {
            for (Member m : membersToPermit) {
                channel.upsertPermissionOverride(java.util.Objects.requireNonNull(m))
                        .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                        .queue(
                                v -> log.info("[DISCORD-TICKET] Permissões concedidas para {} no canal #{}", m.getUser().getAsTag(), channel.getName()),
                                err -> log.error("[DISCORD-TICKET] Falha ao upsert permissões para {} no canal #{}", m.getUser().getAsTag(), channel.getName(), err)
                        );
            }
        }
    }

    private Guild resolveGuild(JDA jda) {
        Guild guild = null;
        if (discordGuildId != null && !discordGuildId.isBlank()) {
            try {
                guild = jda.getGuildById(java.util.Objects.requireNonNull(discordGuildId.trim()));
            } catch (Exception ignored) {}
        }
        if (guild == null) {
            guild = jda.getGuilds().stream().findFirst().orElse(null);
        }
        return guild;
    }

    private String safeGetCategoryName(Ticket ticket) {
        if (ticket == null) return "Geral";
        try {
            if (ticket.getCategory() != null && ticket.getCategory().getName() != null && !ticket.getCategory().getName().isBlank()) {
                return ticket.getCategory().getName();
            }
        } catch (Exception ex) {
            log.warn("[DISCORD-TICKET] Não foi possível carregar categoria do chamado #{}: {}",
                    ticket.getNumber() != null ? ticket.getNumber() : "-", ex.getMessage());
        }
        return "Geral";
    }

    private String safeGetRequesterName(Ticket ticket) {
        if (ticket == null) return "-";
        try {
            if (ticket.getRequester() != null && ticket.getRequester().getName() != null) {
                String sanitized = DiscordLgpdSanitizer.sanitize(ticket.getRequester().getName());
                return sanitized != null ? sanitized : "-";
            }
        } catch (Exception ex) {
            log.warn("[DISCORD-TICKET] Não foi possível carregar solicitante do chamado #{}: {}",
                    ticket.getNumber() != null ? ticket.getNumber() : "-", ex.getMessage());
        }
        return "-";
    }

    private String safeGetSectorName(Ticket ticket) {
        if (ticket == null) return "Geral";
        try {
            if (ticket.getRequester() != null && ticket.getRequester().getSector() != null
                    && ticket.getRequester().getSector().getName() != null) {
                return ticket.getRequester().getSector().getName();
            }
        } catch (Exception ex) {
            log.warn("[DISCORD-TICKET] Não foi possível carregar setor do chamado #{}: {}",
                    ticket.getNumber() != null ? ticket.getNumber() : "-", ex.getMessage());
        }
        return "Geral";
    }

    private String safeGetRelatedTicketsSummary(Ticket ticket) {
        if (ticket == null) return "";
        try {
            if (ticket.getRelatedTickets() != null && !ticket.getRelatedTickets().isEmpty()) {
                return ticket.getRelatedTickets().stream()
                        .map(t -> "#" + (t.getNumber() != null ? t.getNumber() : "-") + " - " + DiscordLgpdSanitizer.sanitize(t.getTitle()))
                        .collect(java.util.stream.Collectors.joining("\n"));
            }
        } catch (Exception ex) {
            log.warn("[DISCORD-TICKET] Não foi possível carregar chamados relacionados para chamado #{}: {}",
                    ticket.getNumber() != null ? ticket.getNumber() : "-", ex.getMessage());
        }
        return "";
    }
}
