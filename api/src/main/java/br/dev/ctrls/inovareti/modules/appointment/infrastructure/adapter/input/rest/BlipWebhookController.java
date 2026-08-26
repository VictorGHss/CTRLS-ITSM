package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import io.micrometer.observation.annotation.Observed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.config.security.WebhookSignatureValidator;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookInboundService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookIntentMatcher;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller que gerencia a recepção de webhooks de mensagens e notificações da Blip.
 * Implementa validação criptográfica de integridade HMAC-SHA256 e prevenção de duplicidade.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Webhooks - Blip", description = "Endpoints de integração de webhooks para o motor de agendamentos e controle da Blip")
@Observed
public class BlipWebhookController {

    private final HandleBlipWebhookUseCase handleBlipWebhookUseCase;
    private final BlipWebhookInboundService blipWebhookInboundService;
    private final BlipWebhookIdempotencyService idempotencyService;
    private final BlipWebhookIntentMatcher intentMatcher;
    private final ObjectMapper objectMapper;
    private final WebhookSignatureValidator webhookSignatureValidator;
    private final Environment env;
    private final BlipContextService blipContextService;
    private final BlipNotificationService blipNotificationService;
    private final BlipProperties blipProperties;

    @Value("${blip.webhook.secret}")
    private String blipWebhookSecret;

    @Value("${blip.webhook.token:}")
    private String blipWebhookToken;

    @Operation(
        summary = "Recebe e processa webhooks enviados pelo Blip",
        description = "Este endpoint recebe as mensagens enviadas pela plataforma Blip, executa a validação de assinatura criptográfica HMAC-SHA256 (X-Blip-Signature) para atestar a autenticidade e aplica controle de idempotência de eventos."
    )
    @PostMapping(value = {"/v1/webhook/blip", "/webhooks/blip"})
    public ResponseEntity<?> blipWebhook(
            @RequestHeader(value = "X-Inovare-Token", required = false) String inovareToken,
            @RequestHeader(value = "X-Blip-Signature", required = false) String blipSignature,
            HttpServletRequest request,
            @RequestBody(required = false) String rawJson) {

        log.debug("Recebido POST em /api/v1/webhook/blip. Payload bruto: {}", rawJson);
        log.debug("[ALERTA REDE] Requisição bruta da Take Blip ACABOU de tocar o Tomcat na porta 8085!");

        // 1. VALIDAÇÃO DE ASSINATURA CRIPTOGRÁFICA (HMAC-SHA256) E TOKENS DE SEGURANÇA IMEDIATA
        byte[] bodyBytes = null;
        if (request instanceof ContentCachingRequestWrapper wrappedRequest) {
            bodyBytes = wrappedRequest.getContentAsByteArray();
        }
        if (bodyBytes == null || bodyBytes.length == 0) {
            bodyBytes = (rawJson != null) ? rawJson.getBytes(StandardCharsets.UTF_8) : new byte[0];
        }

        boolean isSignatureValid = webhookSignatureValidator.isValid(bodyBytes, blipSignature, blipWebhookSecret);

        String expectedToken = StringUtils.hasText(blipWebhookToken)
            ? blipWebhookToken
            : System.getenv("APP_BLIP_SECURITY_WEBHOOK_TOKEN");

        boolean hasTokenMatch = StringUtils.hasText(inovareToken)
            && StringUtils.hasText(expectedToken)
            && secureCompare(expectedToken, inovareToken);

        boolean isBypassProfile = env.acceptsProfiles(Profiles.of("local", "default"));
        boolean isBypassEnabled = hasTokenMatch || (isBypassProfile && StringUtils.hasText(inovareToken));

        if (!isSignatureValid && !isBypassEnabled) {
            log.warn("[ACESSO NEGADO] Assinatura do webhook inválida ou ausente. Bypass por token inativo no perfil de produção.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "status", "error",
                "reason", "unauthorized",
                "message", "Assinatura HMAC ou Token de seguranca invalido."
            ));
        }

        if (!isSignatureValid && isBypassEnabled) {
            log.debug("[BYPASS] Assinatura ausente ou inválida, mas acesso liberado por token confiável configurado.");
        }

