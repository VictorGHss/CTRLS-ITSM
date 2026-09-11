package br.dev.ctrls.itsm.modules.access.application.usecase;

import br.dev.ctrls.itsm.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.itsm.modules.access.domain.model.InvalidChallengeException;
import br.dev.ctrls.itsm.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.itsm.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.itsm.modules.access.infrastructure.security.AccessSecurityGuard;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.PatientExternalPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidateAccessChallengeUseCaseTest {

    private FeegowClientPort feegowClientPort;
    private PatientExternalPort patientExternalPort;
    private AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private AccessSecurityGuard securityGuard;
    private ValidateAccessChallengeUseCase useCase;

    @BeforeEach
    void setUp() {
        feegowClientPort = Mockito.mock(FeegowClientPort.class);
        patientExternalPort = Mockito.mock(PatientExternalPort.class);
        accessCredentialRepositoryPort = Mockito.mock(AccessCredentialRepositoryPort.class);
        securityGuard = new AccessSecurityGuard();

        useCase = new ValidateAccessChallengeUseCase(
                feegowClientPort,
                patientExternalPort,
                accessCredentialRepositoryPort,
                securityGuard
        );
    }

    @Test
    @DisplayName("Deve gerar Magic Token determinístico de 16 caracteres hexadecimais")
    void shouldGenerateDeterministicMagicToken() {
        String token1 = useCase.generateAccessToken("12345", "42999998888");
        String token2 = useCase.generateAccessToken("12345", "5542999998888");

        assertNotNull(token1);
        assertEquals(16, token1.length(), "Token deve ter 16 caracteres hexadecimais (8 bytes)");
        assertEquals(token1, token2, "Token deve normalizar o DDI 55 e gerar o mesmo hash");
    }

    @Test
    @DisplayName("Deve validar Magic Token criptográfico com sucesso")
    void shouldValidateWithMagicTokenSuccessfully() {
        String appointmentId = "1001";
        String phone = "42999991234";
        FeegowPatientAccessInfo patientInfo = new FeegowPatientAccessInfo(
                appointmentId, "10", "João Silva", "12345678901",
                LocalDate.now(), LocalTime.of(10, 0), "1", "Dr. Teste", phone
        );

        when(feegowClientPort.fetchPatientAccessInfo(appointmentId)).thenReturn(Optional.of(patientInfo));
        when(patientExternalPort.patientInfo("10")).thenReturn(new FeegowPatient("10", "João Silva", phone, "12345678901", "1990-01-01"));

        String validToken = useCase.generateAccessToken(appointmentId, phone);

        FeegowPatientAccessInfo result = useCase.validateAccessChallenge(appointmentId, null, validToken);
        assertNotNull(result);
        assertEquals("João Silva", result.name());
    }

    @Test
    @DisplayName("Deve bloquear o agendamento na 5ª tentativa incorreta do telefone e impedir novas tentativas")
    void shouldLockoutAfterFiveIncorrectPhoneAttempts() {
        String appointmentId = "2002";
        String phone = "42999998765"; // últimos 4: 8765
        FeegowPatientAccessInfo patientInfo = new FeegowPatientAccessInfo(
                appointmentId, "20", "Maria Santos", "98765432100",
                LocalDate.now(), LocalTime.of(11, 0), "2", "Dra. Teste", phone
        );

        when(feegowClientPort.fetchPatientAccessInfo(appointmentId)).thenReturn(Optional.of(patientInfo));
        when(patientExternalPort.patientInfo("20")).thenReturn(new FeegowPatient("20", "Maria Santos", phone, "98765432100", "1985-05-15"));

        // Tentativas 1 a 4 com dígitos errados (ex: 0000)
        for (int i = 1; i <= 4; i++) {
            final int attempt = i;
            InvalidChallengeException ex = assertThrows(InvalidChallengeException.class, () ->
                    useCase.validatePhoneChallenge(appointmentId, "0000")
            );
            int remaining = 5 - attempt;
            assertTrue(ex.getMessage().contains("mais " + remaining + " tentativa(s)"));
        }

        // 5ª tentativa errada ativa o bloqueio
        InvalidChallengeException lockEx = assertThrows(InvalidChallengeException.class, () ->
                useCase.validatePhoneChallenge(appointmentId, "0000")
        );
        assertTrue(lockEx.getMessage().contains("bloqueado por 15 minutos"));

        // Tentativa subsequente (mesmo com dígitos corretos) deve ser rejeitada imediatamente pelo bloqueio
        InvalidChallengeException blockedEx = assertThrows(InvalidChallengeException.class, () ->
                useCase.validateAccessChallenge(appointmentId, "8765", null)
        );
        assertTrue(blockedEx.getMessage().contains("bloqueado por 15 minutos"));
    }
}
