package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipTemplateDto;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Observed
public class BlipNotificationService {

    private final BlipLIMEClient limeClient;
    private final BlipTemplateParameterResolver blipTemplateParameterResolver;
    private final AppointmentMotorProperties motorProperties;
    private final BlipPayloadBuilder blipPayloadBuilder;
    private final BlipContextService blipContextService;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipAppointmentFormatter blipAppointmentFormatter;
    private final BlipReviewNotificationService blipReviewNotificationService;

    @org.springframework.beans.factory.annotation.Value("${notification.blocked-doctor-ids:46}")
    private String rawBlockedDoctorIds = "46";

    private java.util.Set<String> parsedBlockedDoctorIds = new java.util.HashSet<>();

    @jakarta.annotation.PostConstruct
    public void initBlockedDoctorIds() {
        parseBlockedDoctorIds(this.rawBlockedDoctorIds);
    }

    public void setRawBlockedDoctorIds(String rawBlockedDoctorIds) {
        this.rawBlockedDoctorIds = rawBlockedDoctorIds;
        parseBlockedDoctorIds(rawBlockedDoctorIds);
    }

    public java.util.Set<String> getBlockedDoctorIds() {
        if (parsedBlockedDoctorIds.isEmpty() && rawBlockedDoctorIds != null && !rawBlockedDoctorIds.isBlank()) {
            parseBlockedDoctorIds(rawBlockedDoctorIds);
        }
        return parsedBlockedDoctorIds;
    }