        // 2. PARSER DO PAYLOAD
        Map<String, Object> payload;
        try {
            payload = (rawJson != null && !rawJson.isBlank())
                ? objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {})
                : Map.of();
        } catch (Exception e) {
            log.error("Erro ao converter payload do webhook em Map", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "reason", "invalid_payload",
                "message", "Formato de payload invalido."
            ));
        }

        if (payload == null || payload.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "processed", "reason", "empty-payload"));
        }

        BlipWebhookInboundService.ParsedInbound parsed = blipWebhookInboundService.parse(payload);

        String messageId = parsed.messageId();
        String appointmentId = parsed.appointmentId();
        String action = parsed.action();
        String from = parsed.from();
        Object content = parsed.content();

        // 3. IDEMPOTÊNCIA (Prevenção de Duplicidade): Early Return 200 se for duplicado
        boolean isNotification = payload.containsKey("event");
        if (!isNotification && !idempotencyService.isFirstTimeProcessing(messageId)) {
            log.debug("[IDEMPOTÊNCIA] Evento duplicado ignorado. messageId='{}'", messageId);
            return ResponseEntity.ok(Map.of(
                    "status", "processed",
                    "reason", "duplicate-ignored"
            ));
        }

        // BLINDAGEM STATE-LOCK: Se o paciente estiver no fluxo de confirmação e mandar texto livre
        String isConfirmingAgenda = (from != null && !from.isBlank())
                ? blipContextService.getUserContext(from, "isConfirmingAgenda")
                : null;

        if ("true".equalsIgnoreCase(isConfirmingAgenda)) {
            String rawText = action != null ? action.trim() : "";
            String actionValue = action != null ? action.trim() : "";
            String rawActionTextLower = (rawText + " " + actionValue).toLowerCase();

            boolean isButtonClick = intentMatcher.isButtonClickOrExplicitIntent(action, actionValue, rawActionTextLower, rawText);

            if (!isButtonClick) {
                log.info("[STATE-LOCK] Paciente {} enviou texto livre '{}' durante fluxo de confirmacao de agenda. Ignorando entrada.", from, action);
                
                try {
                    String parkingBlockId = blipProperties.getBlocks().getWaitingResponse();
                    if (parkingBlockId == null || parkingBlockId.isBlank()) {
                        parkingBlockId = blipProperties.getBlocks().getExibirAgenda();
                    }
                    if (parkingBlockId != null && !parkingBlockId.isBlank()) {
                        blipContextService.changeMasterState(from, parkingBlockId);
                        log.info("[STATE-LOCK] Master State congelado/resetado no bloco seguro '{}' para o paciente {}.", parkingBlockId, from);
                    }
                } catch (Exception ex) {
                    log.warn("[STATE-LOCK] Falha ao congelar Master State para {}: {}", from, ex.getMessage());
                }

                if (idempotencyService.shouldSendOrientation(from)) {
                    try {
                        blipNotificationService.sendPlainTextMessage(from, "Por favor, utilize os botões acima para confirmar ou alterar seu agendamento.");
                        log.info("[STATE-LOCK] Enviada mensagem de orientação para o paciente {}.", from);
                    } catch (Exception e) {
                        log.error("Erro ao enviar plain text message de orientacao para {}: {}", from, e.getMessage());
                    }
                } else {
                    log.info("[STATE-LOCK] Orientação suprimida por rate limit de 30s para o paciente {}.", from);
                }

                return ResponseEntity.ok(Map.of(
                    "status", "ignored",
                    "reason", "state-locked-text-ignored"
                ));
            }
        }

        Map<String, Object> metadata = new java.util.HashMap<>(intentMatcher.extractMetadata(payload));
        if (parsed.rawFrom() != null) {
            metadata.put("rawFrom", parsed.rawFrom());
        }

        if (action == null || action.isBlank() || "received".equalsIgnoreCase(action) || "consumed".equalsIgnoreCase(action)) {
            log.debug("[WEBHOOK] 📥 Recebido | Ação: {} | De: {} | ID: {}", action, from, messageId);
        } else {
            log.info("[WEBHOOK] 📥 Recebido | Ação: {} | De: {} | ID: {}", action, from, messageId);
        }

        if (action != null && (action.startsWith("confirm_") || action.startsWith("alter_"))) {
            final String phoneForClear = from;
            CompletableFuture.runAsync(() -> {
                try {
                    blipContextService.setUserContextForUser(phoneForClear, "isConfirmingAgenda", "false");
                } catch (Exception e) {
                    log.warn("Erro ao limpar isConfirmingAgenda no contexto para {}: {}", phoneForClear, e.getMessage());
                }
            });
        }

        HandleBlipWebhookUseCase.WebhookResult result;
        try {
            result = handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                    messageId,
                    appointmentId,
                    action,
                    from,
                    inovareToken,
                    content,
                    metadata,
                    parsed.bsuid(),
                    parsed.type()));
        } catch (NotFoundException ex) {
            log.warn("[WEBHOOK-AVISO] Recurso não localizado ao processar o webhook do Blip. Detalhes: {}", ex.getMessage());
            return ResponseEntity.ok(Map.of(
                "status", "ignored",
                "reason", "not-found"
            ));
        } catch (Throwable ex) {
            log.error("[WEBHOOK-CRITICAL] Erro inesperado ao processar webhook do Blip para messageId='{}', action='{}'", messageId, action, ex);
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "reason", "internal-fallback-handled",
                "message", ex.getMessage() != null ? ex.getMessage() : "Erro tratado com fallback seguro"
            ));
        }

        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "processed", "queue", ""));
        }

        if ("Não".equalsIgnoreCase(result.action())) {
            return ResponseEntity.ok(Map.of(
                "status", "ignored",
                "reason", "declined"
            ));
        }

        if ("Integrar_GerAcesso".equalsIgnoreCase(result.action()) || "Finalizar_Agendamento".equalsIgnoreCase(result.action())) {
            String resolvedId = result.patientCPF() != null ? result.patientCPF() : "";
            if (resolvedId.isEmpty()) {
                resolvedId = appointmentId != null ? appointmentId : "";
            }
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "action", result.action(),
                "appointmentId", resolvedId
            ));
        }

        return ResponseEntity.ok(new WebhookResponse(
            Objects.requireNonNullElse(result.queue(), ""),
            Objects.requireNonNullElse(result.patientName(), ""),
            Objects.requireNonNullElse(result.patientCPF(), ""),
            Objects.requireNonNullElse(result.patientBirthdate(), ""),
            Objects.requireNonNullElse(result.action(), ""),
            Objects.requireNonNullElse(result.doctorName(), "")
        ));
    }

    @Operation(
        summary = "Dispara manualmente fluxos de agendamento por API",
        description = "Permite simular ou forçar requisições de webhooks Blip de forma controlada via painel administrativo para depuração e testes."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/webhooks/blip/manual-trigger", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> manualTrigger(
            @RequestHeader(value = "X-Inovare-Token", required = false) String inovareToken,
            @RequestBody ManualTriggerRequest body) {
        if (body == null
                || !StringUtils.hasText(body.identity())
                || !StringUtils.hasText(body.appointmentId())
                || !StringUtils.hasText(body.action())) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ignored",
                "reason", "missing-fields"));
        }

        String normalizedAction = body.action().trim().toLowerCase();
        String actionPrefix = switch (normalizedAction) {
            case "confirm" -> "confirm";
            case "alter" -> "alter";
            default -> null;
        };

        if (actionPrefix == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ignored",
                "reason", "invalid-action"));
        }

        String appointmentId = body.appointmentId().trim();
        String action = actionPrefix + "_" + appointmentId;

        log.info("[MANUAL TRIGGER] identity='{}' | action='{}' | appointmentId='{}'",
            body.identity(), action, appointmentId);

        handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                UUID.randomUUID().toString(),
                appointmentId,
                action,
                body.identity().trim(),
                inovareToken,
                null,
                Map.of()), true);

        return ResponseEntity.ok(Map.of());
    }

    public static boolean isConfirmationOrAlterationIntentText(String text) {
        return BlipWebhookIntentMatcher.isConfirmationOrAlterationIntentText(text);
    }

    public record ManualTriggerRequest(
            @JsonProperty("identity") String identity,
            @JsonAlias({"appointment_id", "appointmentId"}) @JsonProperty("appointment_id") String appointmentId,
            @JsonProperty("action") String action) {
    }

    public record WebhookResponse(
            @JsonProperty("status") String status,
            @JsonProperty("queue") String queue,
            @JsonProperty("patientName") String patientName,
            @JsonProperty("patientCPF") String patientCPF,
            @JsonProperty("patientBirthdate") String patientBirthdate,
            @JsonProperty("action") String action,
            @JsonProperty("doctorName") String doctorName) {
        public WebhookResponse(String queue, String patientName, String patientCPF, String patientBirthdate, String action, String doctorName) {
            this("ok", queue, patientName, patientCPF, patientBirthdate, action, doctorName);
        }
    }

    private boolean secureCompare(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
