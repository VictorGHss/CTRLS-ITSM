package br.dev.ctrls.inovareti.modules.access.application.usecase;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessValidationResult;
import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.CpfValidator;
import br.dev.ctrls.inovareti.modules.access.domain.model.DoctorAccessData;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.inovareti.modules.access.domain.service.DoctorAccessResolver;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Caso de Uso: Validação e Processamento Principal de Requisições de Acesso Físico às Catracas.
 * Gerencia agrupamento de consultas diárias, cadastro de titular no GerAcesso, cálculo de janelas
 * e orquestração assíncrona de acompanhantes via Virtual Threads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessAccessRequestUseCase {

    private final FeegowClientPort feegowClientPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final PatientExternalPort patientExternalPort;
    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final GerAcessoClientPort gerAcessoClientPort;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final DoctorAccessResolver doctorAccessResolver;
    private final AccessWindowCalculator accessWindowCalculator;
    private final ValidateAccessChallengeUseCase validateAccessChallengeUseCase;
    private final RegisterCompanionUseCase registerCompanionUseCase;
    private final SelfRegistrationUseCase selfRegistrationUseCase;

    private final ConcurrentMap<String, FeegowPatient> patientCache = new ConcurrentHashMap<>();

    private FeegowPatient getPatientInfoWithCache(String patientId) {
        if (patientId == null) {
            return null;
        }
        FeegowPatient cached = patientCache.get(patientId);
        if (cached != null) {
            return cached;
        }
        try {
            FeegowPatient patient = patientExternalPort.patientInfo(patientId);
            if (patient != null) {
                patientCache.put(patientId, patient);
            }
            return patient;
        } catch (Exception ex) {
            log.warn("[ProcessAccessRequest] Erro ao buscar prontuário do paciente ID: {} para cache. Causa: {}", patientId, ex.getMessage());
            return null;
        }
    }

    public AccessValidationResult execute(String appointmentId, String requestCpf, List<CompanionAccessInfo> companions) {
        return execute(appointmentId, requestCpf, companions, null, null, null);
    }

    public AccessValidationResult execute(
            String appointmentId,
            String requestCpf,
            List<CompanionAccessInfo> companions,
            UUID credentialId,
            String targetName,
            String userType) {

        log.info("[ProcessAccessRequest] Processando solicitação de acesso físico. Agendamento: {}, credentialId={}, targetName={}, userType={}",
                appointmentId, credentialId, targetName, userType);

        boolean isCompanionTarget = "COMPANION".equalsIgnoreCase(userType);
        if (!isCompanionTarget && credentialId != null) {
            List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
            isCompanionTarget = creds != null && creds.stream().anyMatch(c -> c.getId() != null && c.getId().equals(credentialId) && c.getUserType() == UserType.COMPANION);
        }

        if (isCompanionTarget && requestCpf != null && !requestCpf.isBlank()) {
            log.info("[ProcessAccessRequest] Redirecionando para atualização dedicada de CPF de ACOMPANHANTE: agendamento={}, targetName={}", appointmentId, targetName);
            return registerCompanionUseCase.processCompanionCpfUpdate(appointmentId, requestCpf, credentialId, targetName);
        }

        List<AccessCredential> existingList = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        boolean hasOnlyContingency = existingList != null && !existingList.isEmpty() && existingList.stream()
                .allMatch(c -> c.getAccessCredential() != null && c.getAccessCredential().startsWith("CRED-"));

        if (existingList != null && !existingList.isEmpty() && !hasOnlyContingency 
                && (requestCpf == null || requestCpf.isBlank()) && (companions == null || companions.isEmpty())) {
            log.info("[ProcessAccessRequest] Credenciais já existentes encontradas no banco para o agendamento ID: {}. Ignorando processamento redundante.", appointmentId);
            Optional<AccessCredential> patientCredOpt = existingList.stream()
                .filter(c -> c.getUserType() == UserType.PATIENT)
                .findFirst();
            String token = validateAccessChallengeUseCase.generateAccessToken(appointmentId, null);
            String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + token;
            if (patientCredOpt.isPresent()) {
                AccessCredential patientCred = patientCredOpt.get();
                return new AccessValidationResult(true, patientCred.getName(), patientCred.getAccessCredential(), false, "Credencial resolvida com sucesso (recuperada do banco).", token, accessUrl);
            } else {
                AccessCredential firstCred = existingList.getFirst();
                return new AccessValidationResult(true, firstCred.getName(), firstCred.getAccessCredential(), false, "Credencial resolvida com sucesso (recuperada do banco).", token, accessUrl);
            }
        }

        if (appointmentId != null && (appointmentId.startsWith("INOV-") || appointmentId.startsWith("IMG-"))) {
            return selfRegistrationUseCase.processSelfRegistrationCpfUpdate(appointmentId, requestCpf);
        }

        Optional<FeegowPatientAccessInfo> accessInfoOpt;
        try {
            accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        } catch (Exception ex) {
            log.warn("[ProcessAccessRequest] ERP Feegow indisponível ao buscar agendamento {}. Ativando Fallback Seguro.", appointmentId, ex);
            String token = validateAccessChallengeUseCase.generateAccessToken(appointmentId, null);
            String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + token;
            return new AccessValidationResult(false, null, null, true, "ERP Feegow temporária e indisponível. Solicite o CPF ao paciente.", token, accessUrl);
        }
        if (accessInfoOpt.isEmpty()) {
            log.warn("[ProcessAccessRequest] Não foi possível obter dados do agendamento {} na API Feegow.", appointmentId);
            return new AccessValidationResult(false, null, null, false, "Agendamento não encontrado.");
        }

        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();

        FeegowPatient mainPatient = getPatientInfoWithCache(accessInfo.patientId());
        String patientName = mainPatient != null ? mainPatient.name() : null;
        String patientBirthdate = mainPatient != null ? mainPatient.birthdate() : null;

        String rawFeegowCpf = accessInfo.cpf();
        if (rawFeegowCpf == null || rawFeegowCpf.isBlank()) {
            if (mainPatient != null && mainPatient.cpf() != null && !mainPatient.cpf().isBlank()) {
                rawFeegowCpf = mainPatient.cpf();
            }
        }

        String cleanFeegowCpf = CpfValidator.cleanCpf(rawFeegowCpf);
        String cleanRequestCpf = "";
        if (requestCpf != null && !requestCpf.isBlank() && !requestCpf.contains("{{") && !requestCpf.equalsIgnoreCase("null")) {
            cleanRequestCpf = CpfValidator.cleanCpf(requestCpf);
        }

        String resolvedCpf = null;
        if (CpfValidator.isValidCpf(cleanRequestCpf)) {
            resolvedCpf = cleanRequestCpf;
            try {
                String resolvedName = (patientName != null && !patientName.isBlank()) ? patientName : accessInfo.name();
                log.info("[ProcessAccessRequest] Sincronizando CPF válido informado ({}) com a Feegow para o paciente ID: {}", resolvedCpf, accessInfo.patientId());
                patientExternalPort.updatePatientCpf(accessInfo.patientId(), resolvedCpf, resolvedName, patientBirthdate);
            } catch (Exception e) {
                log.error("[ProcessAccessRequest] Falha ao sincronizar CPF com a Feegow: {}", e.getMessage());
            }
        } else if (CpfValidator.isValidCpf(cleanFeegowCpf)) {
            resolvedCpf = cleanFeegowCpf;
        }

        String targetPhone = mainPatient != null && mainPatient.phone() != null ? mainPatient.phone() : accessInfo.phone();

        if (resolvedCpf == null || resolvedCpf.isBlank() || !CpfValidator.isValidCpf(resolvedCpf)) {
            log.warn("[ProcessAccessRequest] Prontuário Feegow sem CPF válido pela Receita Federal (Feegow: '{}', request: '{}') para o paciente ID: {}. Ativando flag 'requiresCpfFallback'.",
                    cleanFeegowCpf, cleanRequestCpf, accessInfo.patientId());
            String token = validateAccessChallengeUseCase.generateAccessToken(appointmentId, targetPhone);
            String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + token;
            return new AccessValidationResult(false, null, null, true, "Por favor, confirme seu CPF para liberação da catraca.", token, accessUrl);
        }

        LocalDate appointmentDate = accessWindowCalculator.resolveAppointmentDate(accessInfo.appointmentDate());

        log.info("[ProcessAccessRequest] Buscando pauta do dia {} no Feegow para agrupamento de consultas...", appointmentDate);
        List<FeegowAppointment> dailyAppointments = appointmentExternalPort.searchAppointments(appointmentDate, 0);
        List<FeegowAppointment> matchingAppointments = new ArrayList<>();
        String finalCpf = resolvedCpf;

        for (FeegowAppointment app : dailyAppointments) {
            if (app.patientId().equals(accessInfo.patientId())) {
                matchingAppointments.add(app);
            }
        }

        if (accessInfo.patientId() != null) {
            try {
                var sessions = appointmentSessionRepository.findByPatientId(accessInfo.patientId());
                for (var s : sessions) {
                    if (s.getAppointmentAt() != null && s.getAppointmentAt().toLocalDate().equals(appointmentDate)) {
                        boolean alreadyPresent = matchingAppointments.stream().anyMatch(m -> m.id().equals(s.getFeegowAppointmentId()));
                        if (!alreadyPresent && s.getFeegowAppointmentId() != null) {
                            matchingAppointments.add(new FeegowAppointment(
                                s.getFeegowAppointmentId(),
                                accessInfo.patientId(),
                                s.getDoctorProfissionalId() != null ? s.getDoctorProfissionalId() : "",
                                "",
                                "",
                                s.getAppointmentAt(),
                                "1",
                                "",
                                "",
                                false
                            ));
                            log.info("[ProcessAccessRequest] Sessão adicional ({}) incorporada ao agrupamento para paciente {}", 
                                    s.getFeegowAppointmentId(), accessInfo.patientId());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[ProcessAccessRequest] Erro ao incorporar sessões locais no agrupamento: {}", ex.getMessage());
            }
        }

        if (matchingAppointments.isEmpty()) {
            matchingAppointments.add(new FeegowAppointment(
                accessInfo.appointmentId(),
                accessInfo.patientId(),
                accessInfo.doctorId(),
                accessInfo.doctorName(),
                "",
                LocalDateTime.of(appointmentDate, accessInfo.appointmentTime() != null ? accessInfo.appointmentTime() : LocalTime.of(12, 0)),
                "1",
                "",
                "",
                false
            ));
        }

        LocalTime earliestTime = matchingAppointments.stream()
            .map(app -> app.startAt().toLocalTime())
            .min(java.util.Comparator.naturalOrder())
            .orElse(accessInfo.appointmentTime() != null ? accessInfo.appointmentTime() : LocalTime.of(12, 0));

        LocalTime physicalOpeningTime = LocalTime.of(6, 0);
        LocalTime closingTime = LocalTime.of(23, 0);

        log.info("[ACCESS-WINDOW] Janela GerAcesso calculada para a data {}. Acesso físico liberado das {} às {}. Menor consulta: {}.",
            appointmentDate, physicalOpeningTime, closingTime, earliestTime);

        LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
        List<AccessCredential> existingForThisApp = accessCredentialRepositoryPort.findByAppointmentId(accessInfo.appointmentId());
        Optional<AccessCredential> activeCredOpt = existingForThisApp.stream()
            .filter(c -> c.getUserType() == UserType.PATIENT
                      && c.getAccessCredential() != null
                      && !c.getAccessCredential().startsWith("CRED-")
                      && c.getCreatedAt() != null
                      && c.getCreatedAt().toLocalDate().isEqual(today))
            .findFirst();

        String token;
        String locator;

        if (activeCredOpt.isPresent()) {
            AccessCredential existing = activeCredOpt.get();
            token = existing.getAccessCredential();
            locator = existing.getLocator();
            log.info("[ProcessAccessRequest] Reutilizando credencial ativa existente para o agendamento {}: {}", accessInfo.appointmentId(), token);
        } else {
            String startVisit = LocalDateTime.of(appointmentDate, physicalOpeningTime).format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);
            String endVisit = LocalDateTime.of(appointmentDate, closingTime).format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);

            String docId = accessInfo.doctorId();
            String matricula = "";
            String doctorCpf = "";
            if (docId != null && !docId.isBlank()) {
                DoctorAccessData docData = doctorAccessResolver.resolveDoctorAccessData(docId);
                matricula = docData.matricula();
                doctorCpf = docData.cpf();
            }

            GerAcessoRequest titularRequest = GerAcessoRequest.builder()
                .cpf(finalCpf)
                .status(1)
                .name(accessInfo.name())
                .phone(targetPhone != null ? targetPhone : "")
                .email("")
                .visitType(1)
                .startVisit(startVisit)
                .endVisit(endVisit)
                .visitedRegistration(matricula)
                .visitedCpf(doctorCpf)
                .build();

            boolean isCpfValid = finalCpf != null && finalCpf.length() == 11;
            Optional<GerAcessoResponse> responseOpt = Optional.empty();

            if (isCpfValid) {
                log.info("[ProcessAccessRequest] Enviando cadastro do paciente titular {} para a GerAcesso local (Médico: {})...", accessInfo.name(), accessInfo.doctorName());
                responseOpt = gerAcessoClientPort.registerAccess(titularRequest);
            } else {
                log.warn("[GERACESSO-CPF] CPF do paciente ID {} incompleto (CPF: {}). Ativando credencial contingencial.",
                        accessInfo.patientId(), finalCpf);
            }

            if (responseOpt.isPresent() && responseOpt.get().credential() != null && !responseOpt.get().credential().isBlank()) {
                token = responseOpt.get().credential().trim();
                locator = responseOpt.get().locator() != null ? responseOpt.get().locator().trim() : "";
                log.info("[ProcessAccessRequest] Cadastro concluído na GerAcesso. Credencial (QR Code)={}, Locator={}", token, locator);
            } else {
                token = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                locator = "LOC-" + System.currentTimeMillis();
                log.warn("[ProcessAccessRequest] Utilizando credencial local contingencial.");
            }
        }

        List<AccessCredential> savedList = accessCredentialRepositoryPort.findByAppointmentId(accessInfo.appointmentId());
        String cleanPatientPhone = targetPhone != null ? targetPhone.replaceAll("\\D", "") : null;
        if (cleanPatientPhone != null && cleanPatientPhone.isBlank()) cleanPatientPhone = null;

        if (savedList.isEmpty()) {
            AccessCredential credential = AccessCredential.builder()
                .appointmentId(accessInfo.appointmentId())
                .name(accessInfo.name())
                .cpf(finalCpf)
                .phone(cleanPatientPhone)
                .doctorName(accessInfo.doctorName())
                .userType(UserType.PATIENT)
                .accessCredential(token)
                .locator(locator)
                .createdAt(LocalDateTime.now())
                .build();

            accessCredentialRepositoryPort.save(credential);
            log.info("[ProcessAccessRequest] Credencial associada e salva para o agendamento ID: {}", accessInfo.appointmentId());
        } else {
            AccessCredential existingCred = savedList.getFirst();
            if (cleanPatientPhone != null && !cleanPatientPhone.isBlank()) {
                existingCred.setPhone(cleanPatientPhone);
            }
            if (accessInfo.doctorName() != null && !accessInfo.doctorName().isBlank()) {
                existingCred.setDoctorName(accessInfo.doctorName());
            }
            if (finalCpf != null && !finalCpf.isBlank() && CpfValidator.isValidCpf(finalCpf)) {
                existingCred.setCpf(finalCpf);
            }
            if (activeCredOpt.isEmpty() && !token.startsWith("CRED-")) {
                existingCred.setAccessCredential(token);
                existingCred.setLocator(locator);
                existingCred.setCreatedAt(LocalDateTime.now());
                accessCredentialRepositoryPort.save(existingCred);
                log.info("[ProcessAccessRequest] Credencial renovada com novo código GerAcesso ({}) para o agendamento ID: {}", token, accessInfo.appointmentId());
            } else if (existingCred.getAccessCredential() != null 
                    && existingCred.getAccessCredential().startsWith("CRED-") 
                    && !token.startsWith("CRED-")) {
                existingCred.setAccessCredential(token);
                existingCred.setLocator(locator);
                existingCred.setCreatedAt(LocalDateTime.now());
                accessCredentialRepositoryPort.save(existingCred);
                log.info("[ProcessAccessRequest] Atualizando credencial contingencial para credencial GerAcesso real para o agendamento ID: {}", accessInfo.appointmentId());
            } else {
                accessCredentialRepositoryPort.save(existingCred);
            }
        }

        if (companions != null && !companions.isEmpty()) {
            final List<AccessCredential> existingAppCreds = accessCredentialRepositoryPort.findByAppointmentId(accessInfo.appointmentId());
            registerCompanionUseCase.registerCompanionsParallel(
                companions,
                accessInfo,
                finalCpf,
                appointmentDate,
                physicalOpeningTime,
                closingTime,
                token,
                locator,
                existingAppCreds
            );
        }

        String magicToken = validateAccessChallengeUseCase.generateAccessToken(appointmentId, targetPhone);
        String accessUrl = "https://itsm-inovare.ctrls.dev.br/" + appointmentId + "?t=" + magicToken;
        return new AccessValidationResult(true, accessInfo.name(), token, false, "Credencial resolvida com sucesso.", magicToken, accessUrl);
    }
}
