package br.dev.ctrls.itsm.modules.appointment.application.usecase;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.itsm.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.itsm.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.itsm.modules.access.domain.service.AccessService;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipDeliveryFailureHandler;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipDeskRoutingService;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipGroupActionHandler;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipIdempotencyService;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipIdentityReconciler;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipIntentResolver;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipNudgeResponseHandler;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipPayloadParser;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipPhysicalAccessHandler;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipPrepararExibirHandler;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipTextSanitizer;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipWebhookActionExecutor;
import br.dev.ctrls.itsm.modules.appointment.application.service.BlipWebhookPreprocessor;
import br.dev.ctrls.itsm.modules.appointment.application.service.FeegowBulkIntegrationHandler;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso orquestrador principal para recepção e roteamento de Webhooks do Blip.
 * Intercepta payloads, valida idempotência e tokens de segurança,
 * delega o processamento para serviços especializados e executa pipelines de ação via BlipWebhookActionExecutor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandleBlipWebhookUseCase {

    private final BlipWebhookPreprocessor preprocessor;
    private final BlipPrepararExibirHandler prepararExibirHandler;
    private final BlipDeskRoutingService deskRoutingService;
    private final BlipIntentResolver intentResolver;
    private final BlipPhysicalAccessHandler physicalAccessHandler;
    private final BlipDeliveryFailureHandler deliveryFailureHandler;
    private final BlipGroupActionHandler blipGroupActionHandler;
    private final BlipNudgeResponseHandler blipNudgeResponseHandler;
    private final BlipIdempotencyService blipIdempotencyService;
    private final BlipPayloadParser blipPayloadParser;
    private final BlipContextService blipContextService;
    private final BlipWebhookActionExecutor blipWebhookActionExecutor;
    private final FeegowBulkIntegrationHandler feegowBulkIntegrationHandler;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final ProfessionalExternalPort professionalExternalPort;
    private final BlipTextSanitizer blipTextSanitizer;
    private final AuditPort auditPort;
    private final TransactionTemplate transactionTemplate;
    private final BlipProperties blipProperties;
    private final BlipIdentityReconciler blipIdentityReconciler;
    private final AccessService accessService;

    private record SessionDbData(
        AppointmentSession session,
        AppointmentDoctorMapping doctorMapping
    ) {}

    public enum WebhookIntent {
        CONFIRM, CANCEL, ALTER, UNKNOWN
    }

    public static WebhookIntent detectIntent(String text) {
        return BlipIntentResolver.detectIntent(text);
    }

    public static boolean isWithinBusinessHours() {
        return BlipDeskRoutingService.isWithinBusinessHours();
    }

    public WebhookResult applySilentDeskRouting(String fromPhone) {
        return deskRoutingService.applySilentDeskRouting(fromPhone);
    }

    public WebhookResult applySilentDeskRouting(String fromPhone, BlipWebhookPayload payload) {
        return deskRoutingService.applySilentDeskRouting(fromPhone, payload);
    }

    public void executeNotificationFailure(BlipDeliveryFailureCommand failureCommand) {
        deliveryFailureHandler.executeNotificationFailure(failureCommand);
    }

    public WebhookResult execute(BlipWebhookPayload rawPayload) {
        return execute(rawPayload, false);
    }

    /**
     * Ponto de entrada para execução do processamento do Webhook do Blip.
     */
    public WebhookResult execute(BlipWebhookPayload rawPayload, boolean skipTokenValidation) {
        if (rawPayload == null) {
            return null;
        }

        BlipWebhookPayload payload = preprocessor.enrichPayload(rawPayload);
        String actionValue = payload.action() != null ? payload.action().trim() : "";
        String rawText = actionValue + " " + (payload.content() != null ? payload.content().toString() : "");
        String rawLower = rawText.toLowerCase();

        String prepararUuid = blipProperties.getBlocks().getPrepararAtendimento();
        String exibirUuid = blipProperties.getBlocks().getExibirAgenda();

        boolean isPrepararAtendimento = "preparar_atendimento".equalsIgnoreCase(actionValue)
            || (prepararUuid != null && Pattern.compile("\\b" + Pattern.quote(prepararUuid.toLowerCase()) + "\\b").matcher(rawLower).find());
        boolean isExibirAgenda = "exibir_agenda".equalsIgnoreCase(actionValue)
            || (exibirUuid != null && Pattern.compile("\\b" + Pattern.quote(exibirUuid.toLowerCase()) + "\\b").matcher(rawLower).find());

        if (isPrepararAtendimento || isExibirAgenda) {
            return prepararExibirHandler.handlePrepararOuExibir(payload, isPrepararAtendimento);
        }

        if (!skipTokenValidation) {
            preprocessor.validateWebhookToken(payload);
        }

        if (!blipIdempotencyService.registerIfFirstTime(payload.messageId())) {
            log.debug("Ação ignorada no webhook (idempotência). messageId={}", payload.messageId());
            return null;
        }

        String fromPhone = payload.from();

        // Anti-Ghost: Filtra respostas de ausência comercial antes de processar qualquer texto livre
        if (preprocessor.isAntiGhostCommercialResponse(payload)) {
            return new WebhookResult("", "", "", "", "processed", "");
        }

        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, payload.bsuid());
        dbPhone = preprocessor.purifyPhoneNumberForSearch(dbPhone);
        if (dbPhone.isEmpty()) {
            dbPhone = preprocessor.purifyPhoneNumberForSearch(fromPhone);
        }
        String action = payload.action() != null ? payload.action().trim() : "";

        if (action.isBlank()) {
            String fallbackAction = blipPayloadParser.resolveActionFromContent(payload.content());
            if (fallbackAction != null && !fallbackAction.isBlank()) {
                action = fallbackAction.trim();
            }
        }

        // Interceptação de respostas ao lembrete ("Já estou na clínica" / "Estou a caminho")
        WebhookResult reminderResult = handleReminderNoticeIfApplicable(action, payload, fromPhone, dbPhone);
        if (reminderResult != null) {
            return reminderResult;
        }

        Pattern uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        if (uuidPattern.matcher(action).matches()) {
            String msgType = payload.type();
            boolean isLegitimateType = "text/plain".equalsIgnoreCase(msgType)
                    || "application/vnd.lime.reply+json".equalsIgnoreCase(msgType);
            if (isLegitimateType) {
                WebhookResult groupResult = blipGroupActionHandler.handleGroupAction(action, fromPhone, payload.bsuid(), payload.metadata());
                if (groupResult != null) {
                    return groupResult;
                }
                WebhookResult result = handleUuidAction(action, dbPhone);
                if (result != null) {
                    return result;
                }
            } else {
                log.debug("[WEBHOOK] Ignorando UUID de ação '{}' pois o tipo de mensagem '{}' não é legítimo para cliques.", action, msgType);
            }
        }

        String normalizedAction = action.trim().toLowerCase();

        // Intercepta ações de grupo em texto/botões ("CONFIRMAR TUDO", "1", etc.)
        WebhookResult groupTextResult = blipGroupActionHandler.handleGroupAction(normalizedAction, fromPhone, payload.bsuid(), payload.metadata());
        if (groupTextResult != null) {
            return groupTextResult;
        }

        if (blipNudgeResponseHandler.handleNudgeResponse(normalizedAction, action, fromPhone, payload.bsuid())) {
            return new WebhookResult("", "", "", "", "nudge_response_processed", "");
        }

        action = intentResolver.resolveTextIntentions(normalizedAction, action, payload);

        if ("Atendimento humano".equalsIgnoreCase(actionValue)
                || "atendimento humano".equalsIgnoreCase(actionValue)
                || "atendimento_humano".equalsIgnoreCase(actionValue)
                || "Atendimento humano".equalsIgnoreCase(action)
                || "atendimento humano".equalsIgnoreCase(action)
                || "atendimento_humano".equalsIgnoreCase(action)) {
            log.info("[WEBHOOK] Recebida ação Atendimento humano para {}", fromPhone);
            return deskRoutingService.applySilentDeskRouting(fromPhone, payload);
        }

        switch (action) {
            case "Atendimento humano", "atendimento humano", "atendimento_humano":
                log.info("[WEBHOOK] Recebida ação Atendimento humano para {}", fromPhone);
                return deskRoutingService.applySilentDeskRouting(fromPhone, payload);

            case "already_confirmed_handled":
                log.info("[WEBHOOK] Agendamento já confirmado tratado amigavelmente para {}", fromPhone);
                return new WebhookResult("", "", "", "", "already_confirmed_handled", "");

            case "Sucesso_Confirmacao", "CONFIRMAR_AGENDAMENTO", "confirmar_agendamento", "Confirmar_Agendamento":
                return handleSucessoConfirmacao(fromPhone, payload);

            case "Integrar_GerAcesso":
                return physicalAccessHandler.handleIntegrarGerAcesso(payload, fromPhone);

            case "Não", "nao", "Nao", "NÃO":
                return physicalAccessHandler.handleNaoAction(payload, fromPhone);

            case "Finalizar_Agendamento":
                return physicalAccessHandler.handleFinalizarAgendamento(payload, fromPhone);
        }

        WebhookResult groupResult = blipGroupActionHandler.handleGroupAction(action, fromPhone, payload.bsuid(), payload.metadata());
        if (groupResult != null) {
            return groupResult;
        }

        Pattern pattern = Pattern.compile("(?i)(confirm|alter|cancel)_(\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(action);

        if (!matcher.find()) {
            return handleFreeTextRouting(action, payload, fromPhone, dbPhone);
        }

        String actionType = matcher.group(1).toLowerCase();
        String rawId = matcher.group(2);
        String appointmentId = blipPayloadParser.normalizeFeegowAppointmentId(rawId);

        if (appointmentId == null || appointmentId.isBlank()) {
            log.warn("[WEBHOOK] Payload {}_ recebido sem ID válido. action={}", actionType, action);
            return null;
        }

        if (!blipIdempotencyService.tryAcquireLock(appointmentId)) {
            return blipIdempotencyService.getCachedResultOrSpinWait(appointmentId, actionType);
        }

        log.info("[WEBHOOK] Processando ação '{}' para agendamento ID={}", actionType, appointmentId);

        SessionDbData dbData = fetchSessionDbData(appointmentId, dbPhone);
        if (dbData == null) {
            return null;
        }

        String doctorName = resolveDoctorName(dbData, appointmentId);
        String queue = resolveQueue(dbData);
        String dispatchIdentity = blipPayloadParser.resolveDispatchIdentity(payload.from(), dbData.session());

        return blipWebhookActionExecutor.execute(
                actionType,
                action,
                appointmentId,
                dbData.session(),
                doctorName,
                queue,
                dispatchIdentity
        );
    }

    private WebhookResult handleReminderNoticeIfApplicable(String action, BlipWebhookPayload payload, String fromPhone, String dbPhone) {
        String rawContentText = "";
        if (payload.content() instanceof String strContent) {
            rawContentText = strContent;
        } else if (payload.content() instanceof Map<?, ?> mapContent) {
            Object tObj = mapContent.get("text");
            if (tObj != null) {
                rawContentText = tObj.toString();
            }
        }
        String combinedActionText = (action + " " + rawContentText).trim().toLowerCase();

        boolean isLembreteResposta = combinedActionText.contains("já estou na clínica")
                || combinedActionText.contains("ja estou na clinica")
                || combinedActionText.contains("estou a caminho")
                || combinedActionText.contains("a caminho");

        if (isLembreteResposta) {
            String statusInfo = combinedActionText.contains("caminho") ? "Estou a caminho" : "Já estou na clínica";
            log.info("[LEMBRETE-RESPOSTA] Paciente ID {} (De: {}) informou status: {}.",
                    dbPhone.isEmpty() ? fromPhone : dbPhone, fromPhone, statusInfo);

            String searchPhone = (dbPhone != null && !dbPhone.isEmpty()) ? dbPhone : fromPhone;

            try {
                blipContextService.clearQueueRedirect(fromPhone);
                if (searchPhone != null && !searchPhone.equalsIgnoreCase(fromPhone)) {
                    blipContextService.clearQueueRedirect(searchPhone);
                }

                List<String> targets = List.of(fromPhone, searchPhone);
                for (String target : targets) {
                    if (target == null || target.isBlank()) continue;
                    blipContextService.setUserContext(target, "flow_action", "reminder_notice");
                    blipContextService.setUserContext(target, "lembrete_resposta", statusInfo);
                }

                String confirmSuccessBlockId = blipProperties.getBlocks().getConfirmSuccess();
                if (confirmSuccessBlockId != null && !confirmSuccessBlockId.isBlank()) {
                    blipContextService.changeMasterState(fromPhone, confirmSuccessBlockId);
                    if (searchPhone != null && !searchPhone.equalsIgnoreCase(fromPhone)) {
                        blipContextService.changeMasterState(searchPhone, confirmSuccessBlockId);
                    }
                }
            } catch (Exception ex) {
                log.warn("[LEMBRETE-FINALIZACAO] Falha ao ajustar estado de finalização no Blip: {}", ex.getMessage());
            }

            log.info("[LEMBRETE-FINALIZACAO] Resposta de lembrete do paciente={} direcionada para auto-encerramento (sem fila de Desk).", searchPhone);
            return new WebhookResult("", "", "", "", "reminder_notice_auto_closed", "");
        }
        return null;
    }

    private WebhookResult handleSucessoConfirmacao(String fromPhone, BlipWebhookPayload payload) {
        log.info("[WEBHOOK] Recebida ação Sucesso_Confirmacao. De: {} | ID: {}", fromPhone, payload.messageId());
        try {
            String isGroupStr = blipContextService.getUserContext(fromPhone, "isGroupFlow");
            String groupIdStr = blipContextService.getUserContext(fromPhone, "groupId");

            if ("true".equalsIgnoreCase(isGroupStr) && groupIdStr != null && !groupIdStr.isBlank()) {
                UUID groupId = UUID.fromString(groupIdStr.trim());
                log.info("[WEBHOOK] Detectado fluxo de grupo em Sucesso_Confirmacao. Disparando confirmGroupAsync para groupId={}", groupId);
                feegowBulkIntegrationHandler.confirmGroupAsync(groupId, fromPhone);
                return new WebhookResult("", "", "", "", "Sucesso_Confirmacao", "");
            }

            // Tratamento robusto para consulta individual em Sucesso_Confirmacao / CONFIRMAR_AGENDAMENTO
            log.info("[WEBHOOK] Sucesso_Confirmacao para agendamento individual. De: {}", fromPhone);
            String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, payload.bsuid());
            String appointmentId = payload.appointmentId();
            if (appointmentId == null || appointmentId.isBlank()) {
                appointmentId = blipContextService.getUserContext(fromPhone, "idAgendamentoFeegow");
            }
            if (appointmentId == null || appointmentId.isBlank()) {
                appointmentId = blipContextService.getUserContext(fromPhone, "appointmentId");
            }
            if (appointmentId == null || appointmentId.isBlank()) {
                appointmentId = blipContextService.getUserContext(fromPhone, "last_pending_appointment_id");
            }
            if (appointmentId == null || appointmentId.isBlank()) {
                String searchPhone = (dbPhone != null && !dbPhone.isBlank()) ? dbPhone : fromPhone;
                List<AppointmentSession> activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(searchPhone);
                if (activeSessions != null && !activeSessions.isEmpty()) {
                    appointmentId = activeSessions.getFirst().getFeegowAppointmentId();
                }
            }

            if (appointmentId != null && !appointmentId.isBlank()) {
                String finalApptId = appointmentId.trim();
                SessionDbData dbData = fetchSessionDbData(finalApptId, dbPhone != null ? dbPhone : fromPhone);
                if (dbData != null) {
                    String doctorName = resolveDoctorName(dbData, finalApptId);
                    String queue = resolveQueue(dbData);
                    String dispatchIdentity = blipPayloadParser.resolveDispatchIdentity(payload.from(), dbData.session());
                    log.info("[WEBHOOK] Confirmando agendamento individual {} via Sucesso_Confirmacao", finalApptId);
                    return blipWebhookActionExecutor.execute(
                            "confirm",
                            "confirm_" + finalApptId,
                            finalApptId,
                            dbData.session(),
                            doctorName,
                            queue,
                            dispatchIdentity
                    );
                } else {
                    log.warn("[WEBHOOK] Sessão não encontrada no banco para appointmentId={} em Sucesso_Confirmacao", finalApptId);
                }
            } else {
                log.warn("[WEBHOOK] Não foi possível resolver appointmentId para {} em Sucesso_Confirmacao", fromPhone);
            }
        } catch (Exception ex) {
            log.error("[WEBHOOK] Erro ao tratar Sucesso_Confirmacao para {}: {}", fromPhone, ex.getMessage(), ex);
        }
        return new WebhookResult("", "", "", "", "Sucesso_Confirmacao", "");
    }

    private WebhookResult handleFreeTextRouting(String action, BlipWebhookPayload payload, String fromPhone, String dbPhone) {
        log.debug("[WEBHOOK] Ação ignorada (não é confirm_, alter_ nem cancel_). action='{}'", action);

        String rawContentText = "";
        if (payload.content() instanceof String strContent) {
            rawContentText = strContent;
        } else if (payload.content() instanceof Map<?, ?> mapContent) {
            Object tObj = mapContent.get("text");
            if (tObj != null) {
                rawContentText = tObj.toString();
            }
        }
        String textLower = rawContentText.toLowerCase().trim();

        if (textLower.isBlank()) {
            return null;
        }

        String safeAction = action.toLowerCase();
        boolean isBlipSystemAction = safeAction.contains("início")
                || safeAction.contains("inicio")
                || safeAction.contains("pesquisa")
                || safeAction.contains("agradecimento")
                || safeAction.contains("sucesso")
                || safeAction.contains("preparar")
                || safeAction.contains("exibir")
                || safeAction.contains("menu")
                || safeAction.contains("finalizar");

        if (isBlipSystemAction) {
            return null;
        }

        try {
            String searchPhone = !dbPhone.isEmpty() ? dbPhone : fromPhone;
            List<AppointmentSession> activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(searchPhone);
            if (activeSessions != null && !activeSessions.isEmpty()) {
                AppointmentSession mainSession = activeSessions.getFirst();

                boolean isReviewSent = mainSession.getReviewRequestedAt() != null;

                boolean isExplicitHumanRequest = textLower.contains("falar")
                        || textLower.contains("humano")
                        || textLower.contains("atendente")
                        || textLower.contains("atendimento")
                        || textLower.contains("duvida")
                        || textLower.contains("dúvida")
                        || textLower.contains("ajuda")
                        || textLower.contains("problema")
                        || textLower.contains("recepcao")
                        || textLower.contains("recepção")
                        || textLower.contains("remarcar")
                        || textLower.contains("cancelar");

                if (isReviewSent && !isExplicitHumanRequest) {
                    log.info("[GOOGLE-REVIEW-ROUTING] Resposta de avaliação/cortesia recebida de {} (reviewRequestedAt={}). Ignorando roteamento para o Desk.",
                            searchPhone, mainSession.getReviewRequestedAt());
                    return new WebhookResult("", "", "", "", "review_response_ignored", "");
                }

                if (isExplicitHumanRequest) {
                    log.info("[WEBHOOK] Solicitação explícita de atendimento humano em texto livre de {}. Limpando contexto de confirmação e roteando.", searchPhone);
                    return deskRoutingService.applySilentDeskRouting(fromPhone, payload);
                }

                boolean isExplicitConfirmation = textLower.matches("(?i)^(sim|confirmado|confirmo|confirmar|confirmar presen[cç]a|presen[cç]a|sim confirmo)$");
                if (isExplicitConfirmation && mainSession.getStatus() != AppointmentSessionStatus.CONFIRMED) {
                    String apptId = mainSession.getFeegowAppointmentId();
                    log.info("[FREE-TEXT-CONFIRM] Intenção de confirmação explícita em texto livre ('{}') para agendamento {}. Executando confirmação.", textLower, apptId);
                    SessionDbData dbData = fetchSessionDbData(apptId, searchPhone);
                    if (dbData != null) {
                        String doctorName = resolveDoctorName(dbData, apptId);
                        String queue = resolveQueue(dbData);
                        String dispatchIdentity = blipPayloadParser.resolveDispatchIdentity(payload.from(), dbData.session());
                        return blipWebhookActionExecutor.execute(
                                "confirm",
                                "confirm_" + apptId,
                                apptId,
                                dbData.session(),
                                doctorName,
                                queue,
                                dispatchIdentity
                        );
                    }
                }

                String flowAction = blipContextService.getUserContext(fromPhone, "flow_action");
                boolean isReminderContext = "reminder_notice".equalsIgnoreCase(flowAction)
                        || (mainSession.getStatusDetails() != null && mainSession.getStatusDetails().contains("PRE_NOTICE_SENT"));

                boolean isCourtesyText = textLower.matches("(?i)^(obrigado|obrigada|valeu|ok|otimo|ótimo|bom|boa|excelente|nota \\d+|\\d+|tudo certo|agradeço|agradeco|obg|blz|tmj|joia|jóa|certo|estou a caminho|ja estou na clinica|já estou na clínica)$");

                if (isReminderContext || isCourtesyText) {
                    try {
                        String apptId = mainSession.getFeegowAppointmentId();
                        String token = accessService.generateAccessToken(apptId, searchPhone);
                        blipContextService.setUserContext(fromPhone, "idAgendamentoFeegow", apptId);
                        blipContextService.setUserContext(fromPhone, "tokenAcesso", token);
                        blipContextService.setContactExtra(fromPhone, "tokenAcesso", token);

                        blipContextService.clearQueueRedirect(fromPhone);
                        if (dbPhone != null && !dbPhone.isBlank() && !dbPhone.equalsIgnoreCase(fromPhone)) {
                            blipContextService.clearQueueRedirect(dbPhone);
                        }
                        String confirmSuccessBlockId = blipProperties.getBlocks().getConfirmSuccess();
                        if (confirmSuccessBlockId != null && !confirmSuccessBlockId.isBlank()) {
                            blipContextService.changeMasterState(fromPhone, confirmSuccessBlockId);
                            if (dbPhone != null && !dbPhone.isBlank() && !dbPhone.equalsIgnoreCase(fromPhone)) {
                                blipContextService.changeMasterState(dbPhone, confirmSuccessBlockId);
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("[LEMBRETE-FINALIZACAO] Erro ao aplicar auto-encerramento em texto livre: {}", ex.getMessage());
                    }
                    log.info("[LEMBRETE-FINALIZACAO] Resposta/texto livre do paciente={} ('{}') direcionado para auto-encerramento (sem fila de Desk).", searchPhone, textLower);
                    return new WebhookResult("", "", "", "", "reminder_notice_auto_closed", "");
                }

                log.debug("[FREE-TEXT-ROUTING] Paciente {} em autoatendimento/triagem. Roteamento silencioso forçado desativado para permitir navegação livre no bot.", searchPhone);
                return null;
            }
        } catch (Exception ex) {
            log.warn("[FREE-TEXT-ROUTING] Erro ao verificar agendamentos ativos para texto livre de {}: {}", fromPhone, ex.getMessage());
        }

        return null;
    }

    private WebhookResult handleUuidAction(String action, String dbPhone) {
        log.info("[WEBHOOK] UUID puro detectado na ação. Carregando sessão de agendamento: {}", action);
        UUID sessionId = UUID.fromString(action);
        AppointmentSession session = transactionTemplate.execute(status ->
            appointmentSessionRepository.findByIdAndPhoneNumber(sessionId, dbPhone).orElse(null)
        );

        if (session != null) {
            log.info("[WEBHOOK] Sessão encontrada para UUID puro e autorizada. Enviando template individual para o agendamento Feegow={}", session.getFeegowAppointmentId());
            sendAppointmentTemplateUseCase.execute(session, br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentCategory.CONFIRMATION);
            return new WebhookResult("", "", "", "", "individual_appointment_selected", "");
        } else {
            log.info("[WEBHOOK] Nenhuma sessão encontrada ou autorizada para a ação de UUID: {}", action);
            return null;
        }
    }

    private SessionDbData fetchSessionDbData(String appointmentId, String dbPhone) {
        try {
            return transactionTemplate.execute(status -> {
                AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentIdAndPhoneNumber(appointmentId, dbPhone)
                        .or(() -> {
                            var byId = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId);
                            if (byId.isPresent()) {
                                String sessPhone = byId.get().getPhoneNumber();
                                if (sessPhone != null && dbPhone != null) {
                                    String d1 = sessPhone.replaceAll("\\D", "");
                                    String d2 = dbPhone.replaceAll("\\D", "");
                                    if (d1.equals(d2) || d1.endsWith(d2) || d2.endsWith(d1)) {
                                        return byId;
                                    }
                                }
                            }
                            return Optional.empty();
                        })
                        .orElseThrow(() -> new NotFoundException("Sessão não encontrada ou não autorizada para o paciente."));
                AppointmentDoctorMapping doctorMapping = appointmentDoctorMappingRepository
                        .findByProfissionalId(session.getDoctorProfissionalId())
                        .orElse(null);
                return new SessionDbData(session, doctorMapping);
            });
        } catch (NotFoundException ex) {
            log.warn("[WEBHOOK-AVISO] Solicitação repetida recebida para o agendamento ID={}. O agendamento já foi processado ou a sessão expirou. Ignorando duplo clique.", appointmentId);
            return null;
        } catch (IllegalStateException ex) {
            log.error("Erro na leitura transacional inicial do webhook para appointmentId={}. Detalhes: {}", appointmentId, ex.getMessage(), ex);
            return null;
        } catch (RuntimeException ex) {
            log.error("Erro de infraestrutura ao buscar sessão do webhook para appointmentId={}. Detalhes: {}", appointmentId, ex.getMessage(), ex);
            return null;
        }
    }

    private String resolveDoctorName(SessionDbData dbData, String appointmentId) {
        String doctorName = dbData.doctorMapping() != null ? dbData.doctorMapping().getProfissionalNome() : null;
        if (doctorName == null || doctorName.isBlank()) {
            try {
                doctorName = professionalExternalPort.getProfessionalName(dbData.session().getDoctorProfissionalId());
            } catch (IllegalStateException e) {
                log.warn("Não foi possível buscar o nome do médico na Feegow, usando fallback. erro={}", e.getMessage());
                auditPort.record(
                        "APPOINTMENT_MOTOR",
                        "CIRCUIT_BREAKER_FALLBACK",
                        "Fallback ao buscar nome do profissional na Feegow. appointmentId=" + appointmentId
                                + ", erro=" + safeMessage(e),
                        resolveTraceId());
            } catch (RuntimeException e) {
                log.warn("Erro de integração ao buscar nome do médico na Feegow, usando fallback. erro={}", e.getMessage());
                auditPort.record(
                        "APPOINTMENT_MOTOR",
                        "CIRCUIT_BREAKER_FALLBACK",
                        "Fallback ao buscar nome do profissional na Feegow. appointmentId=" + appointmentId
                                + ", erro=" + safeMessage(e),
                        resolveTraceId());
            }
        }
        if (doctorName == null || doctorName.isBlank()) {
            doctorName = "Recepção Central";
        }
        return blipTextSanitizer.cleanDoctorName(doctorName);
    }

    private String resolveQueue(SessionDbData dbData) {
        String queue = dbData.doctorMapping() != null ? dbData.doctorMapping().getBlipQueueId() : null;
        if (queue == null || queue.isBlank() || "null".equalsIgnoreCase(queue.trim()) || queue.contains("\u200E")) {
            queue = "Recepção Central / Suporte";
            log.warn("[QUEUE WARNING] Fila não encontrada no banco para o médico {}, usando fallback: {}", dbData.session().getDoctorProfissionalId(), queue);
        }
        queue = blipContextService.cleanQueueName(queue);
        return queue.isBlank() ? "Recepção Central / Suporte" : queue;
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("trace_id");
        }
        return traceId;
    }

    private String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) {
            return "-";
        }
        return ex.getMessage();
    }

    public record BlipWebhookPayload(
        String messageId,
        String appointmentId,
        String action,
        String from,
        String token,
        Object content,
        Map<String, Object> metadata,
        String bsuid,
        String type
    ) {
        public BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token, Object content, Map<String, Object> metadata) {
            this(messageId, appointmentId, action, from, token, content, metadata, null, null);
        }
        public BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token, Object content, Map<String, Object> metadata, String bsuid) {
            this(messageId, appointmentId, action, from, token, content, metadata, bsuid, null);
        }
    }

    public record WebhookResult(String queue, String patientName, String patientCPF, String patientBirthdate, String action, String doctorName) {
    }

    public record BlipDeliveryFailureCommand(
        String messageId,
        String appointmentId,
        Integer errorCode,
        String errorMessage,
        String traceId
    ) {}
}
