package br.dev.ctrls.inovareti.modules.access.application.usecase;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.CpfValidator;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de Uso: Consulta de Credenciais de Acesso por CPF.
 * Suporta auto-cadastros recentes e agendamentos futuros da Feegow com geração sob demanda.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LookupCredentialsByCpfUseCase {

    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final PatientExternalPort patientExternalPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final ProcessAccessRequestUseCase processAccessRequestUseCase;

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
}
