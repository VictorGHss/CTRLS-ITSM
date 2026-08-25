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

    /**
     * Termos e palavras-chave terminantemente proibidos de receber confirmações automáticas
     * (Cirurgias hospitalares, recados internos, tarefas, bloqueios de agenda, etc.).
     */
    private static final List<String> BLACKLISTED_PROCEDURE_TERMS = List.of(
        "cirurg", "cirúrg", "recado", "tarefa", "bloqueio", "reserva",
        "aviso", "lembrete", "reuniao", "reunião", "pessoal", "compromisso",
        "plantao", "plantão", "feriado", "folga", "cirurgia geral", "cirurgia plastica",
        "cirurgia plástica", "cirurgia vascular", "cirurgia toracica", "cirurgia torácica",
        "procedimento cirurgico", "procedimento cirúrgico", "cirurgias mu", "cirurgias mar"
    );

    /**
     * Isenções permitidas para termos cirúrgicos (atendimentos ambulatoriais de pré e pós-operatório).
     */
    private static final List<String> SURGERY_EXEMPTIONS = List.of(
        "conversar cirurgia", "acertar cirurgia", "retorno cirurgico", "retorno cirúrgico",
        "retorno de cirurgia", "pos cirurgico", "pós cirúrgico", "pos-cirurgico", "pós-operatório"
    );

    /**
     * Lista de palavras-chave autorizadas para procedimentos e consultas ambulatoriais.
     */
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

        // 3. Filtro de Procedimentos / Categorias (Recupera lista de IDs elegíveis configurada)
        List<String> eligibleProcedureIdsList = null;
        if (appointmentMotorProperties != null) {
            String prop = appointmentMotorProperties.getEligibleProcedureIds();
            if (prop != null && !prop.isBlank()) {
                eligibleProcedureIdsList = java.util.Arrays.stream(prop.split(","))
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
                        log.info("[FILTRO-PROCEDIMENTO] Agendamento ID={} ignorado (procId={}, procName='{}').",
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
     * Valida se um procedimento/evento de agenda é elegível para disparo de confirmação.
     * Bloqueia rigorosamente cirurgias, recados, tarefas, bloqueios e eventos sem procedimento.
     */
    public static boolean isProcedureEligible(String procId, String procName, List<String> eligibleProcedureIdsList) {
        String normName = (procName != null) ? TextNormalizer.normalize(procName).trim() : "";
        String cleanProcId = (procId != null) ? procId.trim() : "";

        // 1. BLOQUEIO ESTRITO POR BLACKLIST (Cirurgias hospitalares, recados, tarefas, bloqueios de agenda)
        if (!normName.isEmpty()) {
            for (String blocked : BLACKLISTED_PROCEDURE_TERMS) {
                if (normName.contains(blocked)) {
                    // Verifica se possui isenção explícita de consulta/atendimento de consultório
                    boolean isExempt = false;
                    for (String exemption : SURGERY_EXEMPTIONS) {
                        if (normName.contains(exemption)) {
                            isExempt = true;
                            break;
                        }
                    }
                    if (!isExempt) {
                        return false;
                    }
                }
            }
        }

        // Se o agendamento não possui nome ou veio explicitamente sem procedimento/vazio, bloqueia
        if (normName.isEmpty() && cleanProcId.isEmpty()) {
            return false;
        }
        if ("sem procedimento".equals(normName)) {
            return false;
        }

        // 2. VALIDAÇÃO POR LISTA DE IDS AUTORIZADOS (Se configurada)
        if (eligibleProcedureIdsList != null && !eligibleProcedureIdsList.isEmpty()) {
            if (!cleanProcId.isEmpty() && eligibleProcedureIdsList.contains(cleanProcId)) {
                return true;
            }
            // Se o ID foi informado e NÃO consta na lista de IDs autorizados da clínica, bloqueia!
            if (!cleanProcId.isEmpty()) {
                return false;
            }
        }

        // 3. SE NÃO TEMOS ID VÁLIDO OU LISTA DE IDS, VALIDAÇÃO POR PALAVRAS-CHAVE PERMITIDAS
        if (!normName.isEmpty()) {
            if (normName.startsWith("consulta") || normName.equals("consulta")
                    || normName.startsWith("retorno") || normName.equals("retorno")) {
                return true;
            }

            for (String allowed : ALLOWED_PROCEDURE_KEYWORDS) {
                if (normName.contains(allowed)) {
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
