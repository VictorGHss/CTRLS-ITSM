package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentPayload;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estratégia de processamento específica para a ação de confirmação de consulta ("confirm").
 * Delega o processamento em lote/grupo para BlipGroupAppointmentConfirmationCoordinator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class ConfirmBlipWebhookActionHandler implements BlipWebhookActionHandler {

    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipContextService blipContextService;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final BlipUserIdentityReconciliationRepositoryPort blipUserIdentityReconciliationRepository;
    private final BlipProperties blipProperties;
    private final PatientExternalPort patientExternalPort;
    private final BlipGroupAppointmentConfirmationCoordinator groupCoordinator;

    @Override
    public boolean supports(String actionType) {
        if (actionType == null || actionType.isBlank()) return false;
        String lower = actionType.trim().toLowerCase();
        return "confirm".equals(lower) || groupCoordinator.isGroupAction(actionType);
    }

    @Override
    public void prePersistence(AppointmentSession session, String action, String fromIdentity) {
        if (groupCoordinator.isGroupAction(action)) {
            groupCoordinator.processPrePersistenceGroupConfirmation(session, action, fromIdentity);
        }
    }

    @Override
    public void applySessionState(AppointmentSession session, String action, String fromIdentity) {
        if (groupCoordinator.isGroupAction(action)) {
            groupCoordinator.processApplySessionStateGroupConfirmation(session, action, fromIdentity);
            return;
        }

        if (session == null) {
            log.warn("[CONFIRM] Ação de confirmação ignorada pois a sessão fornecida é nula. De: {}", fromIdentity);
            return;
        }

        // --- VALIDAÇÃO DE ORIGEM DE PAYLOAD (confirm_{id} vs "Confirmar Presença") ---
        boolean isEmbeddedAction = action != null && (action.startsWith("confirm_") || action.contains("_"));
        boolean isGenericAction = "confirm".equalsIgnoreCase(action) || "confirmar presença".equalsIgnoreCase(action) || "confirmar presenca".equalsIgnoreCase(action);

        if (isGenericAction && !isEmbeddedAction) {
            boolean isRecentSession = session.getLastInteractionAt() != null
                    && session.getLastInteractionAt().isAfter(LocalDateTime.now().minusHours(48));

            if (!isRecentSession) {
                log.warn("[WEBHOOK-BLOQUEIO-ORIGEM] Ação de confirmação genérica ('{}') ignorada para o agendamento ID {} (tel={}) pois não é de sessão ativa recente (48h).",
                        action, session.getFeegowAppointmentId(), fromIdentity);
                return;
            }
        }

        // --- TRAVA DE SEGURANÇA MÉRITO/ELEGIBILIDADE DINÂMICA VIA ENV/BANCO ---
        if (!groupCoordinator.isDoctorAllowed(session.getDoctorProfissionalId())) {
            log.warn("[WEBHOOK-BYPASS] Agendamento ID {} pertence ao médico ID {} (não listado na ENV de automação). Ignorando confirmação automática e liberando fluxo para atendimento manual/Blip.",
                    session.getFeegowAppointmentId(), session.getDoctorProfissionalId());
            return;
        }

        // --- RESOLUÇÃO E CONFIGURAÇÃO IMEDIATA DA FILA DE REDIRECIONAMENTO (ANTI-CORRIDA) ---
        String targetQueue = null;
        var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(session.getDoctorProfissionalId());
        if (mappingOpt.isPresent()) {
            String queue = mappingOpt.get().getBlipQueueId();
            if (queue != null && !queue.isBlank() && !"null".equalsIgnoreCase(queue.trim())) {
                targetQueue = queue.trim();
            }
        }
        if (targetQueue == null || targetQueue.isBlank()) {
            targetQueue = "Recepção Central / Suporte";
        } else {
            targetQueue = blipContextService.resolveQueueName(targetQueue);
        }

        String userPhone = session.getPhoneNumber();
        blipContextService.setQueueRedirect(userPhone, targetQueue);
        if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
            blipContextService.setQueueRedirect(fromIdentity, targetQueue);
        }

        final String requiresCpfFallback = "false";

        // Salva o ID do agendamento, CPF e telefone no contexto do Blip para persistência
        try {
            String rawPhoneDigits = userPhone != null ? userPhone.replaceAll("\\D", "") : "";
            String plainPhone = (rawPhoneDigits.startsWith("55") && rawPhoneDigits.length() > 11) ? rawPhoneDigits.substring(2) : rawPhoneDigits;

            blipContextService.setUserContextForUser(userPhone, "idAgendamentoFeegow", session.getFeegowAppointmentId());
            blipContextService.setUserContextForUser(userPhone, "appointmentId", session.getFeegowAppointmentId());
            blipContextService.setUserContextForUser(userPhone, "contact.phoneNumber", plainPhone);
            blipContextService.setUserContextForUser(userPhone, "phoneNumber", plainPhone);
            blipContextService.setVariable(userPhone, "requiresCpfFallback", requiresCpfFallback);
            blipContextService.setContactExtra(userPhone, "requiresCpfFallback", requiresCpfFallback);
            blipContextService.setContactExtra(userPhone, "phoneNumber", plainPhone);
            blipContextService.setContactExtra(userPhone, "telefone", plainPhone);

            if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                blipContextService.setUserContextForUser(fromIdentity, "idAgendamentoFeegow", session.getFeegowAppointmentId());
                blipContextService.setUserContextForUser(fromIdentity, "appointmentId", session.getFeegowAppointmentId());
                blipContextService.setUserContextForUser(fromIdentity, "contact.phoneNumber", plainPhone);
                blipContextService.setUserContextForUser(fromIdentity, "phoneNumber", plainPhone);
                blipContextService.setVariable(fromIdentity, "requiresCpfFallback", requiresCpfFallback);
                blipContextService.setContactExtra(fromIdentity, "requiresCpfFallback", requiresCpfFallback);
                blipContextService.setContactExtra(fromIdentity, "phoneNumber", plainPhone);
                blipContextService.setContactExtra(fromIdentity, "telefone", plainPhone);
            }
            log.info("[CONFIRM] ID do agendamento ({}), contact.phoneNumber ({}) e contexto salvos no Blip com sucesso.", session.getFeegowAppointmentId(), plainPhone);
        } catch (Exception ex) {
            log.warn("[CONFIRM] Falha ao salvar ID do agendamento ou telefone no contexto: {}", ex.getMessage());
        }

        String confirmSuccessBlockId = blipProperties.getBlocks().getConfirmSuccess();
        if (confirmSuccessBlockId == null || confirmSuccessBlockId.isBlank()) {
            confirmSuccessBlockId = "b3461299-9500-46b1-b423-12ffef3e1aba";
        }

        String targetBot = "desk@msging.net";
        if (!"644d54dd-aefd-478b-93eb-10081acdd387".equals(confirmSuccessBlockId)) {
            String builderBotId = appointmentMotorProperties.getBlipBuilderBotId();
            if (builderBotId != null && !builderBotId.isBlank()) {
                targetBot = builderBotId;
            }
        }

        // Dispara setMasterState IMEDIATAMENTE
        blipContextService.setMasterState(userPhone, targetBot, confirmSuccessBlockId);
        if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
            blipContextService.setMasterState(fromIdentity, targetBot, confirmSuccessBlockId);
        }

        // Reconcilia e atualiza também o Master-State com a identidade baseada no GUID do túnel
        syncSingleSessionTunnel(session, requiresCpfFallback);

        if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
            blipContextService.setMasterState(fromIdentity, targetBot, confirmSuccessBlockId);
        }

        // Redirecionamento de estado nas identidades de túnel (deterministica e reconciliadas)
        syncReconciledTunnels(userPhone, fromIdentity, targetQueue, targetBot, confirmSuccessBlockId, requiresCpfFallback, session.getFeegowAppointmentId());

        log.info("[CONFIRM] Redirecionamento de estado enviado para a Blip para o usuário {} (identidade webhook: {}). Fila: '{}', Bloco: '{}:{}'",
                userPhone, fromIdentity, targetQueue, targetBot, confirmSuccessBlockId);

        // Push de extras preventivo
        pushContactExtras(session, userPhone, fromIdentity, targetQueue);

        log.info("[WEBHOOK-FLOW] Blip carimbado preventivamente antes da chamada síncrona do ERP Feegow.");

        // Chamada Feegow pós-carimbo do Blip
        String confirmedStatusId = groupCoordinator.resolveConfirmedStatusId();
        log.info("Enviando confirmação para Feegow: {}", session.getFeegowAppointmentId());
        try {
            appointmentExternalPort.updateAppointmentStatus(session.getFeegowAppointmentId(), confirmedStatusId);
            log.info("Resposta do Feegow: SUCCESS");
        } catch (Exception ex) {
            log.error("Resposta do Feegow: ERROR");
            log.error("[CONFIRM] Falha ao atualizar status na Feegow para ID: {}. Detalhes: {}", session.getFeegowAppointmentId(), ex.getMessage());
            throw new RuntimeException("Falha na atualização do Feegow para o agendamento " + session.getFeegowAppointmentId() + ". Cancelando confirmação local.", ex);
        }
        confirmationStateMachineService.markConfirmed(session);

        // Libera o estado do paciente no Blip
        try {
            blipContextService.setUserContextForUser(userPhone, "isConfirmingAgenda", "false");
            blipContextService.setUserContextForUser(userPhone, "hasActiveAppointment", "false");
            if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                blipContextService.setUserContextForUser(fromIdentity, "isConfirmingAgenda", "false");
                blipContextService.setUserContextForUser(fromIdentity, "hasActiveAppointment", "false");
            }
        } catch (Exception ctxEx) {
            log.debug("[CONFIRM] Falha ao limpar variáveis de contexto pós-confirmação: {}", ctxEx.getMessage());
        }
    }

    private void syncSingleSessionTunnel(AppointmentSession session, String requiresCpfFallback) {
        try {
            String guid = null;
            var dbSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(session.getFeegowAppointmentId());
            if (dbSessionOpt.isPresent()) {
                guid = dbSessionOpt.get().getBlipGuid();
                if (guid == null || guid.isBlank()) {
                    guid = dbSessionOpt.get().getBsuid();
                }
            }
            if (guid == null || guid.isBlank()) {
                guid = session.getBlipGuid();
            }
            if (guid == null || guid.isBlank()) {
                guid = session.getBsuid();
            }

            if (guid != null && !guid.isBlank()) {
                if (guid.contains("@")) {
                    guid = guid.substring(0, guid.indexOf("@"));
                }
                guid = guid.trim();
                if (guid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                    String tunnelIdentity = guid + "@tunnel.msging.net";
                    blipContextService.setVariable(tunnelIdentity, "requiresCpfFallback", requiresCpfFallback);
                    blipContextService.setContactExtra(tunnelIdentity, "requiresCpfFallback", requiresCpfFallback);
                    log.info("[CONFIRM] requiresCpfFallback atualizado também para a identidade GUID do túnel: {}", tunnelIdentity);
                }
            }
        } catch (Exception ex) {
            log.warn("[CONFIRM] Falha ao atualizar requiresCpfFallback para GUID do túnel: {}", ex.getMessage());
        }
    }

    private void syncReconciledTunnels(String userPhone, String fromIdentity, String targetQueue, String targetBot,
                                       String confirmSuccessBlockId, String requiresCpfFallback, String feegowAppointmentId) {
        try {
            if (userPhone != null && !userPhone.isBlank()) {
                List<String> tunnelIdentities = new ArrayList<>();
                String subbotId = blipProperties.getSubbotId();
                String subbotLocalPart = null;
                if (subbotId != null && !subbotId.isBlank() && !subbotId.toLowerCase().contains("fluxov1")) {
                    subbotLocalPart = subbotId.trim();
                    if (subbotLocalPart.contains("@")) {
                        subbotLocalPart = subbotLocalPart.substring(0, subbotLocalPart.indexOf('@'));
                    }
                }

                String phoneDigits = userPhone.trim();
                if (phoneDigits.contains("@")) {
                    phoneDigits = phoneDigits.substring(0, phoneDigits.indexOf('@'));
                }
                phoneDigits = phoneDigits.replaceAll("\\D", "");
                if (!phoneDigits.startsWith("55") && !phoneDigits.isEmpty()) {
                    phoneDigits = "55" + phoneDigits;
                }

                if (subbotLocalPart != null) {
                    tunnelIdentities.add(phoneDigits + "." + subbotLocalPart + "@tunnel.msging.net");
                }

                List<BlipUserIdentityReconciliation> reconciliations = new ArrayList<>();
                reconciliations.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(userPhone.trim()));
                String altPhone = userPhone.trim().startsWith("55") ? userPhone.trim().substring(2) : "55" + userPhone.trim();
                reconciliations.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(altPhone));

                for (var rec : reconciliations) {
                    if (rec.getBlipGuid() != null && !rec.getBlipGuid().isBlank()) {
                        String tunnelId = rec.getBlipGuid().trim() + "@tunnel.msging.net";
                        if (!tunnelIdentities.contains(tunnelId) && !tunnelId.toLowerCase().contains("fluxov1")) {
                            tunnelIdentities.add(tunnelId);
                        }
                    }
                }

                for (String tunnelId : tunnelIdentities) {
                    if (!tunnelId.equalsIgnoreCase(userPhone) && !tunnelId.equalsIgnoreCase(fromIdentity)) {
                        blipContextService.setQueueRedirect(tunnelId, targetQueue);
                        try {
                            blipContextService.setUserContextForUser(tunnelId, "idAgendamentoFeegow", feegowAppointmentId);
                            blipContextService.setUserContextForUser(tunnelId, "appointmentId", feegowAppointmentId);
                            blipContextService.setVariable(tunnelId, "requiresCpfFallback", requiresCpfFallback);
                            blipContextService.setContactExtra(tunnelId, "requiresCpfFallback", requiresCpfFallback);
                            blipContextService.setMasterState(tunnelId, targetBot, confirmSuccessBlockId);
                        } catch (Exception ex) {
                            log.warn("[CONFIRM] Falha ao salvar ID ou redirecionar no túnel: {}", ex.getMessage());
                        }
                        log.info("[CONFIRM] Salvo ID, CPF e redirecionado Master-State no túnel: {}", tunnelId);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[CONFIRM] Falha ao aplicar redirecionamento retroativo em túneis: {}", ex.getMessage());
        }
    }

    private void pushContactExtras(AppointmentSession session, String userPhone, String fromIdentity, String targetQueue) {
        try {
            FeegowPatient patient = patientExternalPort.patientInfo(session.getPatientId());
            String patientName = (patient.name() == null || patient.name().isBlank()) ? "Paciente" : patient.name();
            String formattedBirthdate = groupCoordinator.formatBirthdate(patient.birthdate());

            String resolvedDoctorName = "Clínica Inovare";
            var pushMappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(session.getDoctorProfissionalId());
            if (pushMappingOpt.isPresent()) {
                String mappingName = pushMappingOpt.get().getProfissionalNome();
                if (mappingName != null && !mappingName.isBlank() && !"null".equalsIgnoreCase(mappingName.trim())) {
                    resolvedDoctorName = mappingName.trim();
                }
            }

            AppointmentPayload appointmentPayload = AppointmentPayload.builder()
                    .action("confirm")
                    .doctorName(resolvedDoctorName)
                    .queue(targetQueue)
                    .patientName(patientName)
                    .patientCPF(patient.cpf())
                    .patientBirthdate(formattedBirthdate)
                    .build();

            blipContextService.processAppointmentPush(fromIdentity != null ? fromIdentity : userPhone, "confirm", appointmentPayload);
            log.info("[CONFIRM] Push de extras de contato enviado preventivamente para a Blip.");
        } catch (Exception ex) {
            log.warn("[CONFIRM] Falha ao enviar processAppointmentPush preventivo: {}", ex.getMessage());
        }
    }
}
