package br.dev.ctrls.itsm.modules.notification.infrastructure.adapter.output.discord;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.dev.ctrls.itsm.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.itsm.modules.user.domain.model.User;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

/**
 * Construtor dedicado de Embeds e formatação de texto para canais de chamados no Discord.
 * Isola a responsabilidade de layout, estilização e sanitização LGPD.
 */
@Slf4j
@Component
@SuppressWarnings("null")
public class DiscordTicketEmbedBuilder {

    public static final int CLINIC_BRAND_COLOR = 0xF97316; // Cor Laranja de Destaque ITSM (#F97316)
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter WEEK_DAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    /**
     * Constrói o Embed inicial fixado do chamado.
     */
    public MessageEmbed buildInitialTicketEmbed(
            Ticket ticket,
            String preResolvedRequesterName,
            String preResolvedSectorName,
            String preResolvedCategoryName,
            String preResolvedRelatedTicketsSummary) {

        EmbedBuilder eb = new EmbedBuilder();
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
            String formattedSla = Objects.requireNonNullElse(ticket.getSlaDeadline().format(DATE_TIME_FORMATTER), "-");
            eb.addField("Prazo SLA", formattedSla, true);
        }

        String relatedSummary = preResolvedRelatedTicketsSummary != null ? preResolvedRelatedTicketsSummary : safeGetRelatedTicketsSummary(ticket);
        if (relatedSummary != null && !relatedSummary.isBlank()) {
            eb.addField("🔗 Chamados Vinculados", relatedSummary, false);
        }

        String openedAt = ticket.getCreatedAt() != null
                ? ticket.getCreatedAt().format(DATE_TIME_FORMATTER)
                : "-";
        eb.setFooter("CTRLS ITSM • Chamado aberto em: " + openedAt);
        eb.setTimestamp(Instant.now());

