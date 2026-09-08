package br.dev.ctrls.inovareti.modules.access.application.usecase;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.InvalidChallengeException;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Caso de Uso: Validação de Desafio de Segurança e Geração de Magic Token.
 * Gerencia autenticação por link criptográfico HMAC-SHA256 (Magic Link) ou confirmação dos 4 últimos dígitos do telefone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateAccessChallengeUseCase {

    private static final String MAGIC_TOKEN_SECRET = "inovare_magic_access_token_secret_key_2026";

    private final FeegowClientPort feegowClientPort;
    private final PatientExternalPort patientExternalPort;
    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;

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
            log.warn("[ValidateAccessChallenge] Erro ao buscar prontuário do paciente ID: {} para cache. Causa: {}", patientId, ex.getMessage());
            return null;
        }
    }

    /**
     * Gera um token determinístico e criptograficamente seguro (HMAC-SHA256) para o agendamento e telefone.
     */
    public String generateAccessToken(String appointmentId, String phone) {
        if (appointmentId == null || appointmentId.isBlank()) {
            return "";
        }
        try {
            String cleanPhone = phone != null ? phone.replaceAll("\\D", "") : "";
            if (cleanPhone.startsWith("55") && (cleanPhone.length() == 12 || cleanPhone.length() == 13)) {
                cleanPhone = cleanPhone.substring(2);
            }
            String raw = appointmentId + ":" + cleanPhone;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(MAGIC_TOKEN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("[ValidateAccessChallenge] Erro ao gerar token de acesso HMAC", ex);
            return "";
        }
    }

    /**
     * Valida o desafio de acesso seguro utilizando Magic Token (HMAC) ou os 4 dígitos do telefone.
     */
    public FeegowPatientAccessInfo validateAccessChallenge(String appointmentId, String phoneDigits, String token) {
        log.info("[ValidateAccessChallenge] Validando credenciais para o agendamento ID: {} (token={})", 
                appointmentId, token != null && !token.isBlank() ? "presente" : "ausente");

        // Tratamento para auto-check-in da Clínica da Imagem ou Inovare
        if (appointmentId != null && (appointmentId.startsWith("IMG-") || appointmentId.startsWith("INOV-"))) {
            List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(appointmentId);
            if (creds == null || creds.isEmpty()) {
                throw new NotFoundException("Credencial de acesso não encontrada.");
            }
            AccessCredential patient = creds.stream()
                .filter(c -> c.getUserType() == UserType.PATIENT)
                .findFirst()
                .orElse(creds.getFirst());

            LocalDate appDate = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
            if (appointmentId.length() >= 13) {
                try {
                    String datePart = appointmentId.substring(5, 13);
                    appDate = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
                } catch (Exception ignored) {}
            }

            String docName = (patient.getDoctorName() != null && !patient.getDoctorName().isBlank())
                    ? patient.getDoctorName()
                    : (appointmentId.startsWith("INOV-") ? "Inovare – Serviços de Saúde" : "Clínica Da Imagem - Unidade Inovare");

            return new FeegowPatientAccessInfo(
                patient.getAppointmentId(),
                patient.getLocator(),
                patient.getName(),
                patient.getCpf(),
                appDate,
                LocalTime.of(8, 0),
                null,
                docName,
                ""
            );
        }

        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        if (accessInfoOpt.isEmpty()) {
            log.warn("[ValidateAccessChallenge] Agendamento {} não encontrado para validação do desafio.", appointmentId);
            throw new NotFoundException("Agendamento não encontrado.");
        }

        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();
        FeegowPatient mainPatient = getPatientInfoWithCache(accessInfo.patientId());
        String phone = mainPatient != null && mainPatient.phone() != null && !mainPatient.phone().isBlank()
                ? mainPatient.phone()
                : accessInfo.phone();

        // 1. Validação prioritária por Magic Token criptográfico
        if (token != null && !token.isBlank()) {
            String trimmedToken = token.trim();
            String expectedToken1 = generateAccessToken(appointmentId, phone);
            String expectedToken2 = generateAccessToken(appointmentId, accessInfo.phone());
            String expectedToken3 = generateAccessToken(appointmentId, "");

            if (expectedToken1.equalsIgnoreCase(trimmedToken) 
                    || expectedToken2.equalsIgnoreCase(trimmedToken) 
                    || expectedToken3.equalsIgnoreCase(trimmedToken)) {
                log.info("[ValidateAccessChallenge] Magic Token criptográfico validado com sucesso para o agendamento {}", appointmentId);
                return accessInfo;
            }
            log.warn("[ValidateAccessChallenge] Token de acesso inválido para agendamento {}. Esperado: {}, Recebido: {}", 
                    appointmentId, expectedToken1, token);
        }

        // 2. Validação por 4 dígitos do telefone (fallback tradicional)
        if (phoneDigits != null && !phoneDigits.isBlank()) {
            return validatePhoneChallenge(appointmentId, phoneDigits);
        }

        throw new InvalidChallengeException("Identificação necessária. Por favor, utilize o link recebido no WhatsApp.");
    }

    /**
     * Valida os 4 últimos dígitos do telefone do paciente cadastrado no prontuário.
     */
    public FeegowPatientAccessInfo validatePhoneChallenge(String appointmentId, String phoneDigits) {
        log.info("[ValidateAccessChallenge] Validando desafio de telefone para o agendamento ID: {}", appointmentId);
        
        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        if (accessInfoOpt.isEmpty()) {
            log.warn("[ValidateAccessChallenge] Agendamento {} não encontrado para validação do desafio.", appointmentId);
            throw new NotFoundException("Agendamento não encontrado.");
        }

        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();
        FeegowPatient mainPatient = getPatientInfoWithCache(accessInfo.patientId());
        
        String phone = mainPatient != null && mainPatient.phone() != null && !mainPatient.phone().isBlank() 
                ? mainPatient.phone() 
                : accessInfo.phone();
        if (phone == null || phone.isBlank()) {
            log.warn("[ValidateAccessChallenge] Paciente ID {} não possui telefone cadastrado no prontuário.", accessInfo.patientId());
            throw new InvalidChallengeException("Não há telefone cadastrado no prontuário do paciente.");
        }

        String cleanPhone = phone.replaceAll("\\D", "");
        if (cleanPhone.length() < 4) {
            log.warn("[ValidateAccessChallenge] Telefone cadastrado '{}' (limpo: '{}') possui menos de 4 dígitos.", phone, cleanPhone);
            throw new InvalidChallengeException("Telefone inválido cadastrado no prontuário.");
        }

        String lastFourDigits = cleanPhone.substring(cleanPhone.length() - 4);
        String cleanInputDigits = phoneDigits != null ? phoneDigits.replaceAll("\\D", "") : "";

        if (!lastFourDigits.equals(cleanInputDigits)) {
            log.warn("[ValidateAccessChallenge] Falha no desafio de segurança para agendamento {}. Esperado: {}, Recebido: {}", 
                    appointmentId, lastFourDigits, cleanInputDigits);
            throw new InvalidChallengeException("Os dígitos informados estão incorretos. Por favor, tente novamente.");
        }
        
        log.info("[ValidateAccessChallenge] Desafio de segurança validado com sucesso para agendamento {}", appointmentId);
        return accessInfo;
    }
}
