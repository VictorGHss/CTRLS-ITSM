package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.DiscordTicketPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ItemComponent;

/**
 * Adaptador que implementa DiscordTicketPort utilizando a biblioteca JDA para
 * integração com o Discord. Delega a construção visual de embeds e tópicos para DiscordTicketEmbedBuilder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class DiscordTicketAdapter implements DiscordTicketPort {

    private final ObjectProvider<JDA> jdaProvider;
    private final TicketRepositoryPort ticketRepository;
    private final DiscordTicketEmbedBuilder embedBuilder;

    @Value("${discord.bot.guild-id:}")
    private String discordGuildId;

    private static final String ACTIVE_CATEGORY_ID = "1526959210863001751";
    private static final String ARCHIVED_CATEGORY_ID = "1526959741585063957";

    private final Set<UUID> processingResolution = Collections.newSetFromMap(new ConcurrentHashMap<>());

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

        // Pre-resolve dados na thread síncrona com transação ativa para evitar LazyInitializationException
        final String preResolvedRequesterName = embedBuilder.safeGetRequesterName(ticket);
        final String preResolvedSectorName = embedBuilder.safeGetSectorName(ticket);
        final String preResolvedCategoryName = embedBuilder.safeGetCategoryName(ticket);
        final String preResolvedRelatedTicketsSummary = embedBuilder.safeGetRelatedTicketsSummary(ticket);

        // Resolve membros a serem permitidos no canal
        Member requesterMember = null;
        User requester = ticket.getRequester();
        if (requester != null && requester.getDiscordUserId() != null && !requester.getDiscordUserId().isBlank()) {
            try {
                requesterMember = guild.retrieveMemberById(Objects.requireNonNull(requester.getDiscordUserId().trim())).complete();
            } catch (Exception ex) {
                log.warn("[DISCORD-TICKET] Não foi possível carregar criador do chamado no Discord: {}", requester.getDiscordUserId(), ex);
            }
        }

        List<Member> allowedMembers = new ArrayList<>();
        for (String discordId : discordUserIds) {
            if (discordId != null && !discordId.isBlank()) {
                try {
                    Member m = guild.retrieveMemberById(Objects.requireNonNull(discordId.trim())).complete();
                    if (m != null) {
                        allowedMembers.add(m);
                    }
                } catch (Exception ex) {
                    log.warn("[DISCORD-TICKET] Não foi possível carregar membro designado no Discord: {}", discordId, ex);
                }
            }
        }

        String channelName = embedBuilder.formatChannelName(ticket);
        String channelTopic = embedBuilder.formatChannelTopic(ticket, preResolvedRequesterName, preResolvedSectorName);

        var channelAction = activeCategory.createTextChannel(Objects.requireNonNull(channelName))
                .setTopic(channelTopic)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL));

        if (requesterMember != null) {
            channelAction = channelAction.addPermissionOverride(Objects.requireNonNull(requesterMember),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null);
        }

        for (Member member : allowedMembers) {
            channelAction = channelAction.addPermissionOverride(Objects.requireNonNull(member),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null);
        }

        channelAction.queue(
                channel -> {
                    log.info("[DISCORD-TICKET] Canal privado criado com sucesso: #{} (ID: {}) para chamado #{}",
                            channel.getName(), channel.getId(), ticket.getNumber());
                    sendAndPinInitialTicketMessage(channel, ticket, preResolvedRequesterName, preResolvedSectorName,
                            preResolvedCategoryName, preResolvedRelatedTicketsSummary);
                },
                error -> log.error("[DISCORD-TICKET] Falha ao criar canal privado para chamado #{}", ticket.getNumber(), error));
    }

    private void sendAndPinInitialTicketMessage(
            TextChannel channel,
            Ticket ticket,
            String preResolvedRequesterName,
            String preResolvedSectorName,
            String preResolvedCategoryName,
            String preResolvedRelatedTicketsSummary) {
        try {
            MessageEmbed embed = embedBuilder.buildInitialTicketEmbed(
                    ticket, preResolvedRequesterName, preResolvedSectorName, preResolvedCategoryName, preResolvedRelatedTicketsSummary);
            ItemComponent[] actionButtons = embedBuilder.buildInitialActionButtons(ticket);

            channel.sendMessageEmbeds(embed)
                    .setActionRow(actionButtons)
                    .queue(
                            message -> message.pin().queue(
                                    v -> log.info("[DISCORD-TICKET] Mensagem inicial de detalhes fixada no canal #{}", channel.getName()),
                                    pinErr -> log.warn("[DISCORD-TICKET] Falha ao fixar mensagem inicial no canal #{}: {}", channel.getName(), pinErr.getMessage())),
                            sendErr -> log.error("[DISCORD-TICKET] Falha ao enviar embed inicial para o canal #{}: {}", channel.getName(), sendErr.getMessage()));
        } catch (Exception ex) {
            log.error("[DISCORD-TICKET] Erro ao montar ou enviar mensagem inicial fixada no canal #{}", channel.getName(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void archiveTicketChannel(Ticket ticketParam) {
        if (ticketParam == null || ticketParam.getId() == null) {
            return;
        }

        UUID ticketId = ticketParam.getId();
        if (!processingResolution.add(ticketId)) {
            log.info("[DISCORD-TICKET] Arquivamento do chamado #{} (ID: {}) já está em andamento ou foi concluído. Ignorando duplicidade.",
                    ticketParam.getNumber(), ticketId);
            return;
        }

        try {
            Ticket dbTicket = ticketRepository.findByIdWithRelations(ticketParam.getId()).orElse(ticketParam);
            log.info("[DISCORD-TICKET] Iniciando arquivamento de canal para chamado #{}.", dbTicket.getNumber());

            JDA jDA = jdaProvider.getIfAvailable();
            if (jDA == null) {
                log.warn("[DISCORD-TICKET] JDA indisponível. Arquivamento do canal para chamado #{} ignorado.", dbTicket.getNumber());
                return;
            }

            Guild guild = resolveGuild(jDA);
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

            List<TextChannel> channels = findChannelsForTicket(guild, dbTicket);
            if (channels.isEmpty()) {
                log.warn("[DISCORD-TICKET] Nenhum canal encontrado para o chamado #{}.", dbTicket.getNumber());
                return;
            }

            for (TextChannel channel : channels) {
                MessageEmbed solutionEmbed = embedBuilder.buildSolutionEmbed(dbTicket, ticketParam);
                ItemComponent reopenButton = embedBuilder.buildReopenButton(dbTicket);

                channel.sendMessageEmbeds(solutionEmbed)
                        .setActionRow(reopenButton)
                        .queue(
                                message -> message.pin().queue(
                                        v -> log.info("[DISCORD-TICKET] Embed de solução do chamado #{} enviado e fixado no canal #{}", dbTicket.getNumber(), channel.getName()),
                                        pinErr -> log.warn("[DISCORD-TICKET] Embed de solução enviado, mas falhou ao fixar no canal #{}: {}", channel.getName(), pinErr.getMessage())),
                                err -> log.error("[DISCORD-TICKET] Falha ao enviar embed de resolução para canal #{}: {}", channel.getName(), err.getMessage()));

                // Remove permissão de escrita de todos os membros humanos vinculados
                for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                    long targetId = override.getIdLong();
                    if (targetId != jDA.getSelfUser().getIdLong()) {
                        try {
                            channel.getManager().putMemberPermissionOverride(
                                    targetId,
                                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                                    EnumSet.of(Permission.MESSAGE_SEND)).queue();
                        } catch (Exception ex) {
                            log.warn("[DISCORD-TICKET] Falha ao remover permissões de escrita para membro no canal #{}: {}", channel.getName(), ex.getMessage());
                        }
                    }
                }

                // Move para a categoria semanal de arquivados disponível com fallback
                try {
                    channel.getManager().setParent(targetArchivedCategory).queue(
                            v -> log.info("[DISCORD-TICKET] Canal #{} movido com sucesso para categoria '{}' (ID: {}).",
                                    channel.getName(), targetArchivedCategory.getName(), targetArchivedCategory.getId()),
                            error -> {
                                log.error("[DISCORD-TICKET] Falha ao mover canal #{} para arquivados: {}", channel.getName(), error.getMessage());
                                if (error.getMessage() != null && error.getMessage().contains("50035")) {
                                    log.warn("[DISCORD-TICKET] Limite de 50 canais atingido (50035). Criando/obtendo categoria secundária.");
                                    Category fallbackCategory = resolveAvailableArchivedCategory(guild, null);
                                    if (fallbackCategory != null && !fallbackCategory.getId().equals(targetArchivedCategory.getId())) {
                                        channel.getManager().setParent(fallbackCategory).queue(
                                                v -> log.info("[DISCORD-TICKET] Canal #{} movido para categoria alternativa '{}'", channel.getName(), fallbackCategory.getName()),
                                                e -> log.error("[DISCORD-TICKET] Falha final ao mover canal #{}: {}", channel.getName(), e.getMessage()));
                                    }
                                }
                            });
                } catch (Exception ex) {
                    log.error("[DISCORD-TICKET] Exceção ao agendar movimento do canal #{}: {}", channel.getName(), ex.getMessage());
                }
            }
        } finally {
            CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS)
                    .execute(() -> processingResolution.remove(ticketId));
        }
    }

    private synchronized Category resolveAvailableArchivedCategory(Guild guild, Category baseCategory) {
        String baseWeeklyName = embedBuilder.getWeeklyArchivedCategoryName(LocalDate.now());

        List<Category> matchingCategories = guild.getCategories().stream()
                .filter(cat -> cat.getName() != null && cat.getName().startsWith(baseWeeklyName))
                .sorted((c1, c2) -> c1.getName().compareTo(c2.getName()))
                .toList();

        for (Category cat : matchingCategories) {
            if (cat.getChannels().size() < 50) {
                log.info("[DISCORD-TICKET] Utilizando categoria semanal existente '{}' (ID: {}, canais: {}/50)",
                        cat.getName(), cat.getId(), cat.getChannels().size());
                return cat;
            }
        }

        String newCategoryName = matchingCategories.isEmpty()
                ? baseWeeklyName
                : baseWeeklyName + " - " + (matchingCategories.size() + 1);

        if (newCategoryName.length() > 32) {
            newCategoryName = newCategoryName.substring(0, 32).trim();
        }
        log.info("[DISCORD-TICKET] Criando nova categoria semanal de arquivados: '{}'", newCategoryName);

        try {
            Category newCategory = guild.createCategory(Objects.requireNonNull(newCategoryName)).complete();
            log.info("[DISCORD-TICKET] Categoria semanal de arquivados criada: '{}' (ID: {})", newCategory.getName(), newCategory.getId());
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

        List<TextChannel> channels = findChannelsForTicket(guild, ticket);
        for (TextChannel channel : channels) {
            try {
                channel.getManager().setParent(activeCategory).queue(
                        v -> log.info("[DISCORD-TICKET] Canal #{} movido de volta para a categoria de ativos.", channel.getName()),
                        err -> log.error("[DISCORD-TICKET] Falha ao mover canal #{} para ativos: {}", channel.getName(), err.getMessage()));
            } catch (Exception ex) {
                log.error("[DISCORD-TICKET] Erro ao mover canal #{} para categoria ativa: {}", channel.getName(), ex.getMessage());
            }

            for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                long targetId = override.getIdLong();
                if (targetId != jda.getSelfUser().getIdLong()) {
                    try {
                        channel.getManager().putMemberPermissionOverride(
                                targetId,
                                EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY),
                                null).queue();
                    } catch (Exception ex) {
                        log.warn("[DISCORD-TICKET] Falha ao restaurar permissão de escrita no canal #{}: {}", channel.getName(), ex.getMessage());
                    }
                }
            }

            channel.sendMessage("🔄 **Chamado Reaberto!** Este chamado foi reaberto e está novamente em atendimento.").queue();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void notifyMerged(Ticket childTicket, Ticket parentTicket) {
        childTicket = ticketRepository.findById(childTicket.getId()).orElse(childTicket);
        parentTicket = ticketRepository.findById(parentTicket.getId()).orElse(parentTicket);
        log.info("[DISCORD-TICKET] Processando unificação do chamado filho #{} ao pai #{}.", childTicket.getNumber(), parentTicket.getNumber());

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

        Category baseArchivedCategory = guild.getCategoryById(ARCHIVED_CATEGORY_ID);
        Category targetArchivedCategory = resolveAvailableArchivedCategory(guild, baseArchivedCategory);

        List<TextChannel> childChannels = findChannelsForTicket(guild, childTicket);
        List<TextChannel> parentChannels = findChannelsForTicket(guild, parentTicket);
        TextChannel parentChannel = parentChannels.isEmpty() ? null : parentChannels.getFirst();

        String parentMention = parentChannel != null ? "<#" + parentChannel.getId() + ">" : "Chamado Mestre #" + parentTicket.getNumber();

        // 1. Notifica e arquiva canais do filho
        for (TextChannel channel : childChannels) {
            MessageEmbed childEmbed = embedBuilder.buildChildMergedEmbed(childTicket, parentTicket, parentMention);

            channel.sendMessageEmbeds(childEmbed).queue(
                    msg -> {
                        for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
                            long targetId = override.getIdLong();
                            if (targetId != jda.getSelfUser().getIdLong()) {
                                try {
                                    channel.getManager().putMemberPermissionOverride(
                                            targetId,
                                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                                            EnumSet.of(Permission.MESSAGE_SEND)).queue();
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        if (targetArchivedCategory != null) {
                            channel.getManager().setParent(targetArchivedCategory).queue(
                                    v -> log.info("[DISCORD-TICKET] Canal do filho #{} arquivado com sucesso.", channel.getName()),
                                    err -> log.error("[DISCORD-TICKET] Falha ao mover canal do filho #{} para arquivados: {}", channel.getName(), err.getMessage()));
                        }
                    },
                    err -> log.error("[DISCORD-TICKET] Falha ao enviar mensagem de unificação no canal do filho #{}: {}", channel.getName(), err.getMessage()));
        }

        // 2. Notifica no canal mestre (pai)
        if (parentChannel != null) {
            MessageEmbed parentEmbed = embedBuilder.buildParentMergedEmbed(childTicket);
            parentChannel.sendMessageEmbeds(parentEmbed).queue(
                    v -> log.info("[DISCORD-TICKET] Notificação de unificação enviada no canal mestre #{}", parentChannel.getName()),
                    err -> log.error("[DISCORD-TICKET] Erro ao enviar aviso no canal mestre #{}: {}", parentChannel.getName(), err.getMessage()));
        }

        syncTicketChannelPermissions(parentTicket);
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

        List<TextChannel> channels = findChannelsForTicket(guild, ticket);
        if (channels.isEmpty()) {
            log.warn("[DISCORD-TICKET] Nenhum canal encontrado para sincronizar permissões do chamado #{}.", ticket.getNumber());
            return;
        }

        List<User> candidates = new ArrayList<>();
        if (ticket.getRequester() != null) {
            candidates.add(ticket.getRequester());
        }
        if (ticket.getAssignedTo() != null) {
            candidates.add(ticket.getAssignedTo());
        }
        if (ticket.getAdditionalUsers() != null) {
            candidates.addAll(ticket.getAdditionalUsers());
        }

        List<Member> membersToPermit = new ArrayList<>();
        for (User u : candidates) {
            if (u != null && u.getDiscordUserId() != null && !u.getDiscordUserId().isBlank()) {
                try {
                    Member m = guild.retrieveMemberById(Objects.requireNonNull(u.getDiscordUserId().trim())).complete();
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
                channel.upsertPermissionOverride(Objects.requireNonNull(m))
                        .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                        .queue(
                                v -> log.info("[DISCORD-TICKET] Permissões concedidas para {} no canal #{}", m.getUser().getAsTag(), channel.getName()),
                                err -> log.error("[DISCORD-TICKET] Falha ao upsert permissões para {} no canal #{}", m.getUser().getAsTag(), channel.getName(), err));
            }
        }
    }

    private Guild resolveGuild(JDA jda) {
        Guild guild = null;
        if (discordGuildId != null && !discordGuildId.isBlank()) {
            try {
                guild = jda.getGuildById(Objects.requireNonNull(discordGuildId.trim()));
            } catch (Exception ignored) {
            }
        }
        if (guild == null) {
            guild = jda.getGuilds().stream().findFirst().orElse(null);
        }
        return guild;
    }

    /**
     * Localiza canais de texto associados a um chamado no Discord.
     */
    private List<TextChannel> findChannelsForTicket(Guild guild, Ticket ticket) {
        if (guild == null || ticket == null) {
            return Collections.emptyList();
        }
        String shortNum = ticket.getNumber() != null ? ticket.getNumber().toLowerCase() : "";
        String idStr = ticket.getId() != null ? ticket.getId().toString().toLowerCase() : "";

        List<TextChannel> found = new ArrayList<>();
        for (TextChannel tc : guild.getTextChannels()) {
            String name = tc.getName() != null ? tc.getName().toLowerCase() : "";
            String topic = tc.getTopic();

            boolean matchesName = !shortNum.isEmpty() && (name.endsWith("-" + shortNum) ||
                    name.startsWith("ticket-" + shortNum) ||
                    name.contains(shortNum));
            boolean matchesTopic = topic != null && !idStr.isEmpty() && topic.toLowerCase().contains(idStr);

            if (matchesName || matchesTopic) {
                found.add(tc);
            }
        }
        return found;
    }
}
