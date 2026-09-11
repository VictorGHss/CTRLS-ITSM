package br.dev.ctrls.itsm.modules.appointment.application.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import br.dev.ctrls.itsm.modules.appointment.application.dto.AppointmentPayload;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.model.BlipUserIdentityReconciliation;
import br.dev.ctrls.itsm.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.BlipProperties;
import br.dev.ctrls.itsm.modules.access.domain.service.AccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordenador responsável pelo fluxo de confirmação de múltiplos agendamentos em lote/grupo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlipGroupAppointmentConfirmationCoordinator {

    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipContextService blipContextService;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final BlipUserIdentityReconciliationRepositoryPort blipUserIdentityReconciliationRepository;
    private final BlipProperties blipProperties;
    private final PatientExternalPort patientExternalPort;
    private final AccessService accessService;
    private final DoctorEligibilityService doctorEligibilityService;

    public boolean isGroupAction(String action) {
        if (action == null || action.isBlank()) return false;
        String lower = action.trim().toLowerCase();
        return lower.startsWith("confirm_group_") || "confirm_group".equals(lower) || "confirmar tudo".equals(lower) || "confirmar_tudo".equals(lower);
    }

    public void processPrePersistenceGroupConfirmation(AppointmentSession session, String action, String fromIdentity) {
        if (!isGroupAction(action)) return;

        String groupIdStr = action.toLowerCase().startsWith("confirm_group_")
                ? action.substring("confirm_group_".length()).trim()
                : "";

        try {
            List<AppointmentSession> listaSessoes = resolveGroupSessions(session, groupIdStr);
            String userPhone = session.getPhoneNumber();
            log.info("[CONFIRM-BATCH] Processando confirmação em lote para o grupo: {} / telefone: {}. Total: {}", groupIdStr, userPhone, listaSessoes.size());

            // --- RESOLUÇÃO E CONFIGURAÇÃO IMEDIATA DA FILA DE REDIRECIONAMENTO ---
            String targetQueue = resolveTargetQueue(listaSessoes);
            blipContextService.setQueueRedirect(userPhone, targetQueue);
            if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                blipContextService.setQueueRedirect(fromIdentity, targetQueue);
            }

            // --- ATUALIZAÇÃO IMEDIATA DO STATUS LOCAL (FIM DO LEMBRETE FANTASMA) ---
            for (AppointmentSession groupSession : listaSessoes) {
                confirmationStateMachineService.markConfirmed(groupSession);
                try {
                    appointmentSessionRepository.save(groupSession);
                } catch (Exception ex) {
                    log.error("[CONFIRM-BATCH] Falha ao persistir status local CONFIRMED para sessionId={}", groupSession.getId(), ex);
                }
            }

            // --- CARIMBO PREVENTIVO DE CONTEXTO E MASTER STATE NO BLIP ---
            setupBlipContextAndMasterState(listaSessoes, userPhone, fromIdentity, targetQueue);

            // --- PUSH DE EXTRAS PREVENTIVO ---
            pushContactExtras(listaSessoes, userPhone, fromIdentity, targetQueue);

            log.info("[WEBHOOK-FLOW] Blip carimbado preventivamente antes da chamada síncrona do ERP Feegow.");

            // --- ATUALIZAÇÃO NO FEEGOW ---
            String confirmedStatusId = resolveConfirmedStatusId();
            for (AppointmentSession groupSession : listaSessoes) {
                if (!isDoctorAllowed(groupSession.getDoctorProfissionalId())) {
                    log.warn("[WEBHOOK-BYPASS] Agendamento ID {} pertence ao médico ID {} (não listado na automação). Ignorando Feegow.",
                            groupSession.getFeegowAppointmentId(), groupSession.getDoctorProfissionalId());
                    continue;
                }
                try {
                    log.info("Enviando confirmação para Feegow: {}", groupSession.getFeegowAppointmentId());
                    appointmentExternalPort.updateAppointmentStatus(groupSession.getFeegowAppointmentId(), confirmedStatusId);
                    log.info("Resposta do Feegow: SUCCESS");
                } catch (RestClientException | IllegalStateException ex) {
                    log.error("[CONFIRM-BATCH] Falha ao atualizar status na Feegow. appointmentId={}, erro={}",
                            groupSession.getFeegowAppointmentId(), ex.getMessage(), ex);
                }
            }
        } catch (Exception e) {
            log.error("[CONFIRM-BATCH] Erro no processamento em lote da Feegow para ação: {}", action, e);
        }
    }

    public void processApplySessionStateGroupConfirmation(AppointmentSession session, String action, String fromIdentity) {
        if (!isGroupAction(action)) return;

        String groupIdStr = action.toLowerCase().startsWith("confirm_group_")
                ? action.substring("confirm_group_".length()).trim()
                : "";

        try {
            List<AppointmentSession> listaSessoes = resolveGroupSessions(session, groupIdStr);
            String userPhone = session.getPhoneNumber();

            for (AppointmentSession groupSession : listaSessoes) {
                if (isDoctorAllowed(groupSession.getDoctorProfissionalId())) {
                    confirmationStateMachineService.markConfirmed(groupSession);
                    if (session.getId() == null || !groupSession.getId().equals(session.getId())) {
                        appointmentSessionRepository.save(groupSession);
                    }
                } else {
                    log.warn("[WEBHOOK-BYPASS] Agendamento ID {} pertence ao médico ID {} (não listado na automação). Ignorando confirmação automática.",
                            groupSession.getFeegowAppointmentId(), groupSession.getDoctorProfissionalId());
                }
            }
            log.info("[CONFIRM-BATCH] Sessões do grupo {} / telefone {} atualizadas para CONFIRMED no banco local. Total: {}", groupIdStr, userPhone, listaSessoes.size());

            // --- PRÉ-CREDENCIAMENTO FÍSICO NA GERACESSO PARA TODAS AS SESSÕES DO GRUPO ---
            Thread.ofVirtual().name("group-geracesso-pre-reg").start(() -> {
                for (AppointmentSession s : listaSessoes) {
                    if (s.getFeegowAppointmentId() != null && !s.getFeegowAppointmentId().isBlank()) {
                        try {
                            log.info("[CONFIRM-BATCH-CATRACA] Pré-registrando credencial GerAcesso para agendamento {} do grupo...", s.getFeegowAppointmentId());
                            accessService.processAccessRequest(s.getFeegowAppointmentId(), null, List.of());
                        } catch (Exception ex) {
                            log.warn("[CONFIRM-BATCH-CATRACA] Falha ao pré-registrar credencial GerAcesso para {}: {}", s.getFeegowAppointmentId(), ex.getMessage());
                        }
                    }
                }
            });

            try {
                blipContextService.setUserContextForUser(userPhone, "isConfirmingAgenda", "false");
                blipContextService.setUserContextForUser(userPhone, "hasActiveAppointment", "false");
                if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                    blipContextService.setUserContextForUser(fromIdentity, "isConfirmingAgenda", "false");
                    blipContextService.setUserContextForUser(fromIdentity, "hasActiveAppointment", "false");
                }
            } catch (Exception ctxEx) {
                log.debug("[CONFIRM-BATCH] Falha ao limpar variáveis de contexto pós-confirmação: {}", ctxEx.getMessage());
            }
        } catch (Exception e) {
            log.error("[CONFIRM-BATCH] Erro ao atualizar estados do grupo de sessões no banco local. grupo={}", groupIdStr, e);
        }
    }

    private List<AppointmentSession> resolveGroupSessions(AppointmentSession session, String groupIdStr) {
        Map<UUID, AppointmentSession> sessoesUnicas = new LinkedHashMap<>();
        if (!groupIdStr.isBlank()) {
            try {
                UUID groupId = UUID.fromString(groupIdStr);
                List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
                for (NotificationGroup group : groups) {
                    appointmentSessionRepository.findById(group.getSessionId()).ifPresent(s -> sessoesUnicas.put(s.getId(), s));
                }
            } catch (Exception ex) {
                log.warn("[CONFIRM-BATCH] Não foi possível fazer parse do groupId UUID '{}': {}", groupIdStr, ex.getMessage());
            }
        }

        String userPhone = session.getPhoneNumber();
        if (userPhone != null && !userPhone.isBlank()) {
            List<AppointmentSession> activeContactSessions = appointmentSessionRepository.findActiveByPhoneNumber(userPhone);
            for (AppointmentSession s : activeContactSessions) {
                sessoesUnicas.put(s.getId(), s);
            }
        }
        if (session.getId() != null) {
            sessoesUnicas.put(session.getId(), session);
        }
        List<AppointmentSession> result = new ArrayList<>(sessoesUnicas.values());
        result.sort((a, b) -> {
            if (a.getAppointmentAt() == null && b.getAppointmentAt() == null) return 0;
            if (a.getAppointmentAt() == null) return 1;
            if (b.getAppointmentAt() == null) return -1;
            return a.getAppointmentAt().compareTo(b.getAppointmentAt());
        });
        return result;
    }

    private String resolveTargetQueue(List<AppointmentSession> listaSessoes) {
        String targetQueue = null;
        for (AppointmentSession groupSession : listaSessoes) {
            var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(groupSession.getDoctorProfissionalId());
            if (mappingOpt.isPresent()) {
                String queue = mappingOpt.get().getBlipQueueId();
                if (queue != null && !queue.isBlank() && !"null".equalsIgnoreCase(queue.trim())) {
                    targetQueue = queue.trim();
                    break;
                }
            }
        }

        if (targetQueue == null || targetQueue.isBlank()) {
            return "Recepção Central / Suporte";
        }
        return blipContextService.resolveQueueName(targetQueue);
    }

    private void setupBlipContextAndMasterState(List<AppointmentSession> listaSessoes, String userPhone, String fromIdentity, String targetQueue) {
        if (listaSessoes.isEmpty()) return;

        AppointmentSession firstSession = listaSessoes.getFirst();
        String firstFeegowId = firstSession.getFeegowAppointmentId();
        String requiresCpfFallback = "false";

        try {
            String tokenAcesso = "";
            String accessUrl = "";
            try {
                tokenAcesso = accessService.generateAccessToken(firstFeegowId, userPhone);
                accessUrl = "https://itsm.ctrls.dev.br/" + firstFeegowId + "?t=" + tokenAcesso;
            } catch (Exception e) {
                log.warn("[CONFIRM-BATCH] Falha ao gerar Magic Token para agendamento {}: {}", firstFeegowId, e.getMessage());
            }

            blipContextService.setUserContextForUser(userPhone, "idAgendamentoFeegow", firstFeegowId);
            blipContextService.setUserContextForUser(userPhone, "appointmentId", firstFeegowId);
            blipContextService.setUserContextForUser(userPhone, "tokenAcesso", tokenAcesso);
            blipContextService.setUserContextForUser(userPhone, "urlAcesso", accessUrl);
            blipContextService.setUserContext(userPhone, "hasActiveAppointment", "true");
            blipContextService.setVariable(userPhone, "requiresCpfFallback", requiresCpfFallback);
            blipContextService.setContactExtra(userPhone, "requiresCpfFallback", requiresCpfFallback);
            blipContextService.setContactExtra(userPhone, "tokenAcesso", tokenAcesso);
            blipContextService.setContactExtra(userPhone, "urlAcesso", accessUrl);
            blipContextService.setVariable(userPhone, "hasActiveAppointment", "true");
            blipContextService.setContactExtra(userPhone, "hasActiveAppointment", "true");

            if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
                blipContextService.setUserContextForUser(fromIdentity, "idAgendamentoFeegow", firstFeegowId);
                blipContextService.setUserContextForUser(fromIdentity, "appointmentId", firstFeegowId);
                blipContextService.setUserContextForUser(fromIdentity, "tokenAcesso", tokenAcesso);
                blipContextService.setUserContextForUser(fromIdentity, "urlAcesso", accessUrl);
                blipContextService.setUserContext(fromIdentity, "hasActiveAppointment", "true");
                blipContextService.setVariable(fromIdentity, "requiresCpfFallback", requiresCpfFallback);
                blipContextService.setContactExtra(fromIdentity, "requiresCpfFallback", requiresCpfFallback);
                blipContextService.setContactExtra(fromIdentity, "tokenAcesso", tokenAcesso);
                blipContextService.setContactExtra(fromIdentity, "urlAcesso", accessUrl);
                blipContextService.setVariable(fromIdentity, "hasActiveAppointment", "true");
                blipContextService.setContactExtra(fromIdentity, "hasActiveAppointment", "true");
            }
            log.info("[CONFIRM-BATCH] Contexto e Magic Token salvos no Blip para o primeiro agendamento: {}", firstFeegowId);
        } catch (Exception ex) {
            log.warn("[CONFIRM-BATCH] Falha ao salvar ID ou verificar CPF no contexto: {}", ex.getMessage());
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

        blipContextService.setMasterState(userPhone, targetBot, confirmSuccessBlockId);
        if (fromIdentity != null && !fromIdentity.isBlank() && !fromIdentity.equalsIgnoreCase(userPhone)) {
            blipContextService.setMasterState(fromIdentity, targetBot, confirmSuccessBlockId);
        }

        // Atualização em identidades de túnel
        syncTunnelIdentities(listaSessoes, userPhone, fromIdentity, targetQueue, targetBot, confirmSuccessBlockId, requiresCpfFallback, firstFeegowId);
    }

    private void syncTunnelIdentities(List<AppointmentSession> listaSessoes, String userPhone, String fromIdentity,
                                      String targetQueue, String targetBot, String confirmSuccessBlockId,
                                      String requiresCpfFallback, String firstFeegowId) {
        try {
            for (AppointmentSession groupSession : listaSessoes) {
                String guid = groupSession.getBlipGuid() != null && !groupSession.getBlipGuid().isBlank()
                        ? groupSession.getBlipGuid()
                        : groupSession.getBsuid();

                if (guid != null && !guid.isBlank()) {
                    if (guid.contains("@")) {
                        guid = guid.substring(0, guid.indexOf("@"));
                    }
                    guid = guid.trim();
                    if (guid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                        String tunnelIdentity = guid + "@tunnel.msging.net";
                        blipContextService.setVariable(tunnelIdentity, "requiresCpfFallback", requiresCpfFallback);
                        blipContextService.setContactExtra(tunnelIdentity, "requiresCpfFallback", requiresCpfFallback);
                        blipContextService.setMasterState(tunnelIdentity, targetBot, confirmSuccessBlockId);
                    }
                }
            }

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
                        blipContextService.setBuilderMasterState(tunnelId, confirmSuccessBlockId);
                        try {
                            String tokenAcesso = "";
                            String accessUrl = "";
                            try {
                                tokenAcesso = accessService.generateAccessToken(firstFeegowId, userPhone);
                                accessUrl = "https://itsm.ctrls.dev.br/" + firstFeegowId + "?t=" + tokenAcesso;
                            } catch (Exception ignored) {}

                            blipContextService.setUserContextForUser(tunnelId, "idAgendamentoFeegow", firstFeegowId);
                            blipContextService.setUserContextForUser(tunnelId, "appointmentId", firstFeegowId);
                            blipContextService.setUserContextForUser(tunnelId, "tokenAcesso", tokenAcesso);
                            blipContextService.setUserContextForUser(tunnelId, "urlAcesso", accessUrl);
                            blipContextService.setContactExtra(tunnelId, "tokenAcesso", tokenAcesso);
                            blipContextService.setContactExtra(tunnelId, "urlAcesso", accessUrl);
                        } catch (Exception ex) {
                            log.warn("[CONFIRM-BATCH] Falha ao salvar token/id no túnel: {}", ex.getMessage());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[CONFIRM-BATCH] Falha ao sincronizar identidades de túnel: {}", ex.getMessage());
        }
    }

    private void pushContactExtras(List<AppointmentSession> listaSessoes, String userPhone, String fromIdentity, String targetQueue) {
        if (listaSessoes.isEmpty()) return;
        try {
            AppointmentSession firstSession = listaSessoes.getFirst();
            FeegowPatient patient = patientExternalPort.patientInfo(firstSession.getPatientId());
            String patientName = (patient.name() == null || patient.name().isBlank()) ? "Paciente" : patient.name();
            String formattedBirthdate = formatBirthdate(patient.birthdate());

            String resolvedDoctorName = "Recepção Central";
            var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(firstSession.getDoctorProfissionalId());
            if (mappingOpt.isPresent()) {
                String mappingName = mappingOpt.get().getProfissionalNome();
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
            log.info("[CONFIRM-BATCH] Push de extras de contato enviado preventivamente para a Blip.");
        } catch (Exception ex) {
            log.warn("[CONFIRM-BATCH] Falha ao enviar processAppointmentPush preventivo: {}", ex.getMessage());
        }
    }

    public boolean isDoctorAllowed(String doctorId) {
        if (doctorEligibilityService != null) {
            return doctorEligibilityService.isDoctorAllowed(doctorId);
        }
        return false;
    }

    public String formatBirthdate(String birthdate) {
        if (birthdate == null || birthdate.isBlank()) {
            return "";
        }
        String clean = birthdate.trim();
        if (clean.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return clean;
        }
        try {
            LocalDate date;
            if (clean.contains("-")) {
                if (clean.indexOf('-') == 4) { // AAAA-MM-DD
                    date = LocalDate.parse(clean);
                } else { // DD-MM-AAAA
                    date = LocalDate.parse(clean, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                }
                return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
            log.warn("[FORMAT] Falha ao formatar data de nascimento: {}", birthdate);
        }
        return clean;
    }

    public String resolveConfirmedStatusId() {
        String configuredStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
        if (configuredStatusId == null || configuredStatusId.isBlank()) {
            return "7";
        }
        String trimmed = configuredStatusId.trim();
        if ("2".equals(trimmed)) {
            return "7";
        }
        return trimmed;
    }
}
