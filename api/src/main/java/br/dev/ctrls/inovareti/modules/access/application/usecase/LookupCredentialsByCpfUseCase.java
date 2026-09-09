package br.dev.ctrls.inovareti.modules.access.application.usecase;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.CpfValidator;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.AccessCredentialResponse;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de Uso: Consulta de Credenciais de Acesso por CPF.
 * Suporta auto-cadastros recentes e agendamentos futuros da Feegow com geração sob demanda.
 */
@Slf4j
@Component
public class LookupCredentialsByCpfUseCase {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final PatientExternalPort patientExternalPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final ProcessAccessRequestUseCase processAccessRequestUseCase;
    private final FeegowClientPort feegowClientPort;

    public LookupCredentialsByCpfUseCase(
            AccessCredentialRepositoryPort accessCredentialRepositoryPort,
            PatientExternalPort patientExternalPort,
            AppointmentExternalPort appointmentExternalPort,
            ProcessAccessRequestUseCase processAccessRequestUseCase) {
        this(accessCredentialRepositoryPort, patientExternalPort, appointmentExternalPort, processAccessRequestUseCase, null);
    }

    @Autowired
    public LookupCredentialsByCpfUseCase(
            AccessCredentialRepositoryPort accessCredentialRepositoryPort,
            PatientExternalPort patientExternalPort,
            AppointmentExternalPort appointmentExternalPort,
            ProcessAccessRequestUseCase processAccessRequestUseCase,
            @Autowired(required = false) FeegowClientPort feegowClientPort) {
        this.accessCredentialRepositoryPort = accessCredentialRepositoryPort;
        this.patientExternalPort = patientExternalPort;
        this.appointmentExternalPort = appointmentExternalPort;
        this.processAccessRequestUseCase = processAccessRequestUseCase;
        this.feegowClientPort = feegowClientPort;
    }

