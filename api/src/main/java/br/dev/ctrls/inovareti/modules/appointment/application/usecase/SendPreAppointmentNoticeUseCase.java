package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentTemplateDataBuilder;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso dedicado para envio do Lembrete Ativo de Proximidade (1 hora antes da consulta).
 * Utiliza o template ativo do WhatsApp 'lembrete_ativo_itsm_v2' para garantir a entrega
 * mesmo fora da janela de 24h, aplicando antecedência de horário e idempotência no banco de dados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendPreAppointmentNoticeUseCase {

    private static final String DEFAULT_TEMPLATE_NAME = "lembrete_ativo_itsm_v2";

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentTemplateDataBuilder appointmentTemplateDataBuilder;
    private final BlipNotificationService blipNotificationService;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final br.dev.ctrls.inovareti.modules.access.domain.port.output.BlipContactClientPort blipContactClientPort;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService blipContextService;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort appointmentExternalPort;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort patientExternalPort;
    private final br.dev.ctrls.inovareti.modules.appointment.application.service.SendAppointmentReminderUseCase sendAppointmentReminderUseCase;
    private final br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentFilterService appointmentFilterService;

    public void execute() {
        if (!appointmentMotorProperties.isEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        // Janela de 2h a 1h antes da consulta (de 60 a 120 minutos no futuro)
        LocalDateTime windowStart = now.plusMinutes(60);
        LocalDateTime windowEnd = now.plusMinutes(120);

        List<AppointmentSession> candidateSessions = appointmentSessionRepository.findConfirmedSessionsInWindow(windowStart, windowEnd);

        if (candidateSessions == null || candidateSessions.isEmpty()) {
            return;
        }

        String templateName = (appointmentMotorProperties.getBlipTemplatePreNotice() != null && !appointmentMotorProperties.getBlipTemplatePreNotice().isBlank())
                ? appointmentMotorProperties.getBlipTemplatePreNotice().trim()
                : DEFAULT_TEMPLATE_NAME;

        log.info("[LEMBRETE-ANTECEDENCIA] Encontradas {} consulta(s) confirmada(s) elegíveis para o template '{}' (2h a 1h antes).",
                candidateSessions.size(), templateName);

        // Busca a lista de bloqueios da Feegow para o dia da consulta em uma única chamada (com cache)
        List<br.dev.ctrls.inovareti.modules.appointment.application.dto.FeegowLockDto> activeLocks = 
                appointmentExternalPort.listLocks(now.toLocalDate(), now.toLocalDate(), null);

        String eligibleIdsProp = appointmentMotorProperties.getEligibleProcedureIds();
        List<String> eligibleProcedureIds = (eligibleIdsProp != null && !eligibleIdsProp.isBlank())
                ? java.util.Arrays.stream(eligibleIdsProp.split(",")).map(id -> id.trim()).filter(s -> !s.isEmpty()).toList()
                : java.util.Collections.emptyList();

        for (AppointmentSession session : candidateSessions) {
            try {
                if (session.getPhoneNumber() == null || session.getPhoneNumber().isBlank()) {
                    continue;
                }

                // VALIDAÇÃO DE ATENDIMENTO HUMANO NO DESK
                if (blipContextService.hasActiveTicket(session.getPhoneNumber(), session.getLastNotificationSentAt())) {
                    log.info("[ATTENDANCE-GUARD] Contato {} possui ticket aberto no Desk. Ignorando envio de lembrete de proximidade/antecedência para não poluir a conversa da secretária.", session.getPhoneNumber());
                    continue;
                }

                // VALIDAÇÃO DE AGENDA BLOQUEADA NO FEEGOW (/lock/list)
                if (appointmentFilterService.isScheduleBlocked(session, activeLocks)) {
                    continue;
                }

                // RE-VALIDAÇÃO OBRIGATÓRIA PRÉ-DISPARO DE LEMBRETE NA FEEGOW
                if (!sendAppointmentReminderUseCase.validateAndRecheckAppointmentOnFeegow(session)) {
                    continue;
                }

                // Filtros de segurança: verifica se o agendamento no Feegow é encaixe ou procedimento inelegível (ex: tarefa, cirurgia)
                if (session.getFeegowAppointmentId() != null && !session.getFeegowAppointmentId().isBlank()) {
                    try {
                        var feegowAppt = appointmentExternalPort.findById(session.getFeegowAppointmentId().trim());
                        if (feegowAppt != null) {
                            if (feegowAppt.encaixe() != null && feegowAppt.encaixe()) {
                                log.info("[LEMBRETE-ANTECEDENCIA] Abortando envio para sessão ID={} (Feegow ID={}) pois é um ENCAIXE.", session.getId(), session.getFeegowAppointmentId());
                                continue;
                            }
                            if (feegowAppt.procedureId() != null && !eligibleProcedureIds.isEmpty() && !eligibleProcedureIds.contains(feegowAppt.procedureId().trim())) {
                                log.info("[LEMBRETE-ANTECEDENCIA] Abortando envio para sessão ID={} (Feegow ID={}) pois o procedimento '{}' ({}) não é elegível para lembrete.",
                                        session.getId(), session.getFeegowAppointmentId(), feegowAppt.procedureId(), feegowAppt.procedureName());
                                continue;
                            }
                        }
                    } catch (Exception fEx) {
                        log.warn("[LEMBRETE-ANTECEDENCIA] Falha ao consultar Feegow para verificar elegibilidade do agendamento ID={}: {}", session.getFeegowAppointmentId(), fEx.getMessage());
                    }
                }

                // Reconstrói dados do template aplicando regras de nome, médico e offset de horário (-10 min)
                var templateData = appointmentTemplateDataBuilder.build(session);

                String resolvedQueue = "Recepção Central / Suporte";
                String blipQueueId = null;
                if (session.getDoctorProfissionalId() != null && !session.getDoctorProfissionalId().isBlank()) {
                    var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(session.getDoctorProfissionalId().trim());
                    if (mappingOpt.isPresent()) {
                        String queueId = mappingOpt.get().getBlipQueueId();
                        if (queueId != null && !queueId.isBlank() && !"null".equalsIgnoreCase(queueId.trim())) {
                            blipQueueId = queueId.trim();
                            resolvedQueue = blipContextService.resolveQueueName(blipQueueId);
                        }
                    }
                }

                // Remove qualquer redirecionamento prévio de fila do Desk para garantir fluxo de auto-encerramento limpo
                blipContextService.clearQueueRedirect(session.getPhoneNumber());

                String cpf = "";
                if (session.getPatientId() != null && !session.getPatientId().isBlank()) {
                    try {
                        var patient = patientExternalPort.patientInfo(session.getPatientId());
                        if (patient != null && patient.cpf() != null) {
                            cpf = patient.cpf().replaceAll("\\D", "");
                        }
                    } catch (Exception fEx) {
                        log.warn("[LEMBRETE-ANTECEDENCIA] Falha ao consultar CPF para o paciente ID {}: {}", session.getPatientId(), fEx.getMessage());
                    }
                }

                // Sincroniza o contato no Blip com os dados do paciente
                blipContactClientPort.syncContact(session.getPhoneNumber(), templateData.patientName(), cpf, resolvedQueue, session.getDoctorProfissionalId());

                // Injeta variáveis preventivas no contexto do paciente em escopo duplo (definindo flow_action = reminder_notice)
                blipContextService.setUserContext(session.getPhoneNumber(), "flow_action", "reminder_notice");
                blipContextService.setUserContext(session.getPhoneNumber(), "attendanceQueueNameToRedirect", resolvedQueue);
                blipContextService.setUserContext(session.getPhoneNumber(), "fila", resolvedQueue);
                blipContextService.setUserContext(session.getPhoneNumber(), "deskFila", resolvedQueue);
                blipContextService.setUserContext(session.getPhoneNumber(), "Medico", templateData.doctorName());
                blipContextService.setUserContext(session.getPhoneNumber(), "idAgendamentoFeegow", session.getFeegowAppointmentId() != null ? session.getFeegowAppointmentId() : "");
                blipContextService.setUserContext(session.getPhoneNumber(), "appointmentId", session.getId() != null ? session.getId().toString() : "");
                blipContextService.setUserContext(session.getPhoneNumber(), "name", templateData.patientName());
                blipContextService.setUserContext(session.getPhoneNumber(), "paciente", templateData.patientName());
                blipContextService.setUserContext(session.getPhoneNumber(), "Nome", templateData.patientName());

                // Força a atualização do Master-State do paciente no Blip para o bloco Preparar_Atendimento (stateId = a0776d9c-6486-42f3-8a4f-2706f0185908)
                String prepararAtendimentoBlockId = "a0776d9c-6486-42f3-8a4f-2706f0185908";
                try {
                    String cleanPhone = session.getPhoneNumber().replaceAll("\\D", "");
                    if (!cleanPhone.startsWith("55") && !cleanPhone.isBlank()) {
                        cleanPhone = "55" + cleanPhone;
                    }
                    String masterIdentity = cleanPhone + "@wa.gw.msging.net";
                    String tunnelIdentity = cleanPhone + ".fluxov1@tunnel.msging.net";
                    
                    blipContextService.setBuilderMasterState(masterIdentity, prepararAtendimentoBlockId);
                    blipContextService.setBuilderMasterState(tunnelIdentity, prepararAtendimentoBlockId);
                    blipContextService.setBuilderMasterState(session.getPhoneNumber(), prepararAtendimentoBlockId);
                    log.info("[LEMBRETE-ANTECEDENCIA] Master-State do Blip atualizado para Preparar_Atendimento ({}) no paciente {}", prepararAtendimentoBlockId, session.getPhoneNumber());
                } catch (Exception ex) {
                    log.warn("[LEMBRETE-ANTECEDENCIA] Falha ao atualizar Master-State no Blip para {}: {}", session.getPhoneNumber(), ex.getMessage());
                }

                log.info("[LEMBRETE-ANTECEDENCIA] Disparando template '{}' para paciente='{}', médico='{}', hora='{}', tel='{}', fila='{}'",
                        templateName, templateData.patientName(), templateData.doctorName(),
                        templateData.appointmentTime(), session.getPhoneNumber(), resolvedQueue);

                blipNotificationService.sendTemplateMessage(session.getPhoneNumber(), templateName, templateData);

                // Marca idempotência no banco para não reenviar no próximo ciclo
                session.setStatusDetails("PRE_NOTICE_SENT_" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
                session.setLastNotificationSentAt(now);
                appointmentSessionRepository.save(session);

            } catch (Exception ex) {
                log.error("[LEMBRETE-ANTECEDENCIA] Falha ao disparar template '{}' para sessão ID={}: {}",
                        templateName, session.getId(), ex.getMessage(), ex);
            }
        }
    }
}
