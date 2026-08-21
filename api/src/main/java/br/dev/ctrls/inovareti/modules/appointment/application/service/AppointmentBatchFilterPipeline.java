package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.infrastructure.shared.utils.TextNormalizer;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.FeegowLockDto;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.FeegowAppointmentStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
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
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;

    private static final List<String> ALLOWED_PROCEDURE_KEYWORDS = List.of(
        "mano", "phmetria", "impedancia", "teste de contato", "teste cutaneo",
        "leitura de teste", "mostra de exames", "telemedicina", "consulta", "antecipar",
        "emergencia", "dilatacao pre refrativa", "pontos", "aplicacao", "pintar",
        "exame", "curativo", "avaliacao", "retirada do dreno", "botox", "conversar cirurgia",
        "retirada de pontos", "acertar cirurgia", "lobuloplastia", "laser co2", "infiltracao",
        "pulsao", "puncao", "viscossuplementacao", "toc", "primeira consulta", "guiados por usg",
        "skin booster", "intradermoterapia", "preenchimento", "microagulhamento", "excisao e sutura",
        "retorno", "bioestimulador", "electroagulacao", "eletrocoagulacao", "exerese e sutura",
        "biopsia", "intradermo capilar", "peeling"
    );

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

        // 3. Filtro de Procedimentos / Categorias
        List<FeegowAppointment> elegiveisPorProcedimento = semBloqueio.stream()
                .filter(a -> isProcedureEligible(a.procedureId(), a.procedureName(), null))
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

    public static boolean isProcedureEligible(String procId, String procName, List<String> eligibleProcedureIdsList) {
        if (procName != null) {
            String norm = TextNormalizer.normalize(procName);

            // BLOQUEIO ESTRITO DE CIRURGIAS (ex: CIRURGIAS MU, CIRURGIAS MAR, CIRURGIA DR. MURILO, etc.)
            // Exceto especificamente "conversar cirurgia", "acertar cirurgia" e retornos pós-cirúrgicos
            if (norm.contains("cirurg")) {
                boolean isExemption = norm.contains("conversar cirurgia")
                        || norm.contains("acertar cirurgia")
                        || norm.contains("retorno");
                if (!isExemption) {
                    return false;
                }
            }

            // Exclusões explícitas de agendas cirúrgicas de médicos
            if (norm.contains("cirurgias mu") || norm.contains("cirurgias mar") || norm.contains("cirurgia dr") || norm.contains("cirurgias dr")) {
                return false;
            }

            // Se for consulta ou retorno padrão, é elegível
            if (norm.startsWith("consulta") || norm.equals("consulta") || norm.startsWith("retorno") || norm.equals("retorno")) {
                return true;
            }
        }

        // Se o ID estiver na lista de IDs elegíveis
        if (procId != null && !procId.isBlank() && eligibleProcedureIdsList != null && eligibleProcedureIdsList.contains(procId.trim())) {
            return true;
        }

        // Se o nome corresponder a qualquer termo da lista permitida
        if (procName != null) {
            String norm = TextNormalizer.normalize(procName);

            for (String allowed : ALLOWED_PROCEDURE_KEYWORDS) {
                if (norm.contains(allowed)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isDoctorAllowed(String doctorId, List<String> requestedDoctorIds) {
        if (doctorId == null || doctorId.isBlank()) {
            return false;
        }
        String docId = doctorId.trim();

        if (requestedDoctorIds != null && !requestedDoctorIds.isEmpty() && !requestedDoctorIds.contains(docId)) {
            return false;
        }

        if (appointmentMotorProperties.getTestDoctorIds().contains(docId)) {
            return true;
        }

        if (appointmentMotorProperties.getActiveDoctorIds().contains(docId)) {
            return true;
        }

        try {
            Long id = Long.parseLong(docId);
            var configOpt = doctorConfigurationRepository.findById(id);
            if (configOpt.isPresent() && configOpt.get().isConfigActive()) {
                return true;
            }
        } catch (Exception ignored) {}

        // Fallback: mapeamento no banco
        var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(docId);
        return mappingOpt.isPresent();
    }
}