    public List<AccessCredential> lookupCredentialsByCpf(String rawCpf, String clinic) {
        if (rawCpf == null || rawCpf.isBlank()) return List.of();
        String cleanCpf = CpfValidator.cleanCpf(rawCpf);
        if (cleanCpf.length() != 11) return List.of();

        LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
        LocalDate maxAllowedDate = today.plusDays(7);

        // 1) Auto-cadastro público recente (ex: INOV-20260903-CPF ou IMG-20260903-CPF)
        List<AccessCredential> allByCpf = accessCredentialRepositoryPort.findByCpf(cleanCpf);
        if (allByCpf != null && !allByCpf.isEmpty()) {
            List<AccessCredential> validAutoRegistrations = allByCpf.stream()
                .filter(c -> {
                    String apptId = c.getAppointmentId();
                    if (apptId == null || apptId.length() < 13 || (!apptId.startsWith("INOV-") && !apptId.startsWith("IMG-"))) {
                        return false;
                    }
                    try {
                        String datePart = apptId.substring(5, 13);
                        LocalDate appDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                        return !appDate.isBefore(today) && !appDate.isAfter(maxAllowedDate);
                    } catch (Exception ignored) {
                        return false;
                    }
                })
                .toList();

            if (!validAutoRegistrations.isEmpty()) {
                return expandAndSortCredentials(validAutoRegistrations);
            }
        }

        String todayIdSuffix = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = (clinic != null && clinic.toLowerCase().contains("inovare")) ? "INOV-" : "IMG-";
        String appointmentId = prefix + todayIdSuffix + "-" + cleanCpf;

        List<AccessCredential> credentials = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (credentials != null && !credentials.isEmpty()) {
            return expandAndSortCredentials(credentials);
        }

        // 2) Agendamentos Feegow futuros
        try {
            FeegowPatient feegowPatient = patientExternalPort.patientInfo(cleanCpf);
            if (feegowPatient != null && feegowPatient.id() != null && !feegowPatient.id().isBlank()) {
                List<FeegowAppointment> patientAppts = appointmentExternalPort.searchPatientAppointments(feegowPatient.id());
                if (patientAppts != null && !patientAppts.isEmpty()) {
                    List<FeegowAppointment> validUpcoming = patientAppts.stream()
                        .filter(a -> a.startAt() != null)
                        .filter(a -> {
                            LocalDate d = a.startAt().toLocalDate();
                            return !d.isBefore(today) && !d.isAfter(maxAllowedDate);
                        })
                        .sorted((a1, a2) -> a1.startAt().compareTo(a2.startAt()))
                        .toList();

                    if (!validUpcoming.isEmpty()) {
                        for (FeegowAppointment appt : validUpcoming) {
                            String apptIdStr = String.valueOf(appt.id());
                            List<AccessCredential> existing = accessCredentialRepositoryPort.findByAppointmentId(apptIdStr);
                            if (existing != null && !existing.isEmpty()) {
                                return expandAndSortCredentials(existing);
                            }
                        }

                        FeegowAppointment closest = validUpcoming.getFirst();
                        String closestIdStr = String.valueOf(closest.id());
                        log.info("[LookupCredentialsByCpf] Agendamento Feegow {} encontrado para CPF {}. Gerando credencial automaticamente...", closestIdStr, cleanCpf);
                        processAccessRequestUseCase.execute(closestIdStr, cleanCpf, null);
                        List<AccessCredential> newlyCreated = accessCredentialRepositoryPort.findByAppointmentId(closestIdStr);
                        if (newlyCreated != null && !newlyCreated.isEmpty()) {
                            return expandAndSortCredentials(newlyCreated);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[LookupCredentialsByCpf] Falha ao buscar agendamentos Feegow no lookup por CPF {}: {}", cleanCpf, ex.getMessage());
        }

        return List.of();
    }

    public List<AccessCredential> expandAndSortCredentials(List<AccessCredential> baseList) {
        if (baseList == null || baseList.isEmpty()) {
            return List.of();
        }
        Set<String> validAppointmentIds = new HashSet<>();
        for (AccessCredential cred : baseList) {
            if (cred != null && cred.getAppointmentId() != null && !cred.getAppointmentId().isBlank()) {
                validAppointmentIds.add(cred.getAppointmentId());
            }
        }

        Map<UUID, AccessCredential> uniqueCreds = new LinkedHashMap<>();
        for (String apptId : validAppointmentIds) {
            List<AccessCredential> byAppt = accessCredentialRepositoryPort.findByAppointmentId(apptId);
            if (byAppt != null) {
                for (AccessCredential c : byAppt) {
                    if (c != null && c.getId() != null) {
                        uniqueCreds.put(c.getId(), c);
                    }
                }
            }
        }

        List<AccessCredential> result = new ArrayList<>(uniqueCreds.values());
        result.sort((a, b) -> {
            if (a.getUserType() == UserType.PATIENT && b.getUserType() != UserType.PATIENT) return -1;
            if (a.getUserType() != UserType.PATIENT && b.getUserType() == UserType.PATIENT) return 1;
            return 0;
        });

        return result.isEmpty() ? baseList : result;
    }

    /**
     * Executa a busca completa de credenciais de acesso por CPF, enriquecendo os dados com
     * informações de agendamento do Feegow, regras de janela de acesso e formatação de resposta.
     */
    public List<AccessCredentialResponse> execute(String rawCpf, String clinic) {
        List<AccessCredential> credentials = lookupCredentialsByCpf(rawCpf, clinic);
        if (credentials.isEmpty()) {
            return List.of();
        }

        List<AccessCredentialResponse> responseList = new ArrayList<>();
        Map<String, Optional<FeegowPatientAccessInfo>> feegowCache = new HashMap<>();
        LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
        LocalDate maxAllowed = today.plusDays(7);

        for (AccessCredential cred : credentials) {
            String appointmentId = cred.getAppointmentId();
            String doctorName = cred.getDoctorName();
            String appointmentDateDisplay = resolveDisplayDate(appointmentId);
            String opensAt = "06:00";
            String closesAt = "23:59";

            if (appointmentId != null && appointmentId.length() >= 13 && (appointmentId.startsWith("INOV-") || appointmentId.startsWith("IMG-"))) {
                try {
                    String datePart = appointmentId.substring(5, 13);
                    LocalDate parsedDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                    if (parsedDate.isBefore(today) || parsedDate.isAfter(maxAllowed)) {
                        continue;
                    }
                } catch (Exception ignored) {}
            }

            if (appointmentId != null && !appointmentId.startsWith("INOV-") && !appointmentId.startsWith("IMG-")) {
                if (feegowClientPort != null) {
                    try {
                        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowCache.computeIfAbsent(
                            appointmentId,
                            feegowClientPort::fetchPatientAccessInfo
                        );
                        if (accessInfoOpt.isPresent()) {
                            FeegowPatientAccessInfo info = accessInfoOpt.get();
                            if (info.appointmentDate() != null) {
                                if (info.appointmentDate().isBefore(today) || info.appointmentDate().isAfter(maxAllowed)) {
                                    continue;
                                }
                            }
                            if (info.doctorName() != null && !info.doctorName().isBlank()) {
                                doctorName = info.doctorName();
                                if (cred.getDoctorName() == null || cred.getDoctorName().isBlank()) {
                                    cred.setDoctorName(doctorName);
                                    accessCredentialRepositoryPort.save(cred);
                                }
                            }
                            if (info.appointmentDate() != null) {
                                if (info.appointmentTime() != null) {
                                    appointmentDateDisplay = LocalDateTime.of(info.appointmentDate(), info.appointmentTime())
                                            .format(DATE_TIME_FORMATTER);
                                    opensAt = info.appointmentTime().minusMinutes(120).format(TIME_FORMATTER);
                                    closesAt = info.appointmentTime().plusMinutes(120).format(TIME_FORMATTER);
                                } else {
                                    appointmentDateDisplay = info.appointmentDate().format(DATE_FORMATTER);
                                    opensAt = "08:00";
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("[LookupCredentialsByCpf] Erro ao buscar detalhes Feegow no lookup por CPF para {}: {}", appointmentId, ex.getMessage());
                    }
                }
            }

            if (doctorName == null || doctorName.isBlank()) {
                boolean isInovare = (clinic != null && clinic.toLowerCase().contains("inovare"))
                        || (appointmentId != null && appointmentId.startsWith("INOV-"));
                doctorName = isInovare ? "Inovare – Serviços de Saúde" : "Clínica Da Imagem - Unidade Inovare";
            }

            responseList.add(new AccessCredentialResponse(
                cred.getAppointmentId(),
                cred.getName(),
                cred.getUserType() != null ? cred.getUserType() : UserType.PATIENT,
                cred.getLocator(),
                cred.getAccessCredential(),
                cred.getCpf(),
                doctorName,
                appointmentDateDisplay,
                opensAt,
                closesAt
            ));
        }

        if (responseList.isEmpty()) {
            return List.of();
        }

        responseList.sort((a, b) -> {
            if (a.userType() == UserType.PATIENT && b.userType() != UserType.PATIENT) return -1;
            if (a.userType() != UserType.PATIENT && b.userType() == UserType.PATIENT) return 1;
            return 0;
        });

        return responseList;
    }

    private String resolveDisplayDate(String appointmentId) {
        if (appointmentId != null && appointmentId.length() >= 13 && (appointmentId.startsWith("INOV-") || appointmentId.startsWith("IMG-"))) {
            try {
                String datePart = appointmentId.substring(5, 13);
                LocalDate parsedDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
                if (parsedDate.equals(today)) {
                    return "Hoje";
                } else if (parsedDate.equals(today.plusDays(1))) {
                    return "Amanhã (" + parsedDate.format(DateTimeFormatter.ofPattern("dd/MM")) + ")";
                } else {
                    return parsedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
            } catch (Exception ignored) {}
        }
        return "Hoje";
    }
}
