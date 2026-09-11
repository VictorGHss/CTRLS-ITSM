package br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.input.scheduler;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.dev.ctrls.itsm.modules.appointment.application.usecase.IngestAppointmentsUseCase;
import br.dev.ctrls.itsm.modules.appointment.application.usecase.MonitorAppointmentNudgesUseCase;
import br.dev.ctrls.itsm.modules.appointment.application.usecase.SendPreAppointmentNoticeUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class AppointmentMotorScheduler {

    private final AppointmentMotorProperties properties;
    private final IngestAppointmentsUseCase ingestAppointmentsUseCase;
    private final MonitorAppointmentNudgesUseCase monitorAppointmentNudgesUseCase;
    private final br.dev.ctrls.itsm.modules.appointment.application.service.DoctorEligibilityService doctorEligibilityService;

    @Scheduled(cron = "${app.appointment.motor.ingestion-cron}")
    public void ingestDPlusOneAppointments() {
        if (!properties.isEnabled()) {
            return;
        }

        log.info("Scheduler de ingestão de agendamentos iniciado");

        if (properties.isTestMode()) {
            java.util.List<String> testDocs = properties.getTestDoctorIds();
            log.info("Scheduler em modo TESTE. Direcionando para os médicos de teste: {}", testDocs);
            ingestAppointmentsUseCase.execute(testDocs);
        } else {
            java.util.List<String> activeDocs = doctorEligibilityService.getActiveAllowedDoctorIds();
            log.info("Scheduler em modo PRODUÇÃO. Executando ingestão completa para médicos ativos e configurados: {}", activeDocs);
            ingestAppointmentsUseCase.execute(activeDocs);
        }
    }

    @Scheduled(cron = "${app.appointment.motor.monitor-cron:0 0 9,11,13,15,17 * * MON-FRI}", zone = "America/Sao_Paulo")
    public void monitorNudges() {
        int currentHour = java.time.LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo")).getHour();
        log.info("[NUDGE-SCHEDULER] Disparando ciclo de monitoramento de nudges das {}h...", currentHour);

        if (!properties.isEnabled()) {
            return;
        }

        monitorAppointmentNudgesUseCase.execute();
    }

    private final SendPreAppointmentNoticeUseCase sendPreAppointmentNoticeUseCase;

    @Scheduled(cron = "${app.appointment.motor.pre-consultation-cron:0 */15 7-19 * * MON-SAT}", zone = "America/Sao_Paulo")
    public void monitorPreConsultationReminders() {
        if (!properties.isEnabled()) {
            return;
        }

        sendPreAppointmentNoticeUseCase.execute();
    }
}


