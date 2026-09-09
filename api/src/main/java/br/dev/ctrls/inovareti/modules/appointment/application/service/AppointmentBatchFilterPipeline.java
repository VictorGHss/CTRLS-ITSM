package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.FeegowLockDto;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.FeegowAppointmentStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline de filtragem de agendamentos em lote:
 * - Encaixes (flag encaixe).
 * - Agendas Bloqueadas no Feegow (/lock/list).
 * - Procedimentos elegíveis (consultas vs exames).
 * - Status Feegow elegíveis para disparo inicial.
 * - Médicos ativos e permitidos nas configurações.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentBatchFilterPipeline {

    private final AppointmentFilterService appointmentFilterService;
    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final DoctorEligibilityService doctorEligibilityService;

    public List<FeegowAppointment> filterEligibleAppointments(List<FeegowAppointment> appointments, List<String> requestedDoctorIds) {
        if (appointments == null || appointments.isEmpty()) {
            return List.of();
        }

        int total = appointments.size();

        // 1. Filtro de Encaixe
        List<FeegowAppointment> semEncaixe = appointments.stream()
                .filter(a -> {
                    if (a.encaixe() != null && a.encaixe()) {
                        log.info("[FILTRO-ENCAIXE] Agendamento ID={} ignorado porque é um encaixe.", a.id());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 2. Filtro de Agendas Bloqueadas (/lock/list)
        Map<LocalDate, List<FeegowLockDto>> locksCacheMap = new HashMap<>();
        List<FeegowAppointment> semBloqueio = semEncaixe.stream()
                .filter(a -> {
                    if (a.startAt() != null) {
                        LocalDate apptDate = a.startAt().toLocalDate();
                        List<FeegowLockDto> activeLocks =
                                locksCacheMap.computeIfAbsent(apptDate, d -> appointmentExternalPort.listLocks(d, d, null));
                        if (appointmentFilterService.isScheduleBlocked(a, activeLocks)) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 3. Filtro de Procedimentos (Recupera lista de IDs elegíveis configurada no .env / application.properties)
        List<String> eligibleProcedureIdsList = null;
        if (appointmentMotorProperties != null) {
            String prop = appointmentMotorProperties.getEligibleProcedureIds();
            if (prop != null && !prop.isBlank()) {
                eligibleProcedureIdsList = java.util.Arrays.stream(prop.split("[,;\\s]+"))
                        .map(s -> s != null ? s.trim() : "")
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        }

        final List<String> finalEligibleIds = eligibleProcedureIdsList;
        List<FeegowAppointment> elegiveisPorProcedimento = semBloqueio.stream()
                .filter(a -> {
                    boolean eligible = isProcedureEligible(a.procedureId(), a.procedureName(), finalEligibleIds);
                    if (!eligible) {
                        log.info("[FILTRO-PROCEDIMENTO] Agendamento ID={} ignorado (procId='{}', procName='{}') - Não consta nos IDs permitidos da .env.",
                                a.id(), a.procedureId(), a.procedureName());
                    }
                    return eligible;
                })
                .collect(Collectors.toList());

        // 4. Filtro de Status Feegow (somente status elegíveis para disparo inicial)
        List<FeegowAppointment> elegiveisPorStatus = elegiveisPorProcedimento.stream()
                .filter(a -> {
                    String statusId = a.statusId() != null ? a.statusId().trim() : "";
                    FeegowAppointmentStatus status = FeegowAppointmentStatus.fromId(statusId);
                    if (!status.isEligibleForInitialDispatch()) {
                        log.info("[FILTRO-STATUS] Agendamento ID={} ignorado pelo status {} ({}).", a.id(), statusId, status.getDescription());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 5. Filtro de Médicos Ativos e Permitidos
        List<FeegowAppointment> finalEligible = elegiveisPorStatus.stream()
                .filter(a -> isDoctorAllowed(a.doctorId(), requestedDoctorIds))
                .collect(Collectors.toList());

        log.info("[PIPELINE-FILTRAGEM] Processados {} agendamentos brutos -> {} elegíveis finais após todas as regras.",
                total, finalEligible.size());

        return finalEligible;
    }

    /**
     * Valida se um procedimento é elegível para disparo de confirmação.
     * Validação 100% dinâmica baseada na lista de IDs autorizados da variável de ambiente (.env / ELIGIBLE_PROCEDURE_IDS).
     *
     * @param procId O identificador do procedimento no Feegow
     * @param procName O nome descritivo do procedimento no Feegow
     * @param eligibleProcedureIdsList A lista de IDs permitidos carregada do .env
     * @return true se o procId constar na lista configurada; false caso contrário
     */
    public static boolean isProcedureEligible(String procId, String procName, List<String> eligibleProcedureIdsList) {
        if (procId == null || procId.isBlank()) {
            return false;
        }

        String cleanProcId = procId.trim();

        if (eligibleProcedureIdsList == null || eligibleProcedureIdsList.isEmpty()) {
            return false;
        }

        return eligibleProcedureIdsList.contains(cleanProcId);
    }

    public boolean isDoctorAllowed(String doctorId, List<String> requestedDoctorIds) {
        return doctorEligibilityService.isDoctorAllowed(doctorId, requestedDoctorIds);
    }
}