        return eb.build();
    }

    /**
     * Cria os botões de ação do chamado inicial (Assumir / Resolver).
     */
    public ItemComponent[] buildInitialActionButtons(Ticket ticket) {
        Button btnAssumir = Button.primary("ticket:assumir:" + ticket.getId(), "👤 Assumir Chamado");
        Button btnResolver = Button.success("ticket:resolver:" + ticket.getId(), "✅ Resolver Chamado");
        return new ItemComponent[] { btnAssumir, btnResolver };
    }

    /**
     * Constrói o Embed de Resolução / Arquivamento do chamado.
     */
    public MessageEmbed buildSolutionEmbed(Ticket dbTicket, Ticket ticketParam) {
        EmbedBuilder eb = new EmbedBuilder();
        String ticketNum = dbTicket.getNumber() != null ? dbTicket.getNumber() : "-";
        eb.setTitle("✅ Chamado #" + ticketNum + " - Solução do Chamado");
        eb.setColor(CLINIC_BRAND_COLOR);

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

        String sanitizedSolution = DiscordLgpdSanitizer.sanitize(rawSolution);
        eb.setDescription("**Solução Registrada:**\n" + (sanitizedSolution != null ? sanitizedSolution : "-"));

        User assignedUser = dbTicket.getAssignedTo() != null ? dbTicket.getAssignedTo() : ticketParam.getAssignedTo();
        String assignedName = Objects.requireNonNullElse(
                assignedUser != null ? DiscordLgpdSanitizer.sanitize(assignedUser.getName()) : "Equipe ITSM",
                "Equipe ITSM"
        );
        eb.addField("Atendido por", Objects.requireNonNullElse(assignedName, "Equipe ITSM"), true);

        var assetObj = dbTicket.getAsset() != null ? dbTicket.getAsset() : ticketParam.getAsset();
        if (assetObj != null) {
            eb.addField("Ativo Baixado", "Patrimônio " + assetObj.getPatrimonyCode(), true);
        }

        LocalDateTime closedAt = dbTicket.getClosedAt() != null ? dbTicket.getClosedAt() : ticketParam.getClosedAt();
        String closedAtStr = closedAt != null
                ? closedAt.format(DATE_TIME_FORMATTER)
                : LocalDateTime.now().format(DATE_TIME_FORMATTER);
        eb.setFooter("CTRLS ITSM • Chamado resolvido em: " + closedAtStr);
        eb.setTimestamp(Instant.now());

        return eb.build();
    }

    /**
     * Cria o botão de reabertura do chamado.
     */
    public ItemComponent buildReopenButton(Ticket dbTicket) {
        return Button.secondary("ticket:reabrir:" + dbTicket.getId(), "🔄 Reabrir Chamado");
    }

    /**
     * Constrói o Embed para o canal filho sendo unificado ao pai.
     */
    public MessageEmbed buildChildMergedEmbed(Ticket childTicket, Ticket parentTicket, String parentMention) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("🚨 Chamado #" + childTicket.getNumber() + " Unificado ao Chamado #" + parentTicket.getNumber());
        eb.setColor(CLINIC_BRAND_COLOR);
        eb.setDescription("Este chamado foi **unificado** ao atendimento principal em " + parentMention + ".\n\n"
                + "📋 **Chamado Mestre:** #" + parentTicket.getNumber() + " - " + parentTicket.getTitle() + "\n"
                + "🔒 *Este canal está sendo arquivado. O atendimento continuará no canal principal.*");
        eb.setFooter("CTRLS ITSM • Unificação de Chamados");
        eb.setTimestamp(Instant.now());
        return eb.build();
    }

    /**
     * Constrói o Embed para o canal pai avisando sobre a unificação do filho.
     */
    public MessageEmbed buildParentMergedEmbed(Ticket childTicket) {
        EmbedBuilder parentEb = new EmbedBuilder();
        parentEb.setTitle("🔗 Novo Chamado Unificado a Este Atendimento");
        parentEb.setColor(CLINIC_BRAND_COLOR);
        String childRequester = childTicket.getRequester() != null ? childTicket.getRequester().getName() : "Solicitante";
        parentEb.setDescription(
                "O chamado **#" + childTicket.getNumber() + "** (*" + childTicket.getTitle() + "*), "
                        + "solicitado por **" + childRequester + "**, foi unificado a este chamado mestre.\n\n"
                        + "👥 *Os envolvidos agora têm acesso a este canal e receberão as atualizações por aqui.*");
        parentEb.setFooter("CTRLS ITSM • Central de Atendimento");
        parentEb.setTimestamp(Instant.now());
        return parentEb.build();
    }

    /**
     * Normaliza e gera o nome do canal Discord para o chamado.
     */
    public String formatChannelName(Ticket ticket) {
        String shortNum = ticket.getNumber() != null ? ticket.getNumber().toLowerCase() : "ticket";
        String normalizedTitle = "chamado";
        if (ticket.getTitle() != null && !ticket.getTitle().isBlank()) {
            normalizedTitle = Normalizer.normalize(ticket.getTitle(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9\\s-]", "")
                    .replaceAll("\\s+", "-")
                    .replaceAll("-+", "-")
                    .trim();
        }
        if (normalizedTitle.isEmpty()) {
            normalizedTitle = "chamado";
        }
        if (normalizedTitle.length() > 65) {
            normalizedTitle = normalizedTitle.substring(0, 65).replaceAll("-$", "");
        }

        String channelName = normalizedTitle + "-" + shortNum;
        if (channelName.length() > 85) {
            channelName = channelName.substring(0, 85).replaceAll("-$", "");
        }
        return channelName;
    }

    /**
     * Formata o tópico do canal do Discord.
     */
    public String formatChannelTopic(Ticket ticket, String preResolvedRequesterName, String preResolvedSectorName) {
        String shortNum = ticket.getNumber() != null ? ticket.getNumber().toLowerCase() : "ticket";
        String requesterDisplayName = preResolvedRequesterName != null ? preResolvedRequesterName
                : (ticket.getRequester() != null ? ticket.getRequester().getName() : "Solicitante");
        String sectorDisplayName = preResolvedSectorName != null ? preResolvedSectorName
                : (ticket.getRequester() != null && ticket.getRequester().getSector() != null
                        ? ticket.getRequester().getSector().getName()
                        : "Geral");
        String ticketTitleDisplay = ticket.getTitle() != null ? ticket.getTitle() : "Sem título";
        String channelTopic = String.format("🎫 Chamado #%s | %s (%s) | %s | ID: %s",
                shortNum.toUpperCase(), requesterDisplayName, sectorDisplayName, ticketTitleDisplay, ticket.getId());
        if (channelTopic.length() > 1024) {
            channelTopic = channelTopic.substring(0, 1020) + "...";
        }
        return channelTopic;
    }

    /**
     * Calcula o nome da categoria semanal de arquivamento.
     */
    public String getWeeklyArchivedCategoryName(LocalDate date) {
        LocalDate sunday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate saturday = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        return "📁 ⁃ ARQUIVADOS (" + sunday.format(WEEK_DAY_FORMATTER) + " a " + saturday.format(WEEK_DAY_FORMATTER) + ")";
    }

    public String safeGetCategoryName(Ticket ticket) {
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

    public String safeGetRequesterName(Ticket ticket) {
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

    public String safeGetSectorName(Ticket ticket) {
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

    public String safeGetRelatedTicketsSummary(Ticket ticket) {
        if (ticket == null) return "";
        try {
            if (ticket.getRelatedTickets() != null && !ticket.getRelatedTickets().isEmpty()) {
                return ticket.getRelatedTickets().stream()
                        .map(t -> "#" + (t.getNumber() != null ? t.getNumber() : "-") + " - "
                                + DiscordLgpdSanitizer.sanitize(t.getTitle()))
                        .collect(Collectors.joining("\n"));
            }
        } catch (Exception ex) {
            log.warn("[DISCORD-TICKET] Não foi possível carregar chamados relacionados para chamado #{}: {}",
                    ticket.getNumber() != null ? ticket.getNumber() : "-", ex.getMessage());
        }
        return "";
    }
}