    private synchronized void parseBlockedDoctorIds(String raw) {
        this.parsedBlockedDoctorIds = new java.util.HashSet<>();
        if (raw != null && !raw.isBlank()) {
            String[] tokens = raw.split("[,;\\s]+");
            for (String token : tokens) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    this.parsedBlockedDoctorIds.add(trimmed);
                }
            }
        }
    }

    public BlipNotificationService(
            BlipLIMEClient limeClient,
            BlipTemplateParameterResolver blipTemplateParameterResolver,
            AppointmentMotorProperties motorProperties,
            BlipPayloadBuilder blipPayloadBuilder,
            BlipContextService blipContextService,
            br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort appointmentSessionRepository,
            BlipAppointmentFormatter blipAppointmentFormatter,
            BlipReviewNotificationService blipReviewNotificationService) {
        this.limeClient = limeClient;
        this.blipTemplateParameterResolver = blipTemplateParameterResolver;
        this.motorProperties = motorProperties;
        this.blipPayloadBuilder = blipPayloadBuilder;
        this.blipContextService = blipContextService;
        this.appointmentSessionRepository = appointmentSessionRepository;
        this.blipAppointmentFormatter = blipAppointmentFormatter;
        this.blipReviewNotificationService = blipReviewNotificationService;
    }

    public List<BlipTemplateDto> fetchTemplatesFromBlip() {
        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@wa.gw.msging.net",
            "method", "get",
            "uri", "/message-templates"
        );
        try {
            var response = limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            // A resposta agora é um Map direto, não um ResponseEntity
            Map<String, Object> body = response;
            if (body == null || !body.containsKey("resource")) return List.of();
            
            Object resourceObj = body.get("resource");
            if (resourceObj instanceof Map<?, ?> resourceMap) {
                Object docsObj = resourceMap.get("documents");
                if (docsObj instanceof List<?> docs) {
                    List<BlipTemplateDto> templates = new ArrayList<>();
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    for (Object item : docs) {
                        if (item instanceof Map<?, ?> itemMap) {
                            String status = String.valueOf(itemMap.get("status"));
                            if (!"APPROVED".equalsIgnoreCase(status)) continue;
                            
                            String id = String.valueOf(itemMap.get("id"));
                            String name = String.valueOf(itemMap.get("name"));
                            String bodyContent = "{}";
                            try {
                                bodyContent = mapper.writeValueAsString(itemMap);
                            } catch (JsonProcessingException ignored) {}
                            templates.add(new BlipTemplateDto(id, name, bodyContent));
                        }
                    }
                    return templates;
                }
            }
            return List.of();
        } catch (RuntimeException ex) {
            log.error("Erro ao buscar templates no Blip", ex);
            return List.of();
        }
    }

    public void sendAppointmentNotification(String destination, AppointmentTemplateData appointmentData) {
        sendTemplateMessage(destination, motorProperties.getBlipTemplateConfirmation(), appointmentData);
    }

    public void sendTemplateMessage(String destination, String templateName, AppointmentTemplateData appointmentData) {
        String recipientE164 = BlipPayloadBuilder.formatE164Recipient(destination);
        if (recipientE164 == null || recipientE164.length() < 14) {
            log.warn("[TELEFONE-INVÁLIDO] Abortando envio do template '{}'. O destino '{}' é inválido ou possui menos de 11 dígitos com DDD (E.164 resultante: {}).",
                    templateName, destination, recipientE164);
            return;
        }

        String doctorId = null;
        if (appointmentData != null) {
            doctorId = appointmentData.doctorId();
        }
        
        if (!isDoctorAllowed(doctorId)) {
            log.warn("[SANDBOX] Disparo bloqueado. Dr ID: {}, destination={}, template={}",
                doctorId != null ? doctorId : "null",
                destination,
                templateName);
            return;
        }

        List<Map<String, String>> parameters = blipTemplateParameterResolver.buildDynamicParameters(templateName, appointmentData);
        String appointmentId = appointmentData == null ? "" : Objects.toString(appointmentData.appointmentId(), "");

        log.info("[PARAMS TEMPLATE] destination={}, template={}, params={}", recipientE164, templateName, parameters);

        if (parameters.isEmpty()) {
            if (!isStaticZeroParamTemplate(templateName)) {
                log.error("[ABORT] Parâmetros vazios para o template dinâmico '{}'. Envio cancelado para evitar mensagem sem conteúdo. destination={}",
                    templateName, recipientE164);
                return;
            }
            log.info("[TEMPLATE-ESTATICO] Template '{}' possui 0 parâmetros. Disparando sem variáveis via Active Campaign.", templateName);
        }

        Map<String, String> messageParamValues = null;
        List<String> messageParamKeys = null;

        if (!parameters.isEmpty()) {
            messageParamValues = new java.util.LinkedHashMap<>();
            messageParamKeys = new ArrayList<>();
            for (int i = 0; i < parameters.size(); i++) {
                String key = String.valueOf(i + 1);
                String val = parameters.get(i).getOrDefault("text", "");
                messageParamValues.put(key, val);
                messageParamKeys.add(key);
            }
        }

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String campaignName = "Confirmacao Consulta - " + (appointmentId.isBlank() ? uniqueSuffix : appointmentId + " - " + uniqueSuffix);

        Map<String, Object> commandPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
            campaignName,
            recipientE164,
            templateName,
            messageParamValues,
            messageParamKeys,
            null,
            null,
            null
        );

        try {
            var response = limeClient.executeCommand(commandPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
            validateBlipResponse(response, templateName, recipientE164);
            log.info("Template enviado via Active Campaign (/campaign/full). destination={}, template={}, status={}", recipientE164, templateName, response != null ? response.get("status") : "success");
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "";
            String safePatientName = (appointmentData != null && appointmentData.patientName() != null && !appointmentData.patientName().isBlank())
                    ? appointmentData.patientName().trim()
                    : "Paciente";

            if (errMsg.contains("131008")) {
                log.warn("[AUTOCORRECAO-TEMPLATE] Meta recusou disparo de '{}' com a quantidade atual de parâmetros (131008). Reenviando com 1 parâmetro (nome do paciente)...", templateName);
                try {
                    Map<String, Object> retryPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
                            "Notificacao Retry 131008 - " + UUID.randomUUID().toString().substring(0, 8), recipientE164, templateName,
                            Map.of("1", safePatientName), List.of("1"), null, null, null
                    );
                    var retryResponse = limeClient.executeCommand(retryPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
                    validateBlipResponse(retryResponse, templateName, recipientE164);
                    log.info("[AUTOCORRECAO-TEMPLATE] Reenvio de '{}' com 1 parâmetro concluído com sucesso!", templateName);
                    return;
                } catch (Exception retryEx) {
                    log.error("[AUTOCORRECAO-TEMPLATE] Falha no reenvio com 1 parâmetro: {}", retryEx.getMessage());
                }
            } else if (errMsg.contains("132000")) {
                log.warn("[AUTOCORRECAO-TEMPLATE] Meta recusou disparo de '{}' com parâmetros (132000). Reenviando estaticamente com 0 parâmetros...", templateName);
                try {
                    Map<String, Object> retryPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
                            "Notificacao Retry 132000 - " + UUID.randomUUID().toString().substring(0, 8), recipientE164, templateName,
                            Map.of(), List.of(), null, null, null
                    );
                    var retryResponse = limeClient.executeCommand(retryPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
                    validateBlipResponse(retryResponse, templateName, recipientE164);
                    log.info("[AUTOCORRECAO-TEMPLATE] Reenvio estático de '{}' sem parâmetros concluído com sucesso!", templateName);
                    return;
                } catch (Exception retryEx) {
                    log.error("[AUTOCORRECAO-TEMPLATE] Falha no reenvio estático: {}", retryEx.getMessage());
                }
            }
            throw e;
        }
    }

    public static boolean isStaticZeroParamTemplate(String templateName) {
        if (templateName == null || templateName.isBlank()) return false;
        String norm = templateName.trim().toLowerCase().replace(" ", "_");
        return "aviso_agendamento_grupo".equals(norm) || "aviso_confirmacao_pendente_grupo".equals(norm);
    }

    public void sendGroupTemplateMessage(String destination, String templateName, java.util.UUID groupId, String patientName) {
        String recipientE164 = BlipPayloadBuilder.formatE164Recipient(destination);
        if (recipientE164 == null || recipientE164.length() < 14) {
            log.warn("[TELEFONE-INVÁLIDO] Abortando envio do template de grupo '{}'. O destino '{}' é inválido ou possui menos de 11 dígitos com DDD (E.164 resultante: {}).",
                    templateName, destination, recipientE164);
            return;
        }

        String stateIdPrepararAtendimento = "a0776d9c-6486-42f3-8a4f-2706f0185908";

        // --- PREPARAÇÃO E INJEÇÃO AUTOMÁTICA DA VARIÁVEL lista_detalhada E DADOS DE GRUPO NO CONTEXTO DO BLIP ---
        try {
            if (groupId != null && appointmentSessionRepository != null && blipAppointmentFormatter != null && blipContextService != null) {
                var sessions = appointmentSessionRepository.findByCurrentGroupId(groupId);
                if (sessions != null && !sessions.isEmpty()) {
                    String listaDetalhada = blipAppointmentFormatter.buildListaDetalhada(sessions);
                    if (listaDetalhada != null && !listaDetalhada.isBlank()) {
                        blipContextService.setUserContext(recipientE164, "lista_detalhada", listaDetalhada);
                        blipContextService.setUserContext(recipientE164, "listaDetalhada", listaDetalhada);
                        blipContextService.setUserContext(recipientE164, "groupId", groupId.toString());
                        blipContextService.setUserContext(recipientE164, "isConfirmingAgenda", "true");

                        if (destination != null && !destination.equalsIgnoreCase(recipientE164)) {
                            blipContextService.setUserContext(destination, "lista_detalhada", listaDetalhada);
                            blipContextService.setUserContext(destination, "listaDetalhada", listaDetalhada);
                            blipContextService.setUserContext(destination, "groupId", groupId.toString());
                            blipContextService.setUserContext(destination, "isConfirmingAgenda", "true");
                        }
                        log.info("[MENSAGERIA-GRUPO] Contexto de grupo (lista_detalhada, listaDetalhada, groupId) injetado com sucesso no Blip para {}. groupId={}", recipientE164, groupId);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[MENSAGERIA-GRUPO] Falha ao pré-injetar contexto de grupo no Blip: {}", ex.getMessage());
        }

        String safePatientName = (patientName != null && !patientName.isBlank() && !"null".equalsIgnoreCase(patientName.trim()))
                ? patientName.trim()
                : "Paciente";

        String targetBot = null;
        String builderBotId = motorProperties.getBlipBuilderBotId();
        if (builderBotId != null && !builderBotId.isBlank() && !builderBotId.toLowerCase().contains("fluxov1")) {
            targetBot = builderBotId;
        }

        // Define o Master-State do paciente se houver bot de produção configurado e não for ambiente de teste
        if (targetBot != null && !targetBot.isBlank()) {
            try {
                blipContextService.setMasterState(recipientE164, targetBot, stateIdPrepararAtendimento);
                if (destination != null && !destination.equalsIgnoreCase(recipientE164)) {
                    blipContextService.setMasterState(destination, targetBot, stateIdPrepararAtendimento);
                }
            } catch (Exception ex) {
                log.warn("[MENSAGERIA-GRUPO] Falha ao definir master-state para Preparar_Atendimento: {}", ex.getMessage());
            }
        }

        Map<String, Object> commandPayload = blipPayloadBuilder.buildGroupTemplatePayload(
                recipientE164, templateName, resolveWabaNamespace(), groupId, safePatientName,
                targetBot, targetBot != null ? stateIdPrepararAtendimento : null, targetBot
        );

        log.info("[MENSAGERIA-GRUPO] Transmitindo template de grupo '{}' (0 parâmetros) via Active Campaign (/campaign/full) para o telefone={} com o groupId={}", templateName, recipientE164, groupId);
        try {
            var response = limeClient.executeCommand(commandPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
            validateBlipResponse(response, templateName, recipientE164);
            log.info("[MENSAGERIA-GRUPO] Template de grupo '{}' disparado com sucesso via Active Campaign para o telefone={}", templateName, recipientE164);
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errMsg.contains("131008") || errMsg.contains("number of localizable_params (0) does not match the expected number of params (1)")) {
                log.warn("[AUTOCORRECAO-TEMPLATE] Meta indicou que o template de grupo '{}' requer 1 parâmetro (131008). Reenviando com parâmetro 1 (nome do paciente)...", templateName);
                try {
                    Map<String, Object> retryPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
                            "Aviso Grupo Retry 131008 - " + UUID.randomUUID().toString().substring(0, 8), recipientE164, templateName,
                            Map.of("1", safePatientName), List.of("1"), targetBot, stateIdPrepararAtendimento, targetBot
                    );
                    var retryResponse = limeClient.executeCommand(retryPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
                    validateBlipResponse(retryResponse, templateName, recipientE164);
                    log.info("[AUTOCORRECAO-TEMPLATE] Reenvio de '{}' com parâmetro 1 concluído com sucesso!", templateName);
                    return;
                } catch (Exception retryEx) {
                    log.error("[AUTOCORRECAO-TEMPLATE] Falha no reenvio com parâmetro 1: {}", retryEx.getMessage());
                }
            } else if (errMsg.contains("132000") || errMsg.contains("number of localizable_params (1) does not match the expected number of params (0)")
                    || errMsg.contains("message template is not valid") || errMsg.contains("IEnumerable")) {
                log.warn("[AUTOCORRECAO-TEMPLATE] Blip/Meta indicou incompatibilidade de parâmetros para o template de grupo '{}'. Reenviando com 0 parâmetros estáticos...", templateName);
                try {
                    Map<String, Object> retryPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
                            "Aviso Grupo Retry Estático - " + UUID.randomUUID().toString().substring(0, 8), recipientE164, templateName,
                            null, null, targetBot, stateIdPrepararAtendimento, targetBot
                    );
                    var retryResponse = limeClient.executeCommand(retryPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
                    validateBlipResponse(retryResponse, templateName, recipientE164);
                    log.info("[AUTOCORRECAO-TEMPLATE] Reenvio estático de '{}' sem parâmetros concluído com sucesso!", templateName);
                    return;
                } catch (Exception retryEx) {
                    log.error("[AUTOCORRECAO-TEMPLATE] Falha no reenvio estático: {}", retryEx.getMessage());
                }
            }

            log.error("[ERRO-CRITICO-GRUPO-TRANSMISSAO] Erro ao transmitir template de grupo '{}' para o telefone={} com o groupId={}. Motivo: {}",
                    templateName, recipientE164, groupId, e.getMessage());

            // FALLBACK DEFENSIVO (Code 61 / Template Inválido): Se o template de grupo falhar no Blip, aciona fallback para 'aviso_confirmacao_pendente_v2'
            String fallbackTemplate = "aviso_confirmacao_pendente_v2";
            log.info("[FALLBACK-TEMPLATE-NUDGE] Acionando fallback defensivo do template de grupo '{}' -> '{}' para o destinatário {}",
                    templateName, fallbackTemplate, recipientE164);
            try {
                AppointmentTemplateData fallbackData = new AppointmentTemplateData(null, null, safePatientName, null, null, null, null, null, null, null, null, null);
                sendSimpleTemplateMessage(destination, fallbackTemplate, fallbackData);
                log.info("[FALLBACK-TEMPLATE-NUDGE] Fallback para '{}' com paciente '{}' disparado com sucesso no Blip para o destino {}", fallbackTemplate, safePatientName, recipientE164);
            } catch (Exception fallbackEx) {
                log.error("[FALLBACK-TEMPLATE-NUDGE] Falha ao disparar template de fallback '{}' para {}: {}", fallbackTemplate, recipientE164, fallbackEx.getMessage());
                throw e;
            }
        }
    }

    public void sendSimpleTemplateMessage(String destination, String templateName, AppointmentTemplateData appointmentData) {
        String recipientE164 = BlipPayloadBuilder.formatE164Recipient(destination);
        if (recipientE164 == null || recipientE164.length() < 14) {
            log.warn("[TELEFONE-INVÁLIDO] Abortando envio do template simples '{}'. O destino '{}' é inválido ou possui menos de 11 dígitos com DDD (E.164 resultante: {}).",
                    templateName, destination, recipientE164);
            return;
        }

        String doctorId = null;
        if (appointmentData != null) {
            doctorId = appointmentData.doctorId();
        }
        
        if (!isDoctorAllowed(doctorId)) {
            log.warn("[SANDBOX] Disparo bloqueado. Dr ID: {}, destination={}, template={}",
                doctorId != null ? doctorId : "null",
                destination,
                templateName);
            return;
        }

        List<Map<String, String>> parameters = blipTemplateParameterResolver.buildDynamicParameters(templateName, appointmentData);
        String appointmentId = appointmentData == null ? "" : Objects.toString(appointmentData.appointmentId(), "");

        log.info("[PARAMS TEMPLATE] destination={}, template={}, params={}", recipientE164, templateName, parameters);

        if (parameters.isEmpty()) {
            if (!isStaticZeroParamTemplate(templateName)) {
                log.error("[ABORT] Parâmetros vazios para o template dinâmico '{}'. Envio cancelado para evitar mensagem sem conteúdo. destination={}",
                    templateName, recipientE164);
                return;
            }
            log.info("[MENSAGERIA] Template estático de 0 parâmetros '{}' validado. Prosseguindo com envio.", templateName);
        }

        String targetBot = null;
        String builderBotId = motorProperties.getBlipBuilderBotId();
        if (builderBotId != null && !builderBotId.isBlank() && !builderBotId.toLowerCase().contains("fluxov1")) {
            targetBot = builderBotId;
        }
        String stateIdPrepararAtendimento = (targetBot != null && !targetBot.isBlank())
                ? "a0776d9c-6486-42f3-8a4f-2706f0185908"
                : null;

        Map<String, String> messageParamValues = new java.util.LinkedHashMap<>();
        List<String> messageParamKeys = new ArrayList<>();

        for (int i = 0; i < parameters.size(); i++) {
            String key = String.valueOf(i + 1);
            String val = parameters.get(i).getOrDefault("text", "");
            messageParamValues.put(key, val);
            messageParamKeys.add(key);
        }

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String campaignName = "Disparo Consulta - " + (appointmentId.isBlank() ? uniqueSuffix : appointmentId + " - " + uniqueSuffix);

        Map<String, Object> commandPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
            campaignName,
            recipientE164,
            templateName,
            isStaticZeroParamTemplate(templateName) ? null : messageParamValues,
            isStaticZeroParamTemplate(templateName) ? null : messageParamKeys,
            targetBot,
            stateIdPrepararAtendimento,
            targetBot
        );

        log.info("Transmitindo template '{}' via Active Campaign (/campaign/full). destination={}", templateName, recipientE164);
        try {
            var response = limeClient.executeCommand(commandPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
            validateBlipResponse(response, templateName, recipientE164);
            log.info("Template enviado via Active Campaign. destination={}, template={}, status={}", recipientE164, templateName, response != null ? response.get("status") : "success");
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errMsg.contains("131008") || errMsg.contains("number of localizable_params (0) does not match the expected number of params (1)")) {
                log.warn("[AUTOCORRECAO-TEMPLATE] Meta indicou que o template '{}' requer 1 parâmetro (131008). Reenviando com parâmetro 1 (nome do paciente)...", templateName);
                String safePatientName = (appointmentData != null && appointmentData.patientName() != null) ? appointmentData.patientName() : "Paciente";
                try {
                    Map<String, Object> retryPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
                            "Disparo Retry 131008 - " + UUID.randomUUID().toString().substring(0, 8), recipientE164, templateName,
                            Map.of("1", safePatientName), List.of("1"), targetBot, stateIdPrepararAtendimento, targetBot
                    );
                    var retryResponse = limeClient.executeCommand(retryPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
                    validateBlipResponse(retryResponse, templateName, recipientE164);
                    log.info("[AUTOCORRECAO-TEMPLATE] Reenvio de '{}' com parâmetro 1 concluído com sucesso!", templateName);
                    return;
                } catch (Exception retryEx) {
                    log.error("[AUTOCORRECAO-TEMPLATE] Falha no reenvio com parâmetro 1: {}", retryEx.getMessage());
                }
            } else if (errMsg.contains("132000") || errMsg.contains("number of localizable_params (1) does not match the expected number of params (0)")) {
                log.warn("[AUTOCORRECAO-TEMPLATE] Meta indicou que o template '{}' não aceita parâmetros (132000). Reenviando com 0 parâmetros...", templateName);
                try {
                    Map<String, Object> retryPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
                            "Disparo Retry 132000 - " + UUID.randomUUID().toString().substring(0, 8), recipientE164, templateName,
                            null, null, targetBot, stateIdPrepararAtendimento, targetBot
                    );
                    var retryResponse = limeClient.executeCommand(retryPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
                    validateBlipResponse(retryResponse, templateName, recipientE164);
                    log.info("[AUTOCORRECAO-TEMPLATE] Reenvio estático de '{}' sem parâmetros concluído com sucesso!", templateName);
                    return;
                } catch (Exception retryEx) {
                    log.error("[AUTOCORRECAO-TEMPLATE] Falha no reenvio estático: {}", retryEx.getMessage());
                }
            }
            throw e;
        }
    }

    private void validateBlipResponse(Map<String, Object> response, String templateName, String recipient) {
        if (response == null || response.isEmpty()) {
            log.error("[LIME-FAILURE] Resposta nula ou vazia do Blip para o template '{}' (destinatário={}).", templateName, recipient);
            throw new br.dev.ctrls.inovareti.modules.appointment.domain.exception.BlipNotificationException(
                "Resposta nula ou vazia do servidor Blip ao enviar template " + templateName
            );
        }

        String status = String.valueOf(response.getOrDefault("status", "unknown"));

        if ("failure".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status) || "offline-queued".equalsIgnoreCase(status) || "timeout".equalsIgnoreCase(status)) {
            Object reasonObj = response.get("reason");
            String reasonStr = reasonObj != null ? reasonObj.toString() : "desconhecida";
            log.error("[LIME-FAILURE] Disparo de template '{}' rejeitado pelo Blip (destinatário={}). Status: {}, Motivo: {}",
                templateName, recipient, status, reasonStr);
            throw new br.dev.ctrls.inovareti.modules.appointment.domain.exception.BlipNotificationException(
                "Envio de template '" + templateName + "' falhou no Blip. Status: " + status + ", Motivo: " + reasonStr
            );
        }

        if (response.containsKey("reason")) {
            Object reasonObj = response.get("reason");
            if (reasonObj instanceof Map<?, ?> reasonMap) {
                Object codeObj = reasonMap.get("code");
                Object descObj = reasonMap.get("description");
                if (codeObj != null) {
                    log.error("[LIME-FAILURE] Comando LIME retornou código de erro {} ({}) para o template '{}' (destinatário={}).",
                        codeObj, descObj, templateName, recipient);
                    throw new br.dev.ctrls.inovareti.modules.appointment.domain.exception.BlipNotificationException(
                        "Falha na API do Blip ao enviar template '" + templateName + "'. Código de Erro: " + codeObj + " - " + descObj
                    );
                }
            }
        }
    }



    /**
     * Envia uma mensagem de texto simples (text/plain) diretamente para o WhatsApp do destinatário
     * via protocolo LIME. Disparo ativo â€” não depende de transição de bloco no Builder.
     *
     * @param destination identidade do destinatário (ex: "5511999999999@wa.gw.msging.net")
     * @param text        corpo da mensagem a ser enviada
     */
    public void sendPlainTextMessage(String destination, String text) {
        if (destination == null || destination.isBlank() || text == null || text.isBlank()) {
            log.warn("[PLAIN-TEXT] Destino ou texto inválido. Envio cancelado. destination={}", destination);
            return;
        }
        String normalizedDestination = ensureWabaIdentity(destination);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("to", normalizedDestination);
        payload.put("from", "roteadorprincipal57@msging.net");
        payload.put("type", "text/plain");
        payload.put("content", text);
        try {
            var response = limeClient.executeMessage(payload, BlipLIMEClient.AuthorizationScope.ROUTER);
            Object status = response != null ? response.getOrDefault("status", "unknown") : "unknown";
            log.info("[PLAIN-TEXT] Mensagem de texto enviada ativamente. destination={}, status={}", normalizedDestination, status);
        } catch (RuntimeException ex) {
            log.error("[PLAIN-TEXT] Falha ao enviar mensagem de texto para {}. Erro: {}", normalizedDestination, ex.getMessage(), ex);
        }
    }

    /**
     * Envia uma mensagem interativa do tipo select (Quick Reply) contendo a lista de agendamentos
     * e os botões rápidos de confirmação e alteração de grupo.
     */
    public void sendGroupScheduleMessage(String destination, String text, java.util.UUID groupId) {
        if (destination == null || destination.isBlank() || text == null || text.isBlank() || groupId == null) {
            log.warn("[SELECT-MESSAGE] Parâmetros inválidos. destination={}, text={}, groupId={}", destination, text, groupId);
            return;
        }
        String normalizedDestination = ensureWabaIdentity(destination);

        Map<String, Object> optionConfirm = Map.of(
            "text", "CONFIRMAR TUDO",
            "previewText", "CONFIRMAR TUDO",
            "value", "confirm_group_" + groupId.toString(),
            "type", "text/plain",
            "index", 0
        );

        Map<String, Object> optionAlter = Map.of(
            "text", "PRECISO ALTERAR",
            "previewText", "PRECISO ALTERAR",
            "value", "alter_group_" + groupId.toString(),
            "type", "text/plain",
            "index", 1
        );

        String fullMessageText = text + "\n\nPor favor, confirme se você comparecerá aos horários listados acima.";

        Map<String, Object> content = Map.of(
            "text", fullMessageText,
            "scope", "immediate",
            "options", List.of(optionConfirm, optionAlter)
        );

        Map<String, Object> payload = Map.of(
            "id", java.util.UUID.randomUUID().toString(),
            "to", normalizedDestination,
            "from", "roteadorprincipal57@msging.net",
            "type", "application/vnd.lime.select+json",
            "content", content
        );

        try {
            var response = limeClient.executeMessage(payload, BlipLIMEClient.AuthorizationScope.ROUTER);
            Object status = response != null ? response.getOrDefault("status", "unknown") : "unknown";
            log.info("[SELECT-MESSAGE] Mensagem de grupo interativa (select) enviada. destination={}, status={}", normalizedDestination, status);
        } catch (RuntimeException ex) {
            log.error("[SELECT-MESSAGE] Falha ao enviar select de grupo para {}. Erro: {}", normalizedDestination, ex.getMessage(), ex);
        }
    }

    private String resolveWabaNamespace() {
        String ns = motorProperties.getBlipWabaNamespace();
        return (ns != null && !ns.isBlank()) ? ns : "";
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

    /**
     * Verifica se um médico tem permissão para receber disparos de lembretes/notificações.
     * 
     * CONCEITOS SEPARADOS:
     * 1. Lista de Bloqueio por Médico (Blocklist): Defina por `notification.blocked-doctor-ids`
     *    (default: "46"). Médicos nesta lista têm disparos sumariamente bloqueados (retorna false).
     * 2. Modo Sandbox / Allowlist (`testDoctorIds` / `activeDoctorIds`): Define quais médicos estão
     *    liberados para teste em ambiente de sandbox ou em produção.
     */
    public boolean isDoctorAllowed(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return true;
        }
        String docId = doctorId.trim();

        // 1. Bloqueio explícito por lista de bloqueio (Blocklist)
        if (getBlockedDoctorIds().contains(docId)) {
            return false;
        }

        // 2. Verificação de Sandbox / Allowlist (testDoctorIds e activeDoctorIds)
        if (motorProperties.getTestDoctorIds().contains(docId)) {
            return true;
        }
        if (motorProperties.getActiveDoctorIds().contains(docId)) {
            return true;
        }

        // 3. Comportamento Padrão: Se nenhuma allowlist específica estiver ativa, permite por padrão (fail-open)
        if (motorProperties.getTestDoctorIds().isEmpty() && motorProperties.getActiveDoctorIds().isEmpty()) {
            return true;
        }

        return false;
    }

    /**
     * Envia o template de pesquisa de avaliação do Google Review delegando para o serviço especialista
     * {@link BlipReviewNotificationService}.
     */
    public void sendReviewTemplateMessage(String destination, String templateName, String patientName, String doctorName, String doctorIdOrParam) {
        blipReviewNotificationService.sendReviewTemplateMessage(destination, templateName, patientName, doctorName, doctorIdOrParam);
    }

    public void sendReviewTemplateMessage(String destination, String templateName, String doctorIdOrParam) {
        blipReviewNotificationService.sendReviewTemplateMessage(destination, templateName, doctorIdOrParam);
    }
}

