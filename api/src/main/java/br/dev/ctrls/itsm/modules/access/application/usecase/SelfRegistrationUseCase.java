package br.dev.ctrls.itsm.modules.access.application.usecase;

import br.dev.ctrls.itsm.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.itsm.modules.access.domain.model.AccessValidationResult;
import br.dev.ctrls.itsm.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.itsm.modules.access.domain.model.CpfValidator;
import br.dev.ctrls.itsm.modules.access.domain.model.DoctorAccessData;
import br.dev.ctrls.itsm.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.itsm.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.itsm.modules.access.domain.model.UserType;
import br.dev.ctrls.itsm.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.itsm.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.itsm.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.itsm.modules.access.domain.service.DoctorAccessResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de Uso: Auto-cadastro / Quiosque de Recepção (IMG- e INOV-).
 * Permite que pacientes sem agendamento prévio ou visitantes emitam credenciais físicas no totem.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelfRegistrationUseCase {

    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final GerAcessoClientPort gerAcessoClientPort;
    private final DoctorAccessResolver doctorAccessResolver;
    private final RegisterCompanionUseCase registerCompanionUseCase;

    public List<AccessCredential> processSelfRegistration(
            String name, String rawCpf, String phone, String birthDate, String clinic, CompanionAccessInfo companion) {
        return processSelfRegistration(name, rawCpf, phone, birthDate, clinic, null, null, companion != null ? List.of(companion) : List.of());
    }

    public List<AccessCredential> processSelfRegistration(
            String name, String rawCpf, String phone, String birthDate, String clinic, List<CompanionAccessInfo> companions) {
        return processSelfRegistration(name, rawCpf, phone, birthDate, clinic, null, null, companions);
    }

    public List<AccessCredential> processSelfRegistration(
            String name, String rawCpf, String phone, String birthDate, String clinic,
            String visitDateStr, String doctorName, List<CompanionAccessInfo> companions) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do paciente é obrigatório.");
        }
        if (rawCpf == null || rawCpf.isBlank()) {
            throw new IllegalArgumentException("CPF do paciente é obrigatório.");
        }

        String cleanCpf = CpfValidator.cleanCpf(rawCpf);
        if (cleanCpf.length() != 11) {
            throw new IllegalArgumentException("CPF inválido. Deve conter 11 dígitos.");
        }

        if (companions != null && !companions.isEmpty()) {
            Set<String> seenCompanionCpfs = new HashSet<>();
            for (CompanionAccessInfo comp : companions) {
                if (comp != null && comp.name() != null && !comp.name().isBlank()) {
                    String compCpf = CpfValidator.cleanCpf(comp.cpf());
                    if (!compCpf.isBlank() && compCpf.equals(cleanCpf)) {
                        throw new IllegalArgumentException("O acompanhante '" + comp.name() + "' possui o mesmo CPF do paciente titular. Cada pessoa precisa de seu próprio documento para liberar a catraca.");
                    }
                    if (comp.name().trim().equalsIgnoreCase(name.trim()) && name.trim().length() >= 3) {
                        throw new IllegalArgumentException("O acompanhante '" + comp.name() + "' não pode ser a mesma pessoa do paciente titular.");
                    }
                    if (!compCpf.isBlank()) {
                        if (seenCompanionCpfs.contains(compCpf)) {
                            throw new IllegalArgumentException("Foram informados acompanhantes duplicados com o mesmo CPF. Cada pessoa deve possuir um CPF único.");
                        }
                        seenCompanionCpfs.add(compCpf);
                    }
                }
            }
        }

        LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
        LocalDate visitDate = today;
        if (visitDateStr != null && !visitDateStr.isBlank()) {
            try {
                String cleanDate = visitDateStr.trim();
                if (cleanDate.contains("-")) {
                    visitDate = LocalDate.parse(cleanDate);
                } else if (cleanDate.contains("/")) {
                    visitDate = LocalDate.parse(cleanDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
            } catch (Exception ex) {
                log.warn("[SelfRegistration] Erro ao fazer parse da data da visita '{}': {}. Usando data de hoje.", visitDateStr, ex.getMessage());
            }
        }
        if (visitDate.isBefore(today)) {
            visitDate = today;
        }

        String dateIdSuffix = visitDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = (clinic != null && clinic.toLowerCase().contains("img")) ? "IMG-" : "PORT-";
        String appointmentId = prefix + dateIdSuffix + "-" + cleanCpf;

        List<AccessCredential> existing = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (existing != null && !existing.isEmpty()) {
            AccessCredential first = existing.getFirst();
            boolean createdToday = first.getCreatedAt() != null 
                    && first.getCreatedAt().toLocalDate().isEqual(today);

            if (createdToday) {
                log.info("[SelfRegistration] Credencial válida para o dia todo já existente ({}). CPF: {}, ID: {}", clinic, cleanCpf, appointmentId);
                if (companions != null && !companions.isEmpty()) {
                    for (CompanionAccessInfo comp : companions) {
                        if (comp != null && comp.name() != null && !comp.name().isBlank()) {
                            String compCpf = CpfValidator.cleanCpf(comp.cpf());
                            boolean compExists = existing.stream().anyMatch(c -> 
                                (c.getName() != null && c.getName().equalsIgnoreCase(comp.name().trim())) ||
                                (!compCpf.isEmpty() && c.getCpf() != null && CpfValidator.cleanCpf(c.getCpf()).equals(compCpf))
                            );
                            if (!compExists) {
                                registerCompanionUseCase.registerCompanionAccess(
                                    comp,
                                    visitDate,
                                    LocalTime.of(6, 0),
                                    LocalTime.of(23, 59),
                                    existing.getFirst().getAccessCredential(),
                                    existing.getFirst().getLocator(),
                                    appointmentId,
                                    null,
                                    doctorName
                                );
                            }
                        }
                    }
                    return accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
                }
                return existing;
            }
            log.info("[SelfRegistration] Credencial existente para auto-cadastro ({}) foi gerada em data anterior (criada em: {}). Renovando no GerAcesso para hoje. CPF: {}, ID: {}",
                    clinic, first.getCreatedAt(), cleanCpf, appointmentId);
        }

        LocalDateTime startWindow = LocalDateTime.of(visitDate, LocalTime.of(6, 0));
        LocalDateTime endWindow = LocalDateTime.of(visitDate, LocalTime.of(23, 59));
        String startDateFormatted = startWindow.format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);
        String endDateFormatted = endWindow.format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);

        String matricula = "";
        String doctorCpf = "";
        if (doctorName != null && !doctorName.isBlank()) {
            DoctorAccessData docData = doctorAccessResolver.resolveDoctorAccessDataByName(doctorName);
            matricula = docData.matricula();
            doctorCpf = docData.cpf();
        }

        GerAcessoRequest gerAcessoRequest = GerAcessoRequest.builder()
                .name(name.trim().toUpperCase())
                .cpf(cleanCpf)
                .status(1)
                .startVisit(startDateFormatted)
                .endVisit(endDateFormatted)
                .phone(phone != null ? phone.replaceAll("\\D", "") : "")
                .visitType(1)
                .visitedRegistration(matricula)
                .visitedCpf(doctorCpf)
                .build();

        String credentialValue = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String locatorValue = cleanCpf;

        try {
            Optional<GerAcessoResponse> gerResponseOpt = gerAcessoClientPort.registerAccess(gerAcessoRequest);
            if (gerResponseOpt.isPresent()) {
                if (gerResponseOpt.get().credential() != null && !gerResponseOpt.get().credential().isBlank()) {
                    credentialValue = gerResponseOpt.get().credential();
                }
                if (gerResponseOpt.get().locator() != null && !gerResponseOpt.get().locator().isBlank()) {
                    locatorValue = gerResponseOpt.get().locator();
                }
                log.info("[SelfRegistration] Paciente {} cadastrado no GerAcesso com sucesso para {}. CPF={}, Credential={}", clinic, visitDate, cleanCpf, credentialValue);
            }
        } catch (Exception ex) {
            log.warn("[SelfRegistration] Falha na integração GerAcesso para paciente {} (usando contingência): {}", clinic, ex.getMessage());
        }

        Optional<AccessCredential> existingPatientCredOpt = accessCredentialRepositoryPort
                .findByAppointmentId(appointmentId).stream()
                .filter(c -> c.getUserType() == UserType.PATIENT)
                .findFirst();

        String cleanSelfPhone = phone != null ? phone.replaceAll("\\D", "") : null;
        if (cleanSelfPhone != null && cleanSelfPhone.isBlank()) cleanSelfPhone = null;

        AccessCredential patientCred;
        if (existingPatientCredOpt.isPresent()) {
            patientCred = existingPatientCredOpt.get();
            patientCred.setName(name.trim().toUpperCase());
            patientCred.setCpf(cleanCpf);
            patientCred.setPhone(cleanSelfPhone);
            patientCred.setDoctorName(doctorName);
            patientCred.setAccessCredential(credentialValue);
            patientCred.setLocator(locatorValue);
            patientCred.setCreatedAt(LocalDateTime.now(AccessWindowCalculator.CLINIC_ZONE));
        } else {
            patientCred = AccessCredential.builder()
                    .appointmentId(appointmentId)
                    .name(name.trim().toUpperCase())
                    .cpf(cleanCpf)
                    .phone(cleanSelfPhone)
                    .doctorName(doctorName)
                    .userType(UserType.PATIENT)
                    .accessCredential(credentialValue)
                    .locator(locatorValue)
                    .createdAt(LocalDateTime.now(AccessWindowCalculator.CLINIC_ZONE))
                    .build();
        }
        accessCredentialRepositoryPort.save(patientCred);
        log.info("[SelfRegistration] Credencial do paciente titular salva: ID={}, Nome={}", patientCred.getId(), patientCred.getName());

        if (companions != null && !companions.isEmpty()) {
            for (CompanionAccessInfo comp : companions) {
                if (comp != null && comp.name() != null && !comp.name().isBlank()) {
                    try {
                        registerCompanionUseCase.registerCompanionAccess(
                            comp,
                            visitDate,
                            LocalTime.of(6, 0),
                            LocalTime.of(23, 59),
                            credentialValue,
                            locatorValue,
                            appointmentId,
                            null,
                            doctorName
                        );
                    } catch (Exception ex) {
                        log.error("[SelfRegistration] Erro ao cadastrar acompanhante no auto-cadastro: {}", ex.getMessage());
                    }
                }
            }
        }

        return accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
    }

    public AccessValidationResult processSelfRegistrationCpfUpdate(String appointmentId, String requestCpf) {
        String cleanCpf = CpfValidator.cleanCpf(requestCpf);
        if (!CpfValidator.isValidCpf(cleanCpf)) {
            return new AccessValidationResult(false, null, null, true, "Por favor, informe um CPF válido para prosseguir.");
        }

        List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (creds == null || creds.isEmpty()) {
            return new AccessValidationResult(false, null, null, false, "Credencial não encontrada para o agendamento informado.");
        }

        AccessCredential patientCred = creds.stream()
                .filter(c -> c.getUserType() == UserType.PATIENT)
                .findFirst()
                .orElse(creds.getFirst());

        patientCred.setCpf(cleanCpf);
        accessCredentialRepositoryPort.save(patientCred);

        if (patientCred.getAccessCredential() != null && patientCred.getAccessCredential().startsWith("CRED-")) {
            try {
                LocalDate visitDate = patientCred.getCreatedAt() != null ? patientCred.getCreatedAt().toLocalDate() : LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
                LocalDateTime startWindow = LocalDateTime.of(visitDate, LocalTime.of(6, 0));
                LocalDateTime endWindow = LocalDateTime.of(visitDate, LocalTime.of(23, 59));

                DoctorAccessData docData = doctorAccessResolver.resolveDoctorAccessDataByName(patientCred.getDoctorName());

                GerAcessoRequest req = GerAcessoRequest.builder()
                        .name(patientCred.getName())
                        .cpf(cleanCpf)
                        .status(1)
                        .startVisit(startWindow.format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER))
                        .endVisit(endWindow.format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER))
                        .phone(patientCred.getPhone() != null ? patientCred.getPhone() : "")
                        .visitType(1)
                        .visitedRegistration(docData.matricula())
                        .visitedCpf(docData.cpf())
                        .build();

                Optional<GerAcessoResponse> resp = gerAcessoClientPort.registerAccess(req);
                if (resp.isPresent() && resp.get().credential() != null && !resp.get().credential().isBlank()) {
                    patientCred.setAccessCredential(resp.get().credential());
                    if (resp.get().locator() != null && !resp.get().locator().isBlank()) {
                        patientCred.setLocator(resp.get().locator());
                    }
                    accessCredentialRepositoryPort.save(patientCred);
                    log.info("[SelfRegistration] Paciente atualizado no GerAcesso com credencial definitiva: {}", resp.get().credential());
                }
            } catch (Exception ex) {
                log.warn("[SelfRegistration] Falha ao sincronizar CPF com o GerAcesso: {}", ex.getMessage());
            }
        }

        return new AccessValidationResult(true, patientCred.getName(), patientCred.getAccessCredential(), false, "CPF atualizado com sucesso.");
    }
}
