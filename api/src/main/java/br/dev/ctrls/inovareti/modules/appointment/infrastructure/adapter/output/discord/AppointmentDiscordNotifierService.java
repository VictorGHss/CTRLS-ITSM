package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.discord;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Serviço responsável pelo envio de relatórios de agendamento e alertas para canais do Discord.
 * Permite direcionamento por médico (canal com secretárias) e canal geral de TI/Recepção.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentDiscordNotifierService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int INOVARE_BLUE = 0x1F4E79;
    private static final int WARNING_ORANGE = 0xF59E0B;
    private static final int SUCCESS_GREEN = 0x10B981;

    private final ObjectProvider<JDA> jdaProvider;

    @Value("${discord.channel.reception-general:}")
    private String generalReceptionChannelId;

    @Getter
    @Builder
    public static class PatientAttentionItem {
        private String patientName;
        private String appointmentTime;
        private String procedureName;
        private String reason; // ex: "Sem telefone cadastrado na Feegow", "Número inválido"
    }

    @Getter
    @Builder
    public static class DoctorDispatchSummary {
        private Long doctorId;
        private String doctorName;
        private String discordChannelId;
        private LocalDate targetDate;
        private int totalConsultas;
        private int totalDisparados;
        private int totalPreConfirmados;
        private List<PatientAttentionItem> attentionList;
    }

    /**
     * Envia o relatório de disparos e agendamentos no canal exclusivo do médico e secretárias.
     */
    public void sendDoctorDispatchReport(DoctorDispatchSummary summary) {
        if (summary == null) {
            return;
        }

        String channelId = summary.getDiscordChannelId();
        if (channelId == null || channelId.isBlank()) {
            log.debug("[DISCORD-APPOINTMENT] Médico {} (ID {}) não possui canal Discord configurado.",
                    summary.getDoctorName(), summary.getDoctorId());
            return;
        }

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("[DISCORD-APPOINTMENT] JDA indisponível. Notificação não enviada para o canal {}", channelId);
            return;
        }

        try {
            String safeChannelId = channelId.trim();
            TextChannel channel = jda.getTextChannelById(safeChannelId);
            if (channel == null) {
                log.warn("[DISCORD-APPOINTMENT] Canal Discord ID {} do médico {} não foi encontrado ou bot não possui acesso.",
                        channelId, summary.getDoctorName());
                return;
            }

            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("📋 Relatório de Confirmações — " + (summary.getDoctorName() != null ? summary.getDoctorName() : "Médico"));
            eb.setColor(summary.getAttentionList() != null && !summary.getAttentionList().isEmpty() ? WARNING_ORANGE : SUCCESS_GREEN);

            String formattedDate = summary.getTargetDate() != null ? summary.getTargetDate().format(DATE_FORMATTER) : LocalDate.now().format(DATE_FORMATTER);
            eb.setDescription("Resumo dos disparos de confirmação via WhatsApp para o atendimento de **" + formattedDate + "**.");

            eb.addField("📤 Mensagens Disparadas", Integer.toString(summary.getTotalDisparados()), true);
            eb.addField("✅ Já Confirmadas", Integer.toString(summary.getTotalPreConfirmados()), true);
            eb.addField("📊 Total Agendadas", Integer.toString(summary.getTotalConsultas()), true);

            if (summary.getAttentionList() != null && !summary.getAttentionList().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (PatientAttentionItem item : summary.getAttentionList()) {
                    sb.append("• **").append(item.getAppointmentTime() != null ? item.getAppointmentTime() : "--:--").append("** — ")
                      .append(item.getPatientName() != null ? item.getPatientName() : "Paciente")
                      .append(" (*").append(item.getReason() != null ? item.getReason() : "Atenção manual").append("*)\n");
                }
                String text = sb.toString();
                if (text.length() > 1024) {
                    text = text.substring(0, 1020) + "...";
                }
                eb.addField("⚠️ Pacientes que Requerem Contato Manual (Sem WhatsApp)", text, false);
            } else {
                eb.addField("✨ Status", "Todos os pacientes elegíveis receberam a notificação com sucesso!", false);
            }

            eb.setFooter("Inovare-TI • Confirmação Inteligente de Agendamentos", null);
            eb.setTimestamp(java.time.Instant.now());

            channel.sendMessageEmbeds(eb.build()).queue(
                    msg -> log.info("[DISCORD-APPOINTMENT] Relatório matinal enviado com sucesso no canal {} para {}", safeChannelId, summary.getDoctorName()),
                    err -> log.warn("[DISCORD-APPOINTMENT] Falha ao enviar embed no canal {}: {}", safeChannelId, err.getMessage())
            );

        } catch (Exception ex) {
            log.error("[DISCORD-APPOINTMENT] Erro ao enviar relatório no canal {}: {}", channelId, ex.getMessage(), ex);
        }
    }

    /**
     * Envia o resumo geral consolidado da ingestão matinal no canal de TI/Recepção Geral.
     */
    public void sendGeneralIngestionSummary(LocalDate targetDate, int totalRaw, int totalDispatched, int totalPreConfirmed, int totalAttention, long durationMs) {
        String channelId = generalReceptionChannelId;
        if (channelId == null || channelId.isBlank()) {
            return;
        }

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            return;
        }

        try {
            String safeChannelId = channelId.trim();
            TextChannel channel = jda.getTextChannelById(safeChannelId);
            if (channel == null) {
                return;
            }

            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("📢 Ingestão Matinal de Agendamentos Concluída");
            eb.setColor(INOVARE_BLUE);

            String formattedDate = targetDate != null ? targetDate.format(DATE_FORMATTER) : LocalDate.now().format(DATE_FORMATTER);
            eb.setDescription("Processamento matinal executado para a data **" + formattedDate + "**.");

            eb.addField("📋 Consultas Analisadas", Integer.toString(totalRaw), true);
            eb.addField("📤 Mensagens Enviadas", Integer.toString(totalDispatched), true);
            eb.addField("✅ Já Confirmadas", Integer.toString(totalPreConfirmed), true);
            eb.addField("⚠️ Contato Manual (Sem Telefone)", Integer.toString(totalAttention), true);
            eb.addField("⏱️ Tempo de Execução", (durationMs / 1000.0) + "s", true);

            eb.setFooter("Inovare-TI • Motor de Agendamentos", null);
            eb.setTimestamp(java.time.Instant.now());

            channel.sendMessageEmbeds(eb.build()).queue();
        } catch (Exception ex) {
            log.warn("[DISCORD-APPOINTMENT] Falha ao despachar resumo geral para Discord: {}", ex.getMessage());
        }
    }
}
