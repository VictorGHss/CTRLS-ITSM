package br.dev.ctrls.inovareti.modules.access.application.usecase;

import br.dev.ctrls.inovareti.modules.access.domain.model.CpfValidator;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.FeegowPreRegistrationLookupResponse;
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
import java.util.List;

/**
 * Caso de Uso: Consulta prévia no ERP Feegow para auto-atendimento / quiosque de recepção.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LookupFeegowPreRegistrationUseCase {

    private final PatientExternalPort patientExternalPort;
    private final AppointmentExternalPort appointmentExternalPort;

    public FeegowPreRegistrationLookupResponse execute(String rawCpf, String clinic) {
        if (rawCpf == null || rawCpf.isBlank()) {
            return new FeegowPreRegistrationLookupResponse(false, null, null, null, List.of(), "CPF não informado.");
        }

        String cleanCpf = CpfValidator.cleanCpf(rawCpf);
        if (cleanCpf.length() != 11) {
            return new FeegowPreRegistrationLookupResponse(false, null, null, null, List.of(), "CPF deve ter 11 dígitos.");
        }

        log.info("[LookupFeegowPreRegistration] Consulta prévia no Feegow para auto-cadastro por CPF: {}", cleanCpf);

        try {
            FeegowPatient patient = patientExternalPort.patientInfo(cleanCpf);
            if (patient == null || patient.id() == null || patient.id().isBlank() || patient.name() == null || patient.name().isBlank()) {
                log.info("[LookupFeegowPreRegistration] Paciente não localizado no Feegow para o CPF {}", cleanCpf);
                return new FeegowPreRegistrationLookupResponse(false, null, null, null, List.of(), "Paciente não localizado no Feegow.");
            }

            String patientName = patient.name().trim();
            String patientPhone = patient.phone() != null ? patient.phone().trim() : "";
            String patientBirthDate = patient.birthdate() != null ? patient.birthdate().trim() : "";

            if (patientBirthDate.matches("\\d{4}[-/]\\d{2}[-/]\\d{2}")) {
                try {
                    String clean = patientBirthDate.replace('/', '-');
                    LocalDate bDate = LocalDate.parse(clean);
                    patientBirthDate = bDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } catch (Exception ignored) {}
            } else if (patientBirthDate.matches("\\d{2}[-/]\\d{2}[-/]\\d{4}")) {
                patientBirthDate = patientBirthDate.replace('-', '/');
            }

            if (patientPhone.startsWith("+55")) {
                patientPhone = patientPhone.substring(3).trim();
            }
            String cleanPhoneDigits = patientPhone.replaceAll("\\D", "");
            if (cleanPhoneDigits.startsWith("55") && (cleanPhoneDigits.length() == 12 || cleanPhoneDigits.length() == 13)) {
                cleanPhoneDigits = cleanPhoneDigits.substring(2);
            }
            if (!cleanPhoneDigits.isEmpty()) {
                patientPhone = cleanPhoneDigits;
            }

            List<FeegowAppointment> feegowAppts = appointmentExternalPort.searchPatientAppointments(patient.id());
            List<FeegowPreRegistrationLookupResponse.FeegowAppointmentItemDto> appointmentDtos = new ArrayList<>();

            if (feegowAppts != null && !feegowAppts.isEmpty()) {
                LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
                LocalDate tomorrow = today.plusDays(1);

                DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
                DateTimeFormatter dateIsoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                DateTimeFormatter dateBrFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

                for (FeegowAppointment appt : feegowAppts) {
                    if (appt.startAt() == null) continue;

                    LocalDate apptDate = appt.startAt().toLocalDate();
                    if (apptDate.isBefore(today) || apptDate.isAfter(today.plusDays(7))) {
                        continue;
                    }

                    boolean isToday = apptDate.equals(today);
                    boolean isTomorrow = apptDate.equals(tomorrow);

                    String formattedDateLabel;
                    if (isToday) {
                        formattedDateLabel = "Hoje às " + appt.startAt().format(timeFmt);
                    } else if (isTomorrow) {
                        formattedDateLabel = "Amanhã às " + appt.startAt().format(timeFmt);
                    } else {
                        formattedDateLabel = appt.startAt().format(dateBrFmt) + " às " + appt.startAt().format(timeFmt);
                    }

                    String docName = appt.doctorName() != null && !appt.doctorName().isBlank() 
                            ? appt.doctorName().trim() 
                            : "";
                    String specialty = appt.procedureName() != null && !appt.procedureName().isBlank() 
                            ? appt.procedureName().trim() 
                            : "";

                    appointmentDtos.add(new FeegowPreRegistrationLookupResponse.FeegowAppointmentItemDto(
                        appt.id(),
                        docName,
                        specialty,
                        apptDate.format(dateIsoFmt),
                        appt.startAt().format(timeFmt),
                        formattedDateLabel,
                        isToday,
                        null
                    ));
                }

                appointmentDtos.sort((a, b) -> {
                    int c = a.date().compareTo(b.date());
                    return c != 0 ? c : a.time().compareTo(b.time());
                });
            }

            return new FeegowPreRegistrationLookupResponse(
                true,
                patientName,
                patientBirthDate,
                patientPhone,
                appointmentDtos,
                appointmentDtos.isEmpty() ? "Cadastro localizado no Feegow." : "Consultas localizadas no Feegow."
            );
        } catch (Exception ex) {
            log.error("[LookupFeegowPreRegistration] Falha ao consultar pré-cadastro no Feegow para o CPF {}: {}", cleanCpf, ex.getMessage(), ex);
            return new FeegowPreRegistrationLookupResponse(false, null, null, null, List.of(), "Falha temporária ao consultar Feegow.");
        }
    }
}
