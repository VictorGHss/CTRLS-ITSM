package br.dev.ctrls.inovareti.modules.access.domain.service;

import br.dev.ctrls.inovareti.modules.access.application.usecase.LookupCredentialsByCpfUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.ProcessAccessRequestUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.ReactivateAccessUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.RegisterCompanionUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.SelfRegistrationUseCase;
import br.dev.ctrls.inovareti.modules.access.application.usecase.ValidateAccessChallengeUseCase;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessValidationResult;
import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.CpfValidator;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Serviço de domínio e Fachada de Alto Nível: AccessService.
 * Coordena os Casos de Uso especializados de controle de acesso físico às catracas,
 * mantendo 100% de retrocompatibilidade com todos os chamadores da aplicação.
 * Comentários em PT-BR conforme as Regras de Ouro.
 */
@Slf4j
@Service
public class AccessService {

    private final ProcessAccessRequestUseCase processAccessRequestUseCase;
    private final RegisterCompanionUseCase registerCompanionUseCase;
    private final ValidateAccessChallengeUseCase validateAccessChallengeUseCase;
    private final SelfRegistrationUseCase selfRegistrationUseCase;
    private final LookupCredentialsByCpfUseCase lookupCredentialsByCpfUseCase;
    private final ReactivateAccessUseCase reactivateAccessUseCase;

    /**
     * Construtor principal para injeção de dependências do Spring Boot via Casos de Uso.
     */
    @Autowired
    public AccessService(
            ProcessAccessRequestUseCase processAccessRequestUseCase,
            RegisterCompanionUseCase registerCompanionUseCase,
            ValidateAccessChallengeUseCase validateAccessChallengeUseCase,
            SelfRegistrationUseCase selfRegistrationUseCase,
            LookupCredentialsByCpfUseCase lookupCredentialsByCpfUseCase,
            ReactivateAccessUseCase reactivateAccessUseCase) {
        this.processAccessRequestUseCase = processAccessRequestUseCase;
        this.registerCompanionUseCase = registerCompanionUseCase;
        this.validateAccessChallengeUseCase = validateAccessChallengeUseCase;
        this.selfRegistrationUseCase = selfRegistrationUseCase;
        this.lookupCredentialsByCpfUseCase = lookupCredentialsByCpfUseCase;
        this.reactivateAccessUseCase = reactivateAccessUseCase;
    }

    /**
     * Construtor de compatibilidade para testes unitários legados que instanciam AccessService com as 7 portas.
     */
    public AccessService(
            FeegowClientPort feegowClientPort,
            AppointmentExternalPort appointmentExternalPort,
            PatientExternalPort patientExternalPort,
            AccessCredentialRepositoryPort accessCredentialRepositoryPort,
            GerAcessoClientPort gerAcessoClientPort,
            DoctorConfigurationRepository doctorConfigurationRepository,
            AppointmentSessionRepositoryPort appointmentSessionRepository) {

        DoctorAccessResolver doctorAccessResolver = new DoctorAccessResolver(doctorConfigurationRepository);
        AccessWindowCalculator accessWindowCalculator = new AccessWindowCalculator();

        this.validateAccessChallengeUseCase = new ValidateAccessChallengeUseCase(
                feegowClientPort, patientExternalPort, accessCredentialRepositoryPort
        );
        this.registerCompanionUseCase = new RegisterCompanionUseCase(
                accessCredentialRepositoryPort, feegowClientPort, gerAcessoClientPort, doctorAccessResolver, accessWindowCalculator
        );
        this.selfRegistrationUseCase = new SelfRegistrationUseCase(
                accessCredentialRepositoryPort, gerAcessoClientPort, doctorAccessResolver, this.registerCompanionUseCase
        );
        this.processAccessRequestUseCase = new ProcessAccessRequestUseCase(
                feegowClientPort, appointmentExternalPort, patientExternalPort, accessCredentialRepositoryPort,
                gerAcessoClientPort, appointmentSessionRepository, doctorAccessResolver, accessWindowCalculator,
                this.validateAccessChallengeUseCase, this.registerCompanionUseCase, this.selfRegistrationUseCase
        );
        this.lookupCredentialsByCpfUseCase = new LookupCredentialsByCpfUseCase(
                accessCredentialRepositoryPort, patientExternalPort, appointmentExternalPort, this.processAccessRequestUseCase
        );
        this.reactivateAccessUseCase = new ReactivateAccessUseCase(
                accessCredentialRepositoryPort, feegowClientPort, gerAcessoClientPort, doctorAccessResolver
        );
    }

