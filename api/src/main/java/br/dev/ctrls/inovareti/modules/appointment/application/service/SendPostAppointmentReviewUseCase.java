package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso responsável pelo envio da mensagem de avaliação do Google Review
 * para pacientes cujas consultas estejam com status 3 (Atendido) no Feegow ERP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendPostAppointmentReviewUseCase {

    private static final String TEMPLATE_REVIEW_GOOGLE = "pesquisa_avaliacao_google_itsm_v6";
    private static final int FEEGOW_STATUS_ATENDIDO = 3;
    public static final String STATE_REVIEW_FINISHED = BlipContextService.STATE_REVIEW_FINISHED;

    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final PatientExternalPort patientExternalPort;
    private final BlipNotificationService blipNotificationService;
    private final BlipContextService blipContextService;

    @org.springframework.beans.factory.annotation.Value("${app.appointment.motor.active-doctor-ids:}")
    private String activeDoctorIds;

    @org.springframework.beans.factory.annotation.Value("${app.appointment.motor.test-doctor-ids:}")
    private String testDoctorIds;

    private java.util.Set<Long> getAllowedDoctorIds() {
        java.util.Set<Long> set = new java.util.HashSet<>();
        String combined = (activeDoctorIds != null ? activeDoctorIds : "") + "," + (testDoctorIds != null ? testDoctorIds : "");
        for (String s : combined.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                try {
                    set.add(Long.parseLong(trimmed));
                } catch (NumberFormatException ignored) {}
            }
        }
        return set;
    }

    @Transactional
    public int execute() {
        LocalDate today = LocalDate.now();
        log.info("[GOOGLE-REVIEW] Iniciando verificação de consultas atendidas (StatusID=3) na Feegow para a data {}", today);

        List<FeegowAppointment> attendedAppointments;
        try {
            attendedAppointments = appointmentExternalPort.searchAppointments(today, FEEGOW_STATUS_ATENDIDO);
        } catch (Exception ex) {
            log.error("[GOOGLE-REVIEW] Falha ao consultar agendamentos atendidos no Feegow: {}", ex.getMessage(), ex);
            return 0;
        }

        if (attendedAppointments == null || attendedAppointments.isEmpty()) {
            log.info("[GOOGLE-REVIEW] Nenhum agendamento com status 'Atendido' (StatusID=3) encontrado para a data {}.", today);
            return 0;
        }

        log.info("[GOOGLE-REVIEW] Encontrados {} agendamentos com status 'Atendido' (StatusID=3) para a data {}.", attendedAppointments.size(), today);

        int countSent = 0;
        java.util.Set<Long> allowedDoctorIds = getAllowedDoctorIds();

        for (FeegowAppointment appt : attendedAppointments) {
            if (appt == null || appt.id() == null || appt.id().isBlank()) {
                continue;
            }

            // Trava Estrita 1: StatusID deve ser estritamente 3 (Atendido)
            String statusId = appt.statusId() != null ? appt.statusId().trim() : "";
            if (!"3".equals(statusId)) {
                log.info("[GOOGLE-REVIEW] Agendamento ID={} (Paciente ID={}) ignorado pois statusId='{}' (esperado: 3 - Atendido)",
                        appt.id(), appt.patientId(), statusId);
                continue;
            }

            // Trava Estrita 2: Data da consulta deve ser exatamente HOJE
            if (appt.startAt() != null && !today.equals(appt.startAt().toLocalDate())) {
                log.info("[GOOGLE-REVIEW] Agendamento ID={} ignorado pois a data {} não é a data de hoje ({})",
                        appt.id(), appt.startAt().toLocalDate(), today);
                continue;
            }

            // Trava Estrita 3: Horário da consulta NÃO pode estar no futuro (consulta ainda não concluída)
            if (appt.startAt() != null && appt.startAt().isAfter(LocalDateTime.now())) {
                log.info("[GOOGLE-REVIEW] Agendamento ID={} ignorado pois o horário da consulta ({}) ainda não ocorreu.",
                        appt.id(), appt.startAt());
                continue;
            }

            String doctorIdStr = (appt.doctorId() != null && !appt.doctorId().isBlank()) ? appt.doctorId().trim() : null;
            Long doctorId = null;
            if (doctorIdStr != null) {
                try {
                    doctorId = Long.parseLong(doctorIdStr);
                } catch (NumberFormatException ignored) {}
            }

            if (!allowedDoctorIds.isEmpty() && (doctorId == null || !allowedDoctorIds.contains(doctorId))) {
                log.info("[GOOGLE-REVIEW] Médico ID={} não está habilitado na lista de médicos ativos (.env). Disparo ignorado.",
                        doctorIdStr != null ? doctorIdStr : "desconhecido");
                continue;
            }

            String feegowAppointmentId = appt.id().trim();
            if (feegowAppointmentId.contains(".")) {
                feegowAppointmentId = feegowAppointmentId.substring(0, feegowAppointmentId.indexOf('.'));
            }

            try {
                Optional<AppointmentSession> sessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId);
                AppointmentSession session;

                if (sessionOpt.isPresent()) {
                    session = sessionOpt.get();
                    if (session.getReviewRequestedAt() != null) {
                        log.info("[GOOGLE-REVIEW] Agendamento ID={} já recebeu pesquisa de avaliação anteriormente em {}. Ignorado.",
                                feegowAppointmentId, session.getReviewRequestedAt());
                        continue;
                    }
                } else {
                    session = AppointmentSession.builder()
                            .feegowAppointmentId(feegowAppointmentId)
                            .patientId(appt.patientId())
                            .doctorProfissionalId(appt.doctorId())
                            .appointmentAt(appt.startAt() != null ? appt.startAt() : LocalDateTime.now())
                            .status(br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CONFIRMED)
                            .lastInteractionAt(LocalDateTime.now())
                            .build();
                }

                String phone = session.getPhoneNumber();
                String patientName = "Paciente";

                if (appt.patientId() != null && !appt.patientId().isBlank()) {
                    try {
                        FeegowPatient patient = patientExternalPort.patientInfo(appt.patientId());
                        if (patient != null) {
                            if (phone == null || phone.isBlank()) {
                                phone = patient.phone();
                            }
                            if (patient.name() != null && !patient.name().isBlank()) {
                                patientName = patient.name().trim();
                            }
                        }
                    } catch (Exception pEx) {
                        log.warn("[GOOGLE-REVIEW] Falha ao obter dados do paciente ID {}: {}", appt.patientId(), pEx.getMessage());
                    }
                }

                if (phone == null || phone.isBlank()) {
                    log.warn("[GOOGLE-REVIEW] Telefone não encontrado para o agendamento ID {}. Impossível enviar pesquisa de avaliação.", feegowAppointmentId);
                    continue;
                }

                String doctorName = "Clínica Inovare";

                if (appt.doctorId() != null && !appt.doctorId().isBlank()) {
                    try {
                        Long docId = Long.parseLong(appt.doctorId().trim());
                        Optional<DoctorConfiguration> docConfigOpt = doctorConfigurationRepository.findById(docId);
                        if (docConfigOpt.isPresent()) {
                            DoctorConfiguration docCfg = docConfigOpt.get();
                            if (docCfg.getDoctorName() != null && !docCfg.getDoctorName().isBlank()) {
                                doctorName = docCfg.getDoctorName().trim();
                            }
                        }
                    } catch (Exception dEx) {
                        log.warn("[GOOGLE-REVIEW] Falha ao buscar configuração do médico ID {}: {}", appt.doctorId(), dEx.getMessage());
                    }
                }

                if ("Clínica Inovare".equalsIgnoreCase(doctorName) && appt.doctorName() != null && !appt.doctorName().isBlank()) {
                    doctorName = appt.doctorName().trim();
                }

                String doctorIdParam = (appt.doctorId() != null && !appt.doctorId().isBlank()) ? appt.doctorId().trim() : "default";

                log.info("[GOOGLE-REVIEW] Enviando template '{}' para agendamento ID {} (Paciente: {}, Médico: {}, Telefone: {}, DoctorID Param: {})",
                        TEMPLATE_REVIEW_GOOGLE, feegowAppointmentId, patientName, doctorName, phone, doctorIdParam);

                blipNotificationService.sendReviewTemplateMessage(phone, TEMPLATE_REVIEW_GOOGLE, patientName, doctorName, doctorIdParam);
                if (blipContextService != null) {
                    blipContextService.updateUserMasterState(phone, STATE_REVIEW_FINISHED);
                }

                session.setReviewRequestedAt(LocalDateTime.now());
                if (session.getPhoneNumber() == null || session.getPhoneNumber().isBlank()) {
                    session.setPhoneNumber(phone);
                }
                appointmentSessionRepository.save(session);
                countSent++;

            } catch (Exception ex) {
                log.error("[GOOGLE-REVIEW] Erro ao processar envio de avaliação para o agendamento ID {}: {}", feegowAppointmentId, ex.getMessage(), ex);
            }
        }

        log.info("[GOOGLE-REVIEW] Processamento concluído. Total de avaliações enviadas: {}", countSent);
        return countSent;
    }
}
