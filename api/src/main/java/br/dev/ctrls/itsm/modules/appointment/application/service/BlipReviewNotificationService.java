package br.dev.ctrls.itsm.modules.appointment.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.client.BlipContactClientAdapter;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço especialista no envio e parametrização de templates de avaliação médica
 * do Google Review (pesquisa_avaliacao_google_itsm_v6) via protocolo LIME / WABA da Blip.
 */
@Slf4j
@Service
@Observed
@RequiredArgsConstructor
public class BlipReviewNotificationService {

    private static final String DEFAULT_REVIEW_TEMPLATE = "pesquisa_avaliacao_google_itsm_v6";

    private final BlipLIMEClient limeClient;
    private final BlipContextService blipContextService;

    /**
     * Envia o template de pesquisa de avaliação do Google Review via mensagem LIME nativa
     * com componentes WABA (WhatsApp Meta API).
     *
     * @param destination      Identidade ou telefone do paciente (ex: 5511999999999@wa.gw.msging.net)
     * @param templateName     Nome do template WABA aprovado na Meta
     * @param patientName      Nome do paciente para interpolação
     * @param doctorName       Nome do médico/especialista
     * @param doctorIdOrParam  Parâmetro do botão (ID do médico ou hash de roteamento)
     */
    public void sendReviewTemplateMessage(String destination, String templateName, String patientName, String doctorName, String doctorIdOrParam) {
        String wabaDestination = ensureWabaIdentity(destination);
        if (wabaDestination == null || !wabaDestination.contains("@")) {
            log.warn("[TELEFONE-INVÁLIDO] Abortando envio do template de avaliação '{}'. Destino '{}' inválido.",
                    templateName, destination);
            return;
        }

        String safeDoctorIdParam = (doctorIdOrParam != null && !doctorIdOrParam.isBlank())
                ? doctorIdOrParam.trim().replaceAll("\\s+", "")
                : "default";

        String safePatientName = "Paciente";
        if (patientName != null && !patientName.isBlank() && !"null".equalsIgnoreCase(patientName.trim())) {
            String trimmedP = patientName.trim();
            boolean isInvalid = trimmedP.matches("(?i).*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*")
                || trimmedP.matches("^\\d+$")
                || BlipContactClientAdapter.isInvalidName(trimmedP);
            if (!isInvalid) {
                safePatientName = trimmedP;
            }
        }

        String safeDoctorName = "Recepção Central";
        if (doctorName != null && !doctorName.isBlank() && !"null".equalsIgnoreCase(doctorName.trim())) {
            String trimmedD = doctorName.trim();
            if (!BlipContactClientAdapter.isInvalidName(trimmedD)) {
                safeDoctorName = trimmedD;
            }
        }

        String effectiveTemplateName = (templateName != null && !templateName.isBlank())
                ? templateName.trim()
                : DEFAULT_REVIEW_TEMPLATE;

        Map<String, Object> bodyParam1 = Map.of("type", "text", "text", safePatientName);
        Map<String, Object> bodyParam2 = Map.of("type", "text", "text", safeDoctorName);

        Map<String, Object> bodyComponent = Map.of(
            "type", "body",
            "parameters", List.of(bodyParam1, bodyParam2)
        );

        Map<String, Object> buttonParam = Map.of(
            "type", "text",
            "text", safeDoctorIdParam
        );

        Map<String, Object> buttonComponent = Map.of(
            "type", "button",
            "sub_type", "url",
            "index", "0",
            "parameters", List.of(buttonParam)
        );

        Map<String, Object> templateObj = Map.of(
            "name", effectiveTemplateName,
            "language", Map.of("code", "pt_BR"),
            "components", List.of(bodyComponent, buttonComponent)
        );

        Map<String, Object> contentObj = Map.of(
            "type", "template",
            "template", templateObj
        );

        Map<String, Object> messagePayload = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", wabaDestination,
            "type", "application/json",
            "content", contentObj
        );

        try {
            var response = limeClient.executeMessage(messagePayload, BlipLIMEClient.AuthorizationScope.ROUTER);
            Object status = response != null ? response.getOrDefault("status", "success") : "success";
            log.info("[GOOGLE-REVIEW] Template nativo WABA '{}' enviado via LIME message. destination={}, patient={}, doctor={}, doctorId={}, status={}",
                    effectiveTemplateName, wabaDestination, safePatientName, safeDoctorName, safeDoctorIdParam, status);

            if (blipContextService != null) {
                blipContextService.updateUserMasterState(wabaDestination, BlipContextService.STATE_REVIEW_FINISHED);
            }
        } catch (Exception e) {
            log.error("[GOOGLE-REVIEW] Falha ao enviar template nativo WABA '{}' para {}: {}", effectiveTemplateName, wabaDestination, e.getMessage(), e);
            throw new RuntimeException("Falha ao enviar avaliação nativa WABA no Blip para " + wabaDestination + ": " + e.getMessage(), e);
        }
    }

    public void sendReviewTemplateMessage(String destination, String templateName, String doctorIdOrParam) {
        sendReviewTemplateMessage(destination, templateName, "Paciente", "Recepção Central", doctorIdOrParam);
    }

    private String ensureWabaIdentity(String destination) {
        if (destination == null || destination.isBlank()) {
            return "unknown@wa.gw.msging.net";
        }
        String cleaned = destination.trim();
        if (cleaned.contains("@")) {
            int idx = cleaned.indexOf('@');
            String local = cleaned.substring(0, idx).trim();
            String domain = cleaned.substring(idx + 1).trim();
            if (local.matches("^\\+?\\d+$")) {
                local = local.replaceAll("\\D", "");
            }
            return local + "@" + domain;
        }
        String digits = cleaned.replaceAll("\\D", "");
        return digits + "@wa.gw.msging.net";
    }
}
