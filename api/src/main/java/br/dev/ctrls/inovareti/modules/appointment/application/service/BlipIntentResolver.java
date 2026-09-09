package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipWebhookPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookIntent;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pela detecção de intenções e resolução de comandos de texto livre:
 * - Detecta intenções inequívocas (CONFIRM, ALTER, CANCEL, UNKNOWN).
 * - Mapeia texto para ações estruturadas (confirm_{id}, alter_{id}, etc.) com busca de variações de telefone.
 * - Trata mensagens de agendamentos já confirmados recentemente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlipIntentResolver {

    private final BlipIdentityReconciler blipIdentityReconciler;
    private final BlipWebhookPreprocessor preprocessor;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipNotificationService blipNotificationService;
    private final BlipProperties blipProperties;
    private final BlipContextService blipContextService;
    private final TransactionTemplate transactionTemplate;

    public static WebhookIntent detectIntent(String text) {
        if (text == null || text.isBlank()) return WebhookIntent.UNKNOWN;

        String rawTrimmed = text.trim();
        // Se for resposta encapsulada de template com múltiplas linhas, extrai a última linha não vazia que não seja timestamp / metadado
        String[] lines = rawTrimmed.split("\\r?\\n");
        if (lines.length > 1) {
            for (int i = lines.length - 1; i >= 0; i--) {
                String l = lines[i].trim();
                if (l.isBlank()) {
                    continue;
                }
                // Ignora timestamps isolados (ex: "09:10", "7:00", "09:10:00")
                if (l.matches("^\\d{1,2}:\\d{2}(?::\\d{2})?$")) {
                    continue;
                }
                // Ignora datas isoladas (ex: "10/09/2026", "10-09-2026")
                if (l.matches("^\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}$")) {
                    continue;
                }
                rawTrimmed = l;
                break;
            }
        }

        String cleaned = rawTrimmed.toLowerCase().replaceAll("[^a-z0-9áàâãéèêíïóôõöúçñ\\s]", " ").replaceAll("\\s+", " ").trim();

        // 1. Regra de Tamanho: Se a linha tiver mais de 35 caracteres ou mais de 4 palavras, trate como UNKNOWN
        if (cleaned.length() > 35) return WebhookIntent.UNKNOWN;
        String[] words = cleaned.split(" ");
        if (words.length > 4) return WebhookIntent.UNKNOWN;

        // 2. Guarda de Negação/Condicional: Se o texto contiver palavras de negação ou condição, retorne UNKNOWN
        for (String w : words) {
            if (w.equals("não") || w.equals("nao") || w.equals("nunca") || w.equals("nem") ||
                w.equals("se") || w.equals("caso") || w.equals("depois")) {
                return WebhookIntent.UNKNOWN;
            }
        }

        // 3. Casamento Estrito por Palavras-Chave de Intenção Inequívoca
        return switch (cleaned) {
            case "sim", "confirmar", "confirmo", "confirmado", "confirma",
                 "presença", "presenca", "confirmar presença", "confirmar presenca", "sim confirmo" -> WebhookIntent.CONFIRM;

            case "cancelar", "cancel" -> WebhookIntent.CANCEL;

            case "alterar", "remarcar", "trocar",
                 "solicitar alteração", "solicitar alteracao", "preciso alterar", "quero remarcar", "quero alterar" -> WebhookIntent.ALTER;

            default -> WebhookIntent.UNKNOWN;
        };
    }

    public String resolveTextIntentions(String normalizedAction, String action, BlipWebhookPayload payload) {
        WebhookIntent intent = detectIntent(normalizedAction);
        if (intent == WebhookIntent.UNKNOWN) {
            return action;
        }

        String fromPhone = payload != null ? payload.from() : null;
        String bsuid = payload != null ? payload.bsuid() : null;

        // 1. Busca agendamentos pendentes por telefone (com normalização de DDI 55 e 9º dígito)
        List<AppointmentSession> pendingSessions = findPendingSessionsByPhoneWithVariations(fromPhone, bsuid);

        if (pendingSessions.size() == 1) {
            AppointmentSession singleSession = pendingSessions.getFirst();
            String feegowId = singleSession.getFeegowAppointmentId();
            log.info("[WEBHOOK-PHONE-RESOLVE] 1 agendamento pendente (Feegow ID: {}) encontrado por telefone para {}. Mapeando para {}_{}",
                    feegowId, fromPhone, intent.name().toLowerCase(), feegowId);

            return switch (intent) {
                case CONFIRM -> "confirm_" + feegowId;
                case ALTER -> "alter_" + feegowId;
                case CANCEL -> "cancel_" + feegowId;
                default -> action;
            };
        } else if (pendingSessions.size() > 1) {
            log.info("[WEBHOOK-PHONE-RESOLVE] {} agendamentos pendentes encontrados por telefone para {}. Direcionando para confirmação em lote.",
                    pendingSessions.size(), fromPhone);
            for (AppointmentSession session : pendingSessions) {
                if (session.getCurrentGroupId() != null) {
                    String gId = session.getCurrentGroupId().toString();
                    return (intent == WebhookIntent.CONFIRM ? "confirm_group_" : "alter_group_") + gId;
                }
            }
            return (intent == WebhookIntent.CONFIRM ? "confirm_group_fallback" : "alter_group_fallback");
        } else {
            // 0 agendamentos pendentes encontrados
            if (hasRecentlyConfirmedSession(fromPhone, bsuid)) {
                log.info("[WEBHOOK-PHONE-RESOLVE] 0 agendamentos pendentes, porém consulta já confirmada recentemente para {}. Enviando resposta de cortesia.", fromPhone);
                try {
                    blipNotificationService.sendPlainTextMessage(fromPhone, "Olá! Identificamos que seu agendamento já se encontra confirmado. Estamos te aguardando!");
                    String confirmSuccessBlockId = blipProperties.getBlocks().getConfirmSuccess();
                    if (confirmSuccessBlockId != null && !confirmSuccessBlockId.isBlank()) {
                        blipContextService.changeMasterState(fromPhone, confirmSuccessBlockId);
                    }
                } catch (Exception ex) {
                    log.warn("[WEBHOOK-PHONE-RESOLVE] Erro ao enviar mensagem de consulta já confirmada para {}: {}", fromPhone, ex.getMessage());
                }
                return "already_confirmed_handled";
            }

            // Fallback de contexto de grupo se existir
            String groupContextId = resolveGroupContextId(payload);
            if (groupContextId != null) {
                if (intent == WebhookIntent.CONFIRM) {
                    return "confirm_group_" + groupContextId;
                } else if (intent == WebhookIntent.ALTER) {
                    return "alter_group_" + groupContextId;
                }
            }

            return processIntent(intent.name().toLowerCase(), action, payload, intent.name().toLowerCase());
        }
    }

    public List<AppointmentSession> findPendingSessionsByPhoneWithVariations(String rawPhone, String bsuid) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return List.of();
        }

        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(rawPhone, bsuid);
        String searchPhone = (dbPhone != null && !dbPhone.isBlank()) ? dbPhone : rawPhone;
        String digitsOnly = searchPhone.trim().replaceAll("\\D", "");

        if (digitsOnly.isBlank()) {
            digitsOnly = rawPhone.trim().replaceAll("\\D", "");
        }
        if (digitsOnly.isBlank()) {
            return List.of();
        }

        Set<String> phoneVariations = new LinkedHashSet<>();
        phoneVariations.add(digitsOnly);

        String without55 = digitsOnly.startsWith("55") ? digitsOnly.substring(2) : digitsOnly;
        String with55 = digitsOnly.startsWith("55") ? digitsOnly : "55" + digitsOnly;

        phoneVariations.add(without55);
        phoneVariations.add(with55);

        // Normalização de 9º dígito para telefones do Brasil (DDD + 8 ou 9 dígitos)
        if (without55.length() == 11 && without55.charAt(2) == '9') {
            String eightDigit = without55.substring(0, 2) + without55.substring(3);
            phoneVariations.add(eightDigit);
            phoneVariations.add("55" + eightDigit);
        } else if (without55.length() == 10) {
            String nineDigit = without55.substring(0, 2) + "9" + without55.substring(2);
            phoneVariations.add(nineDigit);
            phoneVariations.add("55" + nineDigit);
        }

        Map<UUID, AppointmentSession> sessionMap = new LinkedHashMap<>();
        for (String phoneVar : phoneVariations) {
            List<AppointmentSession> sessions = appointmentSessionRepository.findActiveByPhoneNumber(phoneVar);
            if (sessions != null) {
                for (AppointmentSession s : sessions) {
                    if (s.getStatus() != null
                            && s.getStatus() != AppointmentSessionStatus.CONFIRMED
                            && s.getStatus() != AppointmentSessionStatus.CANCELED
                            && s.getStatus() != AppointmentSessionStatus.CANCELED_NO_RESPONSE) {
                        sessionMap.put(s.getId(), s);
                    }
                }
            }
        }

        return new ArrayList<>(sessionMap.values());
    }

    public boolean hasRecentlyConfirmedSession(String rawPhone, String bsuid) {
        if (rawPhone == null || rawPhone.isBlank()) return false;

        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(rawPhone, bsuid);
        String searchPhone = (dbPhone != null && !dbPhone.isBlank()) ? dbPhone : rawPhone;
        String purified = preprocessor.purifyPhoneNumberForSearch(searchPhone);
        if (purified.isBlank()) {
            purified = preprocessor.purifyPhoneNumberForSearch(rawPhone);
        }
        if (purified.isBlank()) return false;

        List<AppointmentSession> sessions = appointmentSessionRepository.findActiveByPhoneNumber(purified);
        if ((sessions == null || sessions.isEmpty()) && !purified.startsWith("55")) {
            sessions = appointmentSessionRepository.findActiveByPhoneNumber("55" + purified);
        }

        if (sessions != null) {
            for (AppointmentSession s : sessions) {
                if (s.getStatus() == AppointmentSessionStatus.CONFIRMED) {
                    return true;
                }
            }
        }
        return false;
    }

    public String resolveGroupContextId(BlipWebhookPayload payload) {
        if (payload == null || payload.from() == null || payload.from().isBlank()) {
            return null;
        }

        try {
            String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(payload.from(), payload.bsuid());
            if (dbPhone != null && !dbPhone.isBlank()) {
                List<AppointmentSession> activeSessions = transactionTemplate.execute(status ->
                    appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
                );
                if (activeSessions != null) {
                    for (AppointmentSession session : activeSessions) {
                        if (session.getCurrentGroupId() != null) {
                            return session.getCurrentGroupId().toString();
                        }
                    }
                }
            }

            String groupId = blipContextService.getUserContext(payload.from(), "groupId");
            if (groupId == null) {
                return null;
            }

            String normalizedGroupId = groupId.trim();
            if (normalizedGroupId.isBlank() || "null".equalsIgnoreCase(normalizedGroupId)) {
                return null;
            }

            return normalizedGroupId;
        } catch (Exception ex) {
            log.debug("[WEBHOOK] Falha ao resolver groupId do contexto para {}: {}", payload.from(), ex.getMessage());
            return null;
        }
    }

    private String processIntent(String prefix, String action, BlipWebhookPayload payload, String label) {
        String resolvedId = resolveActiveAppointmentId(payload);
        if (resolvedId != null && !resolvedId.isBlank()) {
            log.info("[WEBHOOK] Texto livre de {} '{}' interceptado. Mapeando para {}_{}", label, action, prefix, resolvedId);
            return prefix + "_" + resolvedId;
        }
        return action;
    }

    public String resolveActiveAppointmentId(BlipWebhookPayload payload) {
        String resolvedId = payload.appointmentId();
        if (resolvedId == null || resolvedId.isBlank()) {
            String fromPhone = payload.from();
            if (fromPhone != null && !fromPhone.isBlank()) {
                String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, payload.bsuid());

                String cleanPhone = preprocessor.purifyPhoneNumberForSearch(dbPhone);
                if (cleanPhone.isEmpty()) {
                    cleanPhone = preprocessor.purifyPhoneNumberForSearch(fromPhone);
                }

                final String finalPhone = cleanPhone;
                List<AppointmentSession> activeSessions = transactionTemplate.execute(status ->
                    appointmentSessionRepository.findActiveByPhoneNumber(finalPhone)
                );
                if (activeSessions != null && !activeSessions.isEmpty()) {
                    resolvedId = activeSessions.getFirst().getFeegowAppointmentId();
                }
            }
        }
        return resolvedId;
    }
}
