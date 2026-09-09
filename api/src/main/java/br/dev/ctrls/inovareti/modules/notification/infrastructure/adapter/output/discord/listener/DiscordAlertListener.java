package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.listener;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.inventory.application.event.LowStockEvent;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.DiscordWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Listener de eventos que escuta o LowStockEvent e encaminha o embed de aviso
 * ao canal operacional do Discord de forma assíncrona, evitando bloquear a thread principal de negócio.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordAlertListener {

    private final DiscordWebhookService discordWebhookService;
    private final ObjectProvider<JDA> jdaProvider;

    @Value("${discord.operational.webhook.url:}")
    private String operationalWebhookUrl;

    @Value("${discord.webhook.url:}")
    private String defaultWebhookUrl;

    @Value("${discord.bot.operational-channel-id:}")
    private String operationalChannelId;

    /**
     * Captura o evento de estoque baixo de forma assíncrona e despacha o alerta para o Discord.
     *
     * @param event Dados do item que atingiu o limite crítico.
     */
    @Async
    @EventListener
    public void handleLowStockEvent(LowStockEvent event) {
        log.info("[DISCORD-ALERT] Processando alerta de estoque crítico para o item '{}' (Estoque: {}, Limite Mínimo: {})",
                event.itemName(), event.currentStock(), event.minStock());

        // 1. Tenta o despacho prioritário utilizando Webhook Operacional
        if (operationalWebhookUrl != null && !operationalWebhookUrl.isBlank()) {
            try {
                Map<String, Object> embed = Map.of(
                    "title", "⚠️ Alerta de Estoque Crítico (Inventário)",
                    "description", String.format("O item **%s** atingiu ou está abaixo do limite de estoque mínimo!\n\n**Detalhes:**\n- **ID**: `%s`\n- **Estoque Atual**: `%d` unidades\n- **Estoque Mínimo**: `%d` unidades",
                        event.itemName(), event.itemId(), event.currentStock(), event.minStock()),
                    "color", 16753920 // Laranja (#FFA500)
                );
                discordWebhookService.sendWebhook(operationalWebhookUrl, embed, UUID.randomUUID().toString(), event.itemId().toString());
                log.info("[DISCORD-ALERT] Alerta de estoque crítico despachado com sucesso via Webhook.");
                return;
            } catch (Exception ex) {
                log.warn("[DISCORD-ALERT] Falha ao enviar notificação via Webhook: {}. Recorrendo ao JDA...", ex.getMessage());
            }
        }

        // 2. Fallback: Despacha via JDA bot no canal operacional configurado
        JDA jda = jdaProvider.getIfAvailable();
        if (jda != null && operationalChannelId != null && !operationalChannelId.isBlank()) {
            try {
                TextChannel canal = jda.getTextChannelById(java.util.Objects.requireNonNull(operationalChannelId));
                if (canal != null) {
                    var embed = new EmbedBuilder()
                        .setColor(0xFFA500) // Laranja
                        .setTitle("⚠️ Alerta de Estoque Crítico (Inventário)")
                        .setDescription(String.format("O item **%s** atingiu ou está abaixo do limite de estoque mínimo!\n\n**Detalhes:**\n- **ID**: `%s`\n- **Estoque Atual**: `%d` unidades\n- **Estoque Mínimo**: `%d` unidades",
                            event.itemName(), event.itemId(), event.currentStock(), event.minStock()))
                        .build();
                    canal.sendMessageEmbeds(embed).queue();
                    log.info("[DISCORD-ALERT] Alerta de estoque crítico despachado com sucesso via JDA bot.");
                } else {
                    log.warn("[DISCORD-ALERT] Canal operacional com ID '{}' não foi localizado no JDA.", operationalChannelId);
                }
            } catch (Exception ex) {
                log.error("[DISCORD-ALERT] Falha no fallback de notificação via JDA bot: {}", ex.getMessage(), ex);
            }
        } else {
            log.warn("[DISCORD-ALERT] Sem canais de comunicação configurados para alertas operacionais de estoque.");
        }
    }

    /**
     * Captura o evento de bloqueio por tentativas de login/2FA de forma assíncrona
     * e despacha um alerta imediato de segurança para o canal do Discord.
     */
    @Async
    @EventListener
    public void handleAuthLockoutEvent(br.dev.ctrls.inovareti.modules.auth.application.event.AuthLockoutEvent event) {
        log.warn("[DISCORD-ALERT] Processando alerta de bloqueio de segurança para '{}' (IP: {})",
                event.email(), event.clientIp());

        String targetWebhook = (operationalWebhookUrl != null && !operationalWebhookUrl.isBlank())
                ? operationalWebhookUrl
                : defaultWebhookUrl;

        if (targetWebhook != null && !targetWebhook.isBlank()) {
            try {
                Map<String, Object> embed = Map.of(
                    "title", "🚨 [SEGURANÇA] Bloqueio Temporário Ativado",
                    "description", String.format("Acesso temporariamente suspenso devido a excesso de tentativas consecutivas incorretas.\n\n**Detalhes da Ocorrência:**\n- **Identificador / E-mail**: `%s`\n- **IP de Origem**: `%s`\n- **Motivo**: %s\n- **Tentativas Falhas**: `%d`\n- **Duração do Bloqueio**: `%d minutos`",
                        event.email(), event.clientIp(), event.reason(), event.attempts(), event.lockoutMinutes()),
                    "color", 15158332 // Vermelho (#E74C3C)
                );
                discordWebhookService.sendWebhook(targetWebhook, embed, UUID.randomUUID().toString(), "security-lockout");
                log.info("[DISCORD-ALERT] Alerta de bloqueio de segurança despachado com sucesso via Webhook.");
                return;
            } catch (Exception ex) {
                log.warn("[DISCORD-ALERT] Falha ao enviar alerta de segurança via Webhook: {}. Tentando via JDA...", ex.getMessage());
            }
        }

        JDA jda = jdaProvider.getIfAvailable();
        if (jda != null && operationalChannelId != null && !operationalChannelId.isBlank()) {
            try {
                TextChannel canal = jda.getTextChannelById(operationalChannelId);
                if (canal != null) {
                    var embed = new EmbedBuilder()
                        .setColor(0xE74C3C) // Vermelho
                        .setTitle("🚨 [SEGURANÇA] Bloqueio Temporário Ativado")
                        .setDescription(String.format("Acesso temporariamente suspenso devido a excesso de tentativas consecutivas incorretas.\n\n**Detalhes da Ocorrência:**\n- **Identificador / E-mail**: `%s`\n- **IP de Origem**: `%s`\n- **Motivo**: %s\n- **Tentativas Falhas**: `%d`\n- **Duração do Bloqueio**: `%d minutos`",
                            event.email(), event.clientIp(), event.reason(), event.attempts(), event.lockoutMinutes()))
                        .build();
                    canal.sendMessageEmbeds(embed).queue();
                    log.info("[DISCORD-ALERT] Alerta de segurança despachado com sucesso via JDA bot.");
                }
            } catch (Exception ex) {
                log.error("[DISCORD-ALERT] Falha no fallback de notificação de segurança via JDA: {}", ex.getMessage(), ex);
            }
        }
    }
}
