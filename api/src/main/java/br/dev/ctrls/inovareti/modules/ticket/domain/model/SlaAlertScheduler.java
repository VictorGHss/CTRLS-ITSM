package br.dev.ctrls.inovareti.modules.ticket.domain.model;

import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;

/**
 * Scheduler de alertas de SLA crítico para chamados em andamento.
 *
 * <p>
 * Executa a cada 15 minutos e verifica chamados com {@code slaDeadline}
 * expirando
 * em menos de 30 minutos. Para cada chamado crítico encontrado, envia uma
 * <b>DM privada</b> diretamente para o Discord do técnico responsável com um
 * embed vermelho de urgência.
 * </p>
 *
 * <p>
 * Se o chamado não tiver técnico atribuído ou o técnico não tiver Discord
 * vinculado, o alerta é registrado em log como aviso.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaAlertScheduler {

    private static final int SLA_ALERT_THRESHOLD_MINUTES = 30;
    private static final int EMBED_COLOR_RED = 0xE53E3E;

    private final TicketRepositoryPort ticketRepository;
    private final ObjectProvider<JDA> jdaProvider;

    /**
     * Roda a cada 15 minutos e envia DMs de alerta vermelho para os técnicos
     * cujos chamados estão prestes a estourar o SLA.
     */
    @Scheduled(fixedDelay = 900_000)
    @Transactional(readOnly = true)
    public void checkSlaExpirations() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusMinutes(SLA_ALERT_THRESHOLD_MINUTES);

        List<Ticket> criticalTickets = ticketRepository.findAllByStatus(TicketStatus.OPEN)
                .stream()
                .filter(t -> t.getSlaDeadline() != null
                        && t.getSlaDeadline().isAfter(now)
                        && t.getSlaDeadline().isBefore(threshold))
                .toList();

        // Também verifica IN_PROGRESS
        List<Ticket> inProgressCritical = ticketRepository.findAllByStatus(TicketStatus.IN_PROGRESS)
                .stream()
                .filter(t -> t.getSlaDeadline() != null
                        && t.getSlaDeadline().isAfter(now)
                        && t.getSlaDeadline().isBefore(threshold))
                .toList();

        List<Ticket> allCritical = new java.util.ArrayList<>(criticalTickets);
        allCritical.addAll(inProgressCritical);

        if (allCritical.isEmpty()) {
            log.debug("[SLA-SCHEDULER] Nenhum chamado crítico encontrado (janela: {} -> {})", now, threshold);
            return;
        }

        log.warn("[SLA-SCHEDULER] {} chamado(s) com SLA crítico encontrado(s)!", allCritical.size());

        JDA jda = jdaProvider.getIfAvailable();

        for (Ticket ticket : allCritical) {
            String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
            long minutesRemaining = java.time.Duration.between(now, ticket.getSlaDeadline()).toMinutes();

            log.warn("[SLA-SCHEDULER] ⚠️ Chamado #{} expira em {} minutos. Técnico: {}",
                    shortId, minutesRemaining,
                    ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : "Não atribuído");

            if (ticket.getAssignedTo() == null) {
                log.warn("[SLA-SCHEDULER] Chamado #{} sem técnico atribuído — alerta de SLA não pode ser enviado.",
                        shortId);
                continue;
            }

            String technicianDiscordId = ticket.getAssignedTo().getDiscordUserId();
            if (technicianDiscordId == null || technicianDiscordId.isBlank()) {
                log.warn("[SLA-SCHEDULER] Técnico '{}' do chamado #{} não possui Discord vinculado — DM ignorada.",
                        ticket.getAssignedTo().getName(), shortId);
                continue;
            }

            if (jda != null) {
                sendSlaAlertToChannel(jda, ticket, shortId, minutesRemaining);
            }
        }
    }

    private void sendSlaAlertToChannel(JDA jda, Ticket ticket, String shortId, long minutesRemaining) {
        net.dv8tion.jda.api.entities.Guild guild = jda.getGuilds().stream().findFirst().orElse(null);
        if (guild == null)
            return;

        String ticketNumStr = ticket.getNumber() != null ? ticket.getNumber().toLowerCase() : shortId.toLowerCase();
        String ticketIdStr = ticket.getId() != null ? ticket.getId().toString().toLowerCase() : "";

        List<net.dv8tion.jda.api.entities.channel.concrete.TextChannel> channels = guild.getTextChannels().stream()
                .filter(tc -> {
                    String name = tc.getName() != null ? tc.getName().toLowerCase() : "";
                    String topic = tc.getTopic();
                    boolean matchesName = name.endsWith("-" + ticketNumStr) || name.startsWith("ticket-" + ticketNumStr)
                            || name.contains(ticketNumStr);
                    boolean matchesTopic = topic != null && !ticketIdStr.isEmpty()
                            && topic.toLowerCase().contains(ticketIdStr);
                    return matchesName || matchesTopic;
                })
                .toList();

        if (channels.isEmpty()) {
            log.warn("[SLA-SCHEDULER] Canal privado para chamado #{} não encontrado no Discord para alerta de SLA.",
                    ticketNumStr);
            return;
        }

        String formattedSla = ticket.getSlaDeadline() != null
                ? ticket.getSlaDeadline().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "-";

        String requesterName = ticket.getRequester() != null ? ticket.getRequester().getName() : "-";
        String assignedName = ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : "Não atribuído";

        var embed = new EmbedBuilder()
                .setColor(EMBED_COLOR_RED)
                .setTitle("🚨 ALERTA DE SLA CRÍTICO — Chamado #"
                        + (ticket.getNumber() != null ? ticket.getNumber() : shortId))
                .setDescription("⏰ **ATENÇÃO:** Este chamado está próximo do vencimento do SLA!\n\n"
                        + "📋 **Título:** " + ticket.getTitle() + "\n"
                        + "👤 **Solicitante:** " + requesterName + "\n"
                        + "👤 **Técnico:** " + assignedName + "\n"
                        + "📅 **Prazo SLA:** " + formattedSla + "\n"
                        + "⏱️ **Tempo Restante:** " + minutesRemaining + " minuto(s)\n\n"
                        + "⚡ Por favor, resolva ou atualize o status do chamado!")
                .setFooter("Inovare TI • Sistema de Monitoramento de SLA")
                .setTimestamp(java.time.Instant.now())
                .build();

        for (var channel : channels) {
            channel.sendMessageEmbeds(embed).queue(
                    success -> log.info("[SLA-SCHEDULER] Alerta de SLA enviado no canal privado #{} (chamado #{})",
                            channel.getName(), shortId),
                    error -> log.warn("[SLA-SCHEDULER] Falha ao enviar alerta no canal #{}: {}", channel.getName(),
                            error.getMessage()));
        }
    }
}
