package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;

/**
 * Componente que classifica intenções de texto, reconhece cliques de botão e extrai metadados
 * a partir de mensagens recebidas via Webhook do Blip/WhatsApp.
 */
@Component
@RequiredArgsConstructor
public class BlipWebhookIntentMatcher {

    private final BlipProperties blipProperties;

    /**
     * Identifica se uma ação ou texto recebido corresponde a um clique de botão estruturado ou bypass explícito.
     */
    public boolean isButtonClickOrExplicitIntent(String action, String actionValue, String rawActionTextLower, String rawText) {
        String prepararUuid = blipProperties.getBlocks() != null ? blipProperties.getBlocks().getPrepararAtendimento() : null;
        String exibirUuid = blipProperties.getBlocks() != null ? blipProperties.getBlocks().getExibirAgenda() : null;

        boolean isPrepararAtendimento = "preparar_atendimento".equalsIgnoreCase(actionValue)
            || (prepararUuid != null && Pattern.compile("\\b" + Pattern.quote(prepararUuid.toLowerCase()) + "\\b").matcher(rawActionTextLower).find());
        boolean isExibirAgenda = "exibir_agenda".equalsIgnoreCase(actionValue)
            || (exibirUuid != null && Pattern.compile("\\b" + Pattern.quote(exibirUuid.toLowerCase()) + "\\b").matcher(rawActionTextLower).find());

        boolean isHumanAttendantRequest = rawActionTextLower.contains("atendimento humano")
            || rawActionTextLower.contains("falar com atendente")
            || rawActionTextLower.contains("atendente")
            || rawActionTextLower.contains("recepção")
            || rawActionTextLower.contains("recepcao")
            || rawActionTextLower.contains("suporte");

        boolean isBypassExplicit = rawActionTextLower.contains("ver agendamentos")
            || rawActionTextLower.contains("ver agenda")
            || rawActionTextLower.contains("ver_agenda")
            || rawActionTextLower.contains("confirm_group_")
            || rawActionTextLower.contains("confirmar_tudo")
            || rawActionTextLower.contains("confirmar presença")
            || rawActionTextLower.contains("confirmar presenca")
            || rawActionTextLower.contains("confirmar")
            || rawActionTextLower.contains("confirmo")
            || rawActionTextLower.contains("alter_group_")
            || rawActionTextLower.contains("preciso_alterar")
            || rawActionTextLower.contains("solicitar alteração")
            || rawActionTextLower.contains("solicitar alteracao")
            || rawActionTextLower.contains("alterar")
            || isHumanAttendantRequest
            || isConfirmationOrAlterationIntentText(actionValue)
            || isConfirmationOrAlterationIntentText(rawText);

        boolean isNullOrEmpty = action == null || action.isBlank() || "null".equalsIgnoreCase(action.trim());

        return isNullOrEmpty || isBypassExplicit || (action != null && (
            action.toLowerCase().startsWith("confirm_") ||
            action.toLowerCase().startsWith("alter_") ||
            action.toLowerCase().startsWith("group_") ||
            action.toLowerCase().startsWith("ver_agenda_") ||
            action.toLowerCase().contains("confirmar") ||
            action.toLowerCase().contains("alterar") ||
            action.toLowerCase().contains("ver agendamento") ||
            "group_view_fallback".equalsIgnoreCase(action) ||
            "Verificar_Acompanhante".equalsIgnoreCase(action) ||
            "Integrar_GerAcesso".equalsIgnoreCase(action) ||
            "Não".equalsIgnoreCase(action) ||
            "Finalizar_Agendamento".equalsIgnoreCase(action) ||
            isPrepararAtendimento ||
            isExibirAgenda
        ));
    }

    /**
     * Reconhece termos exatos de intenção de confirmação ou alteração/cancelamento.
     */
    public static boolean isConfirmationOrAlterationIntentText(String text) {
        if (text == null || text.isBlank()) return false;

        String rawTrimmed = text.trim().toLowerCase();
        String cleaned = rawTrimmed.replaceAll("[^a-z0-9áàâãéèêíïóôõöúçñ\\s]", " ").replaceAll("\\s+", " ").trim();

        // 1. Regra de Tamanho: Se o texto tiver mais de 25 caracteres ou mais de 3 palavras -> false
        if (cleaned.length() > 25) return false;
        String[] words = cleaned.split(" ");
        if (words.length > 3) return false;

        // 2. Guarda de Negação/Condicional: Se o texto contiver palavras de negação ou condição -> false
        for (String w : words) {
            if (w.equals("não") || w.equals("nao") || w.equals("nunca") || w.equals("nem") ||
                w.equals("se") || w.equals("caso") || w.equals("depois")) {
                return false;
            }
        }

        // 3. Casamento Estrito (Pattern matching com switch em Java 21)
        return switch (cleaned) {
            case "sim", "confirmar", "confirmo", "confirmado", "confirma",
                 "presença", "presenca", "confirmar presença", "confirmar presenca", "sim confirmo",
                 "alterar", "remarcar", "trocar",
                 "solicitar alteração", "solicitar alteracao", "preciso alterar", "quero remarcar", "quero alterar",
                 "cancelar", "cancel" -> true;
            default -> false;
        };
    }

    /**
     * Extrai os metadados do payload da Blip de forma recursiva e defensiva.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractMetadata(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        Object metadataObj = payload.get("metadata");
        if (metadataObj == null) {
            Object messageObj = payload.get("message");
            if (messageObj instanceof Map<?, ?> msgMap) {
                metadataObj = msgMap.get("metadata");
            }
        }
        if (metadataObj == null) {
            Object resourceObj = payload.get("resource");
            if (resourceObj instanceof Map<?, ?> resMap) {
                metadataObj = resMap.get("metadata");
                if (metadataObj == null) {
                    Object innerMsg = resMap.get("message");
                    if (innerMsg instanceof Map<?, ?> innerMsgMap) {
                        metadataObj = innerMsgMap.get("metadata");
                    }
                }
            }
        }
        if (metadataObj instanceof Map<?, ?> metaMap) {
            return (Map<String, Object>) metaMap;
        }
        return Map.of();
    }
}
