package br.dev.ctrls.itsm.modules.access.application.usecase;

import br.dev.ctrls.itsm.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.itsm.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.itsm.modules.access.domain.model.AccessValidationResult;
import br.dev.ctrls.itsm.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.itsm.modules.access.domain.model.CpfValidator;
import br.dev.ctrls.itsm.modules.access.domain.model.DoctorAccessData;
import br.dev.ctrls.itsm.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.itsm.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.itsm.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.itsm.modules.access.domain.model.UserType;
import br.dev.ctrls.itsm.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.itsm.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.itsm.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.itsm.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.itsm.modules.access.domain.service.DoctorAccessResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Caso de Uso: Cadastro e Gerenciamento de Acompanhantes nas Catracas Físicas.
 * Orquestra validações anti-duplicação, cadastros paralelos com Virtual Threads e comunicação com GerAcesso.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterCompanionUseCase {

    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final FeegowClientPort feegowClientPort;
    private final GerAcessoClientPort gerAcessoClientPort;
    private final DoctorAccessResolver doctorAccessResolver;
    private final AccessWindowCalculator accessWindowCalculator;

    /**
     * Cadastra um acompanhante isoladamente para um determinado agendamento.
     */
    public AccessCredential registerCompanion(String appointmentId, CompanionAccessInfo companion) {
        log.info("[RegisterCompanion] Processando cadastro individual de acompanhante '{}' para agendamento {}", companion.name(), appointmentId);

        List<AccessCredential> existingCreds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        String companionCleanCpf = CpfValidator.cleanCpf(companion.cpf());
        String companionCleanName = companion.name() != null ? companion.name().trim() : "";

        if (existingCreds != null && !existingCreds.isEmpty()) {
            for (AccessCredential cred : existingCreds) {
                String existingCleanCpf = CpfValidator.cleanCpf(cred.getCpf());
                if (!companionCleanCpf.isBlank() && !existingCleanCpf.isBlank() && companionCleanCpf.equals(existingCleanCpf)) {
                    if (cred.getUserType() == UserType.PATIENT) {
                        throw new IllegalArgumentException("O CPF informado pertence ao paciente titular. Cada pessoa precisa de seu próprio documento para liberar a catraca.");
                    } else {
                        throw new IllegalArgumentException("Já existe um acompanhante cadastrado com este CPF (" + cred.getName() + ").");
                    }
                }
                if (cred.getUserType() == UserType.PATIENT && cred.getName() != null 
                        && cred.getName().trim().equalsIgnoreCase(companionCleanName) && companionCleanName.length() >= 3) {
                    throw new IllegalArgumentException("O acompanhante não pode ser a mesma pessoa do paciente titular.");
                }
            }
        }

        if (appointmentId != null && (appointmentId.startsWith("IMG-") || appointmentId.startsWith("INOV-"))) {
            LocalDate date = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
            LocalTime openingTime = LocalTime.of(6, 0);
            LocalTime closingTime = LocalTime.of(23, 59);
            String docName = (existingCreds != null ? existingCreds : List.<AccessCredential>of()).stream()
                    .filter(c -> c != null && c.getDoctorName() != null && !c.getDoctorName().isBlank())
                    .map(c -> c.getDoctorName())
                    .findFirst()
                    .orElse(null);
            return registerCompanionAccess(companion, date, openingTime, closingTime, null, null, appointmentId, null, docName);
        }

        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        if (accessInfoOpt.isEmpty()) {
            log.warn("[RegisterCompanion] Agendamento {} não encontrado para cadastrar acompanhante.", appointmentId);
            throw new NotFoundException("Agendamento não encontrado.");
        }
        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();

        LocalDate resolvedDate = accessWindowCalculator.resolveAppointmentDate(accessInfo.appointmentDate());
        LocalTime openingTime = LocalTime.of(6, 0);
        LocalTime closingTime = LocalTime.of(23, 0);

        return registerCompanionAccess(companion, resolvedDate, openingTime, closingTime, null, null, appointmentId, accessInfo.doctorId(), accessInfo.doctorName());
    }

    /**
     * Efetua o cadastro de acompanhante na GerAcesso e persiste a credencial localmente.
     */
    public AccessCredential registerCompanionAccess(
            CompanionAccessInfo companion,
            LocalDate date,
            LocalTime openingTime,
            LocalTime closingTime,
            String patientToken,
            String patientLocator,
            String appointmentId,
            String docId,
            String doctorName) {

        String companionCpf = CpfValidator.cleanCpf(companion.cpf());
        String startVisit = LocalDateTime.of(date, openingTime).format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);
        String endVisit = LocalDateTime.of(date, closingTime).format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);

        String matricula = "";
        String doctorCpf = "";
        if (docId != null && !docId.isBlank()) {
            DoctorAccessData docData = doctorAccessResolver.resolveDoctorAccessData(docId);
            matricula = docData.matricula();
            doctorCpf = docData.cpf();
        } else if (doctorName != null && !doctorName.isBlank()) {
            DoctorAccessData docData = doctorAccessResolver.resolveDoctorAccessDataByName(doctorName);
            matricula = docData.matricula();
            doctorCpf = docData.cpf();
        }

        GerAcessoRequest request = GerAcessoRequest.builder()
            .cpf(companionCpf)
            .status(1)
            .startVisit(startVisit)
            .endVisit(endVisit)
            .name(companion.name())
            .phone(companion.phone() != null ? companion.phone().replaceAll("\\D", "") : "")
            .visitType(1)
            .visitedRegistration(matricula)
            .visitedCpf(doctorCpf)
            .build();

        String token = null;
        String locator = null;

        try {
            Optional<GerAcessoResponse> responseOpt = gerAcessoClientPort.registerAccess(request);
            if (responseOpt.isPresent() 
                    && responseOpt.get().credential() != null 
                    && !responseOpt.get().credential().isBlank()
                    && !"null".equalsIgnoreCase(responseOpt.get().credential().trim())) {
                token = responseOpt.get().credential().trim();
                locator = responseOpt.get().locator() != null ? responseOpt.get().locator().trim() : "";
                log.info("[RegisterCompanion] Acompanhante cadastrado na GerAcesso: Nome={}, Credencial={}, Locator={}", 
                        companion.name(), token, locator);
            } else {
                log.warn("[RegisterCompanion] GerAcesso retornou resposta sem credencial válida para acompanhante {}. Ativando credencial contingencial.", companion.name());
            }
        } catch (Exception ex) {
            log.error("[RegisterCompanion] Erro ao cadastrar acompanhante na GerAcesso. Causa: {}", ex.getMessage(), ex);
        }

        if (token == null || token.isBlank()) {
            token = "CRED-COMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            locator = (patientLocator != null && !patientLocator.isBlank()) 
                    ? patientLocator 
                    : (patientToken != null && !patientToken.isBlank() ? patientToken : "LOC-" + System.currentTimeMillis());
            log.warn("[RegisterCompanion] Gerada credencial contingencial para acompanhante: Nome={}, Token={}", companion.name(), token);
        }

        // Verifica se já existe um registro salvo no banco para este acompanhante no agendamento
        Optional<AccessCredential> existingCredOpt = accessCredentialRepositoryPort
            .findByAppointmentId(appointmentId).stream()
            .filter(c -> c.getName() != null && c.getName().trim().equalsIgnoreCase(companion.name().trim()) && c.getUserType() == UserType.COMPANION)
            .findFirst();

        String cleanCompPhone = companion.phone() != null ? companion.phone().replaceAll("\\D", "") : null;
        if (cleanCompPhone != null && cleanCompPhone.isBlank()) cleanCompPhone = null;

        AccessCredential cred;
        if (existingCredOpt.isPresent()) {
            cred = existingCredOpt.get();
            cred.setCpf(companionCpf.isBlank() ? null : companionCpf);
            if (cleanCompPhone != null) cred.setPhone(cleanCompPhone);
            if (doctorName != null && !doctorName.isBlank()) cred.setDoctorName(doctorName);
            cred.setAccessCredential(token);
            cred.setLocator(locator);
            cred.setCreatedAt(LocalDateTime.now(AccessWindowCalculator.CLINIC_ZONE));
        } else {
            cred = AccessCredential.builder()
                .appointmentId(appointmentId)
                .name(companion.name().trim())
                .cpf(companionCpf.isBlank() ? null : companionCpf)
                .phone(cleanCompPhone)
                .doctorName(doctorName)
                .userType(UserType.COMPANION)
                .accessCredential(token)
                .locator(locator)
                .createdAt(LocalDateTime.now(AccessWindowCalculator.CLINIC_ZONE))
                .build();
        }

        AccessCredential saved = accessCredentialRepositoryPort.save(cred);
        log.info("[RegisterCompanion] Credencial de acompanhante salva com sucesso. ID: {}, Nome: {}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Executa o cadastro concorrente de múltiplos acompanhantes utilizando Virtual Threads (Java 21).
     */
    public void registerCompanionsParallel(
            List<CompanionAccessInfo> companions,
            FeegowPatientAccessInfo accessInfo,
            String pCleanCpf,
            LocalDate appointmentDate,
            LocalTime physicalOpeningTime,
            LocalTime closingTime,
            String finalToken,
            String finalLocator,
            List<AccessCredential> existingAppCreds) {

        if (companions == null || companions.isEmpty()) {
            return;
        }

        // Deduplica acompanhantes para garantir que duas threads nunca processem o mesmo acompanhante em paralelo
        Map<String, CompanionAccessInfo> uniqueCompanions = new LinkedHashMap<>();
        for (var comp : companions) {
            if (comp == null || comp.name() == null || comp.name().isBlank()) continue;
            String cleanCpf = CpfValidator.cleanCpf(comp.cpf());
            String key = !cleanCpf.isBlank() ? cleanCpf : comp.name().trim().toLowerCase();
            uniqueCompanions.putIfAbsent(key, comp);
        }
        List<CompanionAccessInfo> sanitizedCompanions = new ArrayList<>(uniqueCompanions.values());

        log.info("[RegisterCompanion] Iniciando cadastro paralelo de {} acompanhante(s) via Virtual Threads...", sanitizedCompanions.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = sanitizedCompanions.stream()
                .map(companion -> executor.submit(() -> {
                    try {
                        String compCleanCpf = CpfValidator.cleanCpf(companion.cpf());

                        if (!compCleanCpf.isBlank() && !pCleanCpf.isBlank() && compCleanCpf.equals(pCleanCpf)) {
                            log.warn("[RegisterCompanion] Acompanhante '{}' possui o mesmo CPF do titular ({}). Ignorando.", companion.name(), compCleanCpf);
                            return (Void) null;
                        }
                        if (accessInfo.name() != null && companion.name() != null 
                                && companion.name().trim().equalsIgnoreCase(accessInfo.name().trim())) {
                            log.warn("[RegisterCompanion] Acompanhante '{}' possui o mesmo nome do titular. Ignorando.", companion.name());
                            return (Void) null;
                        }

                        boolean alreadyRegisteredWithRealCred = existingAppCreds.stream()
                            .anyMatch(c -> (c.getName().equalsIgnoreCase(companion.name().trim())
                                            || (!compCleanCpf.isBlank() && c.getCpf() != null && CpfValidator.cleanCpf(c.getCpf()).equals(compCleanCpf)))
                                        && c.getUserType() == UserType.COMPANION
                                        && !c.getAccessCredential().startsWith("CRED-"));
                        if (alreadyRegisteredWithRealCred) {
                            log.info("[RegisterCompanion] Acompanhante '{}' já possui credencial GerAcesso real cadastrada. Ignorando duplicata.", companion.name());
                        } else {
                            registerCompanionAccess(companion, appointmentDate, physicalOpeningTime, closingTime, finalToken, finalLocator, accessInfo.appointmentId(), accessInfo.doctorId(), accessInfo.doctorName());
                        }
                    } catch (Exception ex) {
                        log.error("[RegisterCompanion] Erro fatal no processamento assíncrono do acompanhante '{}': {}", 
                                companion.name(), ex.getMessage(), ex);
                    }
                    return (Void) null;
                }))
                .toList();

            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.error("[RegisterCompanion] Falha ao recuperar resultado da Virtual Thread do acompanhante", e);
                }
            }
        }
        log.info("[RegisterCompanion] Processamento assíncrono de acompanhantes finalizado.");
    }

    /**
     * Processa a atualização dedicada de CPF de um acompanhante já cadastrado.
     */
    public AccessValidationResult processCompanionCpfUpdate(String appointmentId, String requestCpf, UUID credentialId, String targetName) {
        String cleanCpf = CpfValidator.cleanCpf(requestCpf);
        if (!CpfValidator.isValidCpf(cleanCpf)) {
            return new AccessValidationResult(false, null, null, true, "Por favor, informe um CPF válido para o acompanhante.");
        }

        List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
        if (creds == null || creds.isEmpty()) {
            return new AccessValidationResult(false, null, null, false, "Nenhuma credencial encontrada para o agendamento informado.");
        }

        AccessCredential targetCred = null;
        if (credentialId != null) {
            targetCred = creds.stream()
                .filter(c -> c.getId() != null && c.getId().equals(credentialId))
                .findFirst()
                .orElse(null);
        }
        if (targetCred == null && targetName != null && !targetName.isBlank()) {
            String q = targetName.trim().toLowerCase();
            targetCred = creds.stream()
                .filter(c -> c.getUserType() == UserType.COMPANION && c.getName() != null && c.getName().toLowerCase().contains(q))
                .findFirst()
                .orElse(null);
        }
        if (targetCred == null) {
            targetCred = creds.stream()
                .filter(c -> c.getUserType() == UserType.COMPANION)
                .findFirst()
                .orElse(null);
        }

        if (targetCred == null) {
            return new AccessValidationResult(false, null, null, false, "Acompanhante não localizado para atualização de CPF.");
        }

        targetCred.setCpf(cleanCpf);
        accessCredentialRepositoryPort.save(targetCred);
        log.info("[RegisterCompanion] CPF do acompanhante '{}' atualizado para: {}", targetCred.getName(), cleanCpf);

        // Se a credencial ainda for contingencial, tenta registrar na GerAcesso
        if (targetCred.getAccessCredential() != null && targetCred.getAccessCredential().startsWith("CRED-")) {
            try {
                LocalDate date = targetCred.getCreatedAt() != null ? targetCred.getCreatedAt().toLocalDate() : LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
                LocalTime openingTime = LocalTime.of(6, 0);
                LocalTime closingTime = LocalTime.of(23, 0);
                String startVisit = LocalDateTime.of(date, openingTime).format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);
                String endVisit = LocalDateTime.of(date, closingTime).format(AccessWindowCalculator.GERACESSO_DATE_FORMATTER);

                DoctorAccessData docData = doctorAccessResolver.resolveDoctorAccessDataByName(targetCred.getDoctorName());

                GerAcessoRequest req = GerAcessoRequest.builder()
                    .cpf(cleanCpf)
                    .status(1)
                    .startVisit(startVisit)
                    .endVisit(endVisit)
                    .name(targetCred.getName())
                    .phone(targetCred.getPhone() != null ? targetCred.getPhone() : "")
                    .visitType(1)
                    .visitedRegistration(docData.matricula())
                    .visitedCpf(docData.cpf())
                    .build();

                Optional<GerAcessoResponse> resp = gerAcessoClientPort.registerAccess(req);
                if (resp.isPresent() && resp.get().credential() != null && !resp.get().credential().isBlank()) {
                    targetCred.setAccessCredential(resp.get().credential());
                    if (resp.get().locator() != null && !resp.get().locator().isBlank()) {
                        targetCred.setLocator(resp.get().locator());
                    }
                    accessCredentialRepositoryPort.save(targetCred);
                    log.info("[RegisterCompanion] Acompanhante atualizado no GerAcesso com credencial definitiva: {}", resp.get().credential());
                }
            } catch (Exception ex) {
                log.warn("[RegisterCompanion] Falha ao sincronizar CPF do acompanhante com o GerAcesso: {}", ex.getMessage());
            }
        }

        return new AccessValidationResult(true, targetCred.getName(), targetCred.getAccessCredential(), false, "CPF do acompanhante atualizado com sucesso.");
    }
}
