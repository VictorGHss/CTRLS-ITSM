package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipWebhookPayload;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo pré-processamento de payloads de webhook do Blip:
 * - Reconciliação do 9º dígito e túneis determinísticos.
 * - Injeção de contexto (payloadclique).
 * - Validação de tokens de segurança e auditoria.
 * - Filtragem preventiva Anti-Ghost (mensagens automáticas de ausência comercial).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlipWebhookPreprocessor {

    private final BlipLIMEClient blipLimeClient;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipContextService blipContextService;
    private final BlipProperties blipProperties;
    private final BlipIdentityReconciler blipIdentityReconciler;
    private final BlipUserIdentityReconciliationRepositoryPort blipUserIdentityReconciliationRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final AuditPort auditPort;

    public BlipWebhookPayload enrichPayload(BlipWebhookPayload payload) {
        if (payload == null) {
            return null;
        }

        String inboundIdentity = payload.from();
        String reconciledIdentity = inboundIdentity;
        try {
            reconciledIdentity = blipLimeClient.reconcileNinthDigit(inboundIdentity, appointmentSessionRepository);
        } catch (Exception ex) {
            log.warn("[WEBHOOK-RECONCILE-WARN] Falha graciosa ao reconciliar nono dígito para {}: {}", inboundIdentity, ex.getMessage());
        }

        BlipWebhookPayload enriched = new BlipWebhookPayload(
            payload.messageId(),
            payload.appointmentId(),
            payload.action(),
            reconciledIdentity,
            payload.token(),
            payload.content(),
            payload.metadata(),
            payload.bsuid(),
            payload.type()
        );

        injectPayloadCliqueContext(inboundIdentity, reconciledIdentity, enriched.action());
        proactiveTunnelReconciliation(enriched);

        return enriched;
    }

    private void injectPayloadCliqueContext(String inboundIdentity, String reconciledIdentity, String actionForContext) {
        if (actionForContext != null && (actionForContext.contains("confirm_") || actionForContext.contains("alter_"))) {
            try {
                blipContextService.setUserContextForUser(inboundIdentity, "payloadclique", actionForContext);
                if (reconciledIdentity != null && !reconciledIdentity.equalsIgnoreCase(inboundIdentity)) {
                    blipContextService.setUserContextForUser(reconciledIdentity, "payloadclique", actionForContext);
                }

                // Também tenta setar no túnel determinístico
                try {
                    String subbotId = blipProperties.getSubbotId();
                    String subbotLocalPart = null;
                    if (subbotId != null && !subbotId.isBlank() && !subbotId.toLowerCase().contains("fluxov1")) {
                        subbotLocalPart = subbotId.trim();
                        if (subbotLocalPart.contains("@")) {
                            subbotLocalPart = subbotLocalPart.substring(0, subbotLocalPart.indexOf('@'));
                        }
                    }
                    if (subbotLocalPart != null) {
                        String phoneDigits = inboundIdentity.trim();
                        if (phoneDigits.contains("@")) {
                            phoneDigits = phoneDigits.substring(0, phoneDigits.indexOf('@'));
                        }
                        phoneDigits = phoneDigits.replaceAll("\\D", "");
                        if (!phoneDigits.startsWith("55") && !phoneDigits.isEmpty()) {
                            phoneDigits = "55" + phoneDigits;
                        }
                        String deterministicTunnel = phoneDigits + "." + subbotLocalPart + "@tunnel.msging.net";
                        blipContextService.setUserContextForUser(deterministicTunnel, "payloadclique", actionForContext);
                        log.info("[WEBHOOK] Injetado payloadclique={} no túnel determinístico: {}", actionForContext, deterministicTunnel);
                    }
                } catch (Exception ex) {
                    log.warn("[WEBHOOK] Falha ao setar payloadclique no túnel determinístico: {}", ex.getMessage());
                }

                log.info("[WEBHOOK] Injetado payloadclique={} no contexto de {}", actionForContext, inboundIdentity);
            } catch (Exception ex) {
                log.warn("[WEBHOOK] Falha ao setar payloadclique no contexto: {}", ex.getMessage());
            }
        }
    }

    private void proactiveTunnelReconciliation(BlipWebhookPayload payload) {
        if (payload.metadata() instanceof Map<?, ?> metadataMap) {
            Object rawFromObj = metadataMap.get("rawFrom");
            if (rawFromObj != null) {
                String rawFrom = rawFromObj.toString().trim();
                if (rawFrom.contains("@tunnel.msging.net")) {
                    String guid = rawFrom.substring(0, rawFrom.indexOf('@')).trim();
                    boolean isUuid = guid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
                    if (isUuid) {
                        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(payload.from(), payload.bsuid());
                        if (dbPhone != null && !dbPhone.isBlank()) {
                            try {
                                Optional<BlipUserIdentityReconciliation> existing =
                                    blipUserIdentityReconciliationRepository.findByBlipGuid(guid);
                                if (existing.isEmpty()) {
                                    BlipUserIdentityReconciliation newRec =
                                        BlipUserIdentityReconciliation.builder()
                                            .blipGuid(guid)
                                            .bsuid(payload.bsuid())
                                            .phoneNumber(dbPhone)
                                            .build();
                                    blipUserIdentityReconciliationRepository.save(newRec);
                                    log.info("[WEBHOOK] Reconciliação proativa salva para túnel GUID: {} -> Telefone={}", guid, dbPhone);
                                }
                            } catch (Exception ex) {
                                log.warn("[WEBHOOK] Falha ao salvar reconciliação proativa: {}", ex.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    public void validateWebhookToken(BlipWebhookPayload payload) {
        String expectedToken = appointmentMotorProperties.getSecurity().getWebhookToken();
        if (expectedToken != null && !expectedToken.isBlank()) {
            if (payload.token() == null || !secureCompare(expectedToken, payload.token())) {
                log.warn("Token de webhook inválido.");
                throw new SecurityException("Invalid token");
            }
            auditPort.record(
                    "APPOINTMENT_MOTOR",
                    "ASSINATURA_VALIDADA",
                    "Assinatura do webhook validada. messageId=" + payload.messageId(),
                    resolveTraceId());
        }
    }

    public boolean isAntiGhostCommercialResponse(BlipWebhookPayload payload) {
        if ("text/plain".equalsIgnoreCase(payload.type()) && (payload.action() == null || payload.action().isBlank())) {
            String messageText = "";
            if (payload.content() instanceof String text) {
                messageText = text;
            } else if (payload.content() instanceof Map<?, ?> map) {
                Object textObj = map.get("text");
                if (textObj != null) {
                    messageText = textObj.toString();
                }
            }

            String lowerText = messageText.toLowerCase();
            boolean isInteractive = lowerText.contains("confirm_")
                || lowerText.contains("alter_")
                || lowerText.contains("cancel_")
                || lowerText.contains("ver_agenda_")
                || lowerText.contains("group_view_")
                || lowerText.contains("finalizar_agendamento");

            if (!isInteractive) {
                if ((lowerText.contains("agradece") && lowerText.contains("contato"))
                    || lowerText.contains("como podemos ajudar")
                    || lowerText.contains("estamos ausentes")
                    || lowerText.contains("mensagem automática")
                    || lowerText.contains("mensagem automatica")) {
                    log.info("[ANTI-GHOST] Resposta automática comercial interceptada e descartada para o telefone: {}. Ignorando criação de ticket.", payload.from());
                    return true;
                }
            }
        }
        return false;
    }

    public String purifyPhoneNumberForSearch(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return "";
        }
        String clean = rawPhone.trim();
        if (clean.contains("@")) {
            clean = clean.substring(0, clean.indexOf('@')).trim();
        }
        if (clean.contains(".")) {
            clean = clean.substring(0, clean.indexOf('.')).trim();
        }
        clean = clean.replaceAll("\\D", "");
        if (clean.startsWith("55") && clean.length() > 10) {
            clean = clean.substring(2);
        }
        return clean;
    }

    private boolean secureCompare(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
            a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("trace_id");
        }
        return traceId;
    }
}
