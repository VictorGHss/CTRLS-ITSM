package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentBatchFilterPipeline;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentCancellationReconciler;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentGroupDispatcher;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentGroupDispatcher.DispatchResult;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentIngestionDateResolver;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentIngestionDateResolver.ResolvedDatesAndAppointments;
import br.dev.ctrls.inovareti.modules.appointment.application.service.FeegowPatientDetailsFetcher;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.FeegowAppointmentStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso responsável pela ingestão diária de agendamentos vindos do Feegow,
 * gerando sessões locais e disparando notificações individuais ou em lote.
 *
 * Arquitetura Refatorada:
 * - Resolução de datas e busca de dados delegadas ao AppointmentIngestionDateResolver.
 * - Invalidação de consultas canceladas/remarcadas delegada ao AppointmentCancellationReconciler.
 * - Filtros de negócio delegados ao AppointmentBatchFilterPipeline.
 * - Despacho de mensagens paralelo e transacional delegado ao AppointmentGroupDispatcher.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestAppointmentsUseCase {

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final AppointmentIngestionDateResolver dateResolver;
    private final AppointmentCancellationReconciler cancellationReconciler;
    private final AppointmentBatchFilterPipeline filterPipeline;
    private final FeegowPatientDetailsFetcher feegowPatientDetailsFetcher;
    private final AppointmentGroupDispatcher groupDispatcher;
    private final br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentMetricsService appointmentMetricsService;
    private final br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.discord.AppointmentDiscordNotifierService discordNotifierService;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository doctorConfigurationRepository;

    public record IngestionSummary(
        int totalReceived,
        int filteredReceived,
        int sessionsCreated,
        int messagesSent,
        String mode
    ) {}

    public static boolean isProcedureEligible(String procId, String procName, List<String> eligibleProcedureIdsList) {
        return AppointmentBatchFilterPipeline.isProcedureEligible(procId, procName, eligibleProcedureIdsList);
    }

    public static boolean isFeegowConfirmedStatus(String statusId) {
        return FeegowAppointmentStatus.fromId(statusId).isConfirmedOrPresent();
    }

    public IngestionSummary execute() {
        return execute(null, false, null);
    }

    public IngestionSummary execute(List<String> doctorIds) {
        return execute(doctorIds, false, null);
    }

    public IngestionSummary execute(List<String> doctorIds, boolean forceSend) {
        return execute(doctorIds, forceSend, null);
    }

    public IngestionSummary execute(List<String> doctorIds, boolean forceSend, String testPhone) {
        long startTime = System.currentTimeMillis();
        final String overridePhone = (testPhone != null && !testPhone.isBlank())
                ? testPhone.trim().replaceAll("\\D", "")
                : null;

        if (overridePhone != null) {
            log.info("[BLINDAGEM-TESTE] Modo de teste ativado (testPhone={}). Redirecionando disparos para o telefone {}.",
                    testPhone, overridePhone);
        }

        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();

        // Motor não opera aos finais de semana
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            log.info("[MOTOR-INGESTÃO] Dia da semana: {}. Motor não opera aos finais de semana. Encerrando sem processar.", dayOfWeek);
            return new IngestionSummary(0, 0, 0, 0, "WEEKEND_SKIP");
        }

        // 1. Resolução de Datas-Alvo e Busca de Agendamentos no Feegow
        ResolvedDatesAndAppointments resolved = dateResolver.resolveDatesAndFetchAppointments(today, dayOfWeek, doctorIds);
        List<LocalDate> targetDates = resolved.targetDates();
        List<FeegowAppointment> rawAppointments = resolved.appointments();
        int totalRaw = rawAppointments.size();

        // 2. Reconciliação e Invalidação de Consultas Canceladas/Remarcadas
        cancellationReconciler.reconcile(targetDates, rawAppointments);

        // 3. Pipeline de Filtros de Elegibilidade (Encaixes, Locks, Procedimentos, Status, Médicos Ativos)
        List<FeegowAppointment> eligibleAppointments = filterPipeline.filterEligibleAppointments(rawAppointments, doctorIds);
        int totalFiltered = eligibleAppointments.size();

        if (eligibleAppointments.isEmpty()) {
            log.info("[MOTOR-INGESTÃO] Nenhum agendamento elegível para notificação após filtros.");
            return new IngestionSummary(totalRaw, 0, 0, 0, "NO_ELIGIBLE_APPOINTMENTS");
        }

        // Blindagem de Teste: Limita a 2 agendamentos em modo de teste manual
        if (overridePhone != null && eligibleAppointments.size() > 2) {
            log.info("[BLINDAGEM-TESTE] Limitando lote de teste de {} para 2 agendamentos.", eligibleAppointments.size());
            eligibleAppointments = eligibleAppointments.subList(0, 2);
        }

        // 4. Busca em Lote de Detalhes dos Pacientes (Telefones, CPFs)
        Set<String> patientIds = new java.util.HashSet<>();
        for (FeegowAppointment appt : eligibleAppointments) {
            if (appt != null && appt.patientId() != null && !appt.patientId().isBlank()) {
                patientIds.add(appt.patientId().trim());
            }
        }

        Map<String, FeegowPatient> patientDetailsMap = feegowPatientDetailsFetcher.fetchPatientDetailsInParallel(patientIds);

        // 5. Agrupamento por Telefone do Paciente e Mapeamento de Atenção
        Map<String, List<FeegowAppointment>> groupedByPhone = new HashMap<>();
        Map<Long, List<br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.discord.AppointmentDiscordNotifierService.PatientAttentionItem>> attentionByDoctor = new HashMap<>();
        Map<Long, Integer> appointmentsCountByDoctor = new HashMap<>();

        for (FeegowAppointment appt : eligibleAppointments) {
            Long docId = null;
            try {
                if (appt.doctorId() != null) {
                    docId = Long.parseLong(appt.doctorId().trim());
                    appointmentsCountByDoctor.put(docId, appointmentsCountByDoctor.getOrDefault(docId, 0) + 1);
                }
            } catch (Exception ignored) {}

            FeegowPatient patient = patientDetailsMap.get(appt.patientId());
            String phone = (patient != null && patient.phone() != null)
                    ? patient.phone().trim().replaceAll("\\D", "")
                    : "";

            if (phone.isBlank()) {
                log.warn("[INGESTÃO-TELEFONE] Paciente ID={} sem telefone válido. Ignorando agendamento ID={}.",
                        appt.patientId(), appt.id());
                appointmentMetricsService.incrementPhoneMissing(appt.doctorName());

                if (docId != null) {
                    String pName = (patient != null && patient.name() != null) ? patient.name() : ("Paciente ID " + appt.patientId());
                    String timeStr = appt.startAt() != null ? appt.startAt().toLocalTime().toString() : "--:--";
                    attentionByDoctor.computeIfAbsent(docId, k -> new ArrayList<>()).add(
                            br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.discord.AppointmentDiscordNotifierService.PatientAttentionItem.builder()
                                    .patientName(pName)
                                    .appointmentTime(timeStr)
                                    .procedureName(appt.procedureName())
                                    .reason("Sem telefone no Feegow")
                                    .build()
                    );
                }
                continue;
            }

            if (!phone.startsWith("55") && phone.length() <= 11) {
                phone = "55" + phone;
            }

            groupedByPhone.computeIfAbsent(phone, k -> new ArrayList<>()).add(appt);
        }

        log.info("[MOTOR-INGESTÃO] {} agendamentos agrupados em {} telefones únicos para processamento.",
                eligibleAppointments.size(), groupedByPhone.size());

        // 6. Despacho Concorrente via Virtual Threads
        DispatchResult result = groupDispatcher.dispatchGroups(
                groupedByPhone,
                patientDetailsMap,
                forceSend,
                overridePhone
        );

        long durationMs = System.currentTimeMillis() - startTime;
        appointmentMetricsService.recordIngestionDuration(durationMs);

        // 7. Notificações Discord por Médico e Resumo Geral
        try {
            Map<Long, br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration> doctorConfigMap = new HashMap<>();
            doctorConfigurationRepository.findAll().forEach(cfg -> {
                if (cfg.getFeegowProfissionalId() != null) {
                    doctorConfigMap.put(cfg.getFeegowProfissionalId(), cfg);
                }
            });

            // Agrupa disparos por médico para métricas e relatórios
            Map<Long, Integer> dispatchedByDoctor = new HashMap<>();
            for (List<FeegowAppointment> apptList : groupedByPhone.values()) {
                for (FeegowAppointment a : apptList) {
                    try {
                        Long dId = Long.parseLong(a.doctorId().trim());
                        dispatchedByDoctor.put(dId, dispatchedByDoctor.getOrDefault(dId, 0) + 1);
                        appointmentMetricsService.incrementDispatched(a.doctorName(), a.procedureName());
                    } catch (Exception ignored) {}
                }
            }

            // Dispara relatório Discord para os médicos que tiveram agendamentos
            for (Map.Entry<Long, Integer> entry : appointmentsCountByDoctor.entrySet()) {
                Long dId = entry.getKey();
                br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration cfg = doctorConfigMap.get(dId);
                String docName = cfg != null ? cfg.getDoctorName() : ("Médico " + dId);
                String channelId = cfg != null ? cfg.getDiscordChannelId() : null;

                if (channelId != null && !channelId.isBlank()) {
                    int totalAppointments = entry.getValue();
                    int dispatched = dispatchedByDoctor.getOrDefault(dId, 0);
                    List<br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.discord.AppointmentDiscordNotifierService.PatientAttentionItem> attentionItems = attentionByDoctor.get(dId);

                    discordNotifierService.sendDoctorDispatchReport(
                            br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.discord.AppointmentDiscordNotifierService.DoctorDispatchSummary.builder()
                                    .doctorId(dId)
                                    .doctorName(docName)
                                    .discordChannelId(channelId)
                                    .targetDate(targetDates.isEmpty() ? today : targetDates.get(0))
                                    .totalConsultas(totalAppointments)
                                    .totalDisparados(dispatched)
                                    .totalPreConfirmados(0)
                                    .attentionList(attentionItems)
                                    .build()
                    );
                }
            }

        } catch (Exception ex) {
            log.warn("[MOTOR-INGESTÃO] Falha ao processar notificações Discord pós-ingestão por médico: {}", ex.getMessage());
        }

        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        log.info("[MOTOR-INGESTÃO] Ingestão concluída com sucesso! Modo: {}. Processados: {}, Criados: {}, Disparados: {}, Ignorados: {}",
                mode, result.processedCount(), result.sessionsCreatedCount(), result.templatesSentCount(), result.skippedCount());

        return new IngestionSummary(
                totalRaw,
                totalFiltered,
                result.sessionsCreatedCount(),
                result.templatesSentCount(),
                mode
        );
    }
}