    /**
     * Processa a requisição de acesso para um determinado agendamento.
     */
    public AccessValidationResult processAccessRequest(String appointmentId, String requestCpf, List<CompanionAccessInfo> companions) {
        return processAccessRequestUseCase.execute(appointmentId, requestCpf, companions);
    }

    public AccessValidationResult processAccessRequest(
            String appointmentId,
            String requestCpf,
            List<CompanionAccessInfo> companions,
            UUID credentialId,
            String targetName,
            String userType) {
        return processAccessRequestUseCase.execute(appointmentId, requestCpf, companions, credentialId, targetName, userType);
    }

    /**
     * Cadastra um acompanhante isoladamente para um agendamento titular.
     */
    public AccessCredential registerCompanion(String appointmentId, CompanionAccessInfo companion) {
        return registerCompanionUseCase.registerCompanion(appointmentId, companion);
    }

    /**
     * Gera um token determinístico e criptograficamente seguro (HMAC-SHA256) para o agendamento e telefone.
     */
    public String generateAccessToken(String appointmentId, String phone) {
        return validateAccessChallengeUseCase.generateAccessToken(appointmentId, phone);
    }

    /**
     * Valida o desafio de acesso seguro utilizando Magic Token (HMAC) ou os 4 dígitos do telefone.
     */
    public FeegowPatientAccessInfo validateAccessChallenge(String appointmentId, String phoneDigits, String token) {
        return validateAccessChallengeUseCase.validateAccessChallenge(appointmentId, phoneDigits, token);
    }

    /**
     * Valida o desafio dos 4 últimos dígitos do telefone do paciente cadastrado no prontuário.
     */
    public FeegowPatientAccessInfo validatePhoneChallenge(String appointmentId, String phoneDigits) {
        return validateAccessChallengeUseCase.validatePhoneChallenge(appointmentId, phoneDigits);
    }

    /**
     * Valida se um CPF é matematicamente válido utilizando os dígitos verificadores do Módulo 11 da Receita Federal.
     */
    public static boolean isValidCpf(String rawCpf) {
        return CpfValidator.isValidCpf(rawCpf);
    }

    /**
     * Auto-cadastro público de pacientes e acompanhantes no quiosque / recepção.
     */
    public List<AccessCredential> processSelfRegistration(
            String name, String rawCpf, String phone, String birthDate, String clinic, CompanionAccessInfo companion) {
        return selfRegistrationUseCase.processSelfRegistration(name, rawCpf, phone, birthDate, clinic, companion);
    }

    public List<AccessCredential> processSelfRegistration(
            String name, String rawCpf, String phone, String birthDate, String clinic, List<CompanionAccessInfo> companions) {
        return selfRegistrationUseCase.processSelfRegistration(name, rawCpf, phone, birthDate, clinic, companions);
    }

    public List<AccessCredential> processSelfRegistration(
            String name, String rawCpf, String phone, String birthDate, String clinic,
            String visitDateStr, String doctorName, List<CompanionAccessInfo> companions) {
        return selfRegistrationUseCase.processSelfRegistration(name, rawCpf, phone, birthDate, clinic, visitDateStr, doctorName, companions);
    }

    /**
     * Consulta credenciais de acesso por CPF (incluindo auto-cadastros recentes e agendamentos futuros da Feegow).
     */
    public List<AccessCredential> lookupCredentialsByCpf(String rawCpf, String clinic) {
        return lookupCredentialsByCpfUseCase.lookupCredentialsByCpf(rawCpf, clinic);
    }

    /**
     * Reativa o acesso físico de um paciente ou acompanhante na GerAcesso.
     */
    public List<AccessCredential> reactivateAccess(String appointmentId) {
        return reactivateAccessUseCase.reactivateAccess(appointmentId);
    }
}
