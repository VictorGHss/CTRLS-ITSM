package br.dev.ctrls.inovareti.modules.access.domain.service;

import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.InvalidChallengeException;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessServiceMagicTokenTest {

    @Mock
    private AccessCredentialRepositoryPort accessCredentialRepositoryPort;

    @Mock
    private FeegowClientPort feegowClientPort;

    @Mock
    private GerAcessoClientPort gerAcessoClientPort;

    @Mock
    private AppointmentExternalPort appointmentExternalPort;

    @Mock
    private PatientExternalPort patientExternalPort;

    @Mock
    private DoctorConfigurationRepository doctorConfigurationRepository;

    private AccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new AccessService(
                feegowClientPort,
                appointmentExternalPort,
                patientExternalPort,
                accessCredentialRepositoryPort,
                gerAcessoClientPort,
                doctorConfigurationRepository
        );
    }

    @Test
    @DisplayName("Deveria gerar token determinístico e seguro para agendamento e telefone")
    void shouldGenerateDeterministicMagicToken() {
        String appointmentId = "3470556";
        String phone = "(42) 99915-3868";

        String token1 = accessService.generateAccessToken(appointmentId, phone);
        String token2 = accessService.generateAccessToken(appointmentId, "5542999153868");

        assertThat(token1).isNotBlank();
        assertThat(token1).isEqualTo(token2);
        assertThat(token1).hasSize(16);
    }

    @Test
    @DisplayName("Deveria validar acesso com sucesso usando o Magic Token sem exigir 4 dígitos do telefone")
    void shouldValidateAccessUsingMagicTokenSuccessfully() {
        String appointmentId = "3470556";
        String phone = "(42) 99915-3868";
        String validToken = accessService.generateAccessToken(appointmentId, phone);

        FeegowPatientAccessInfo accessInfo = new FeegowPatientAccessInfo(
                appointmentId,
                "310408",
                "CACILDA BORGES DE RAMOS",
                "79512046920",
                LocalDate.now(),
                LocalTime.of(14, 0),
                "10",
                "Dr. Teste",
                phone
        );

        when(feegowClientPort.fetchPatientAccessInfo(appointmentId)).thenReturn(Optional.of(accessInfo));

        // Validação usando apenas o token (phoneDigits = null)
        FeegowPatientAccessInfo result = accessService.validateAccessChallenge(appointmentId, null, validToken);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("CACILDA BORGES DE RAMOS");
    }

    @Test
    @DisplayName("Deveria lançar exceção se o Magic Token for inválido e nenhum dígito for fornecido")
    void shouldRejectInvalidMagicToken() {
        String appointmentId = "3470556";
        String phone = "(42) 99915-3868";

        FeegowPatientAccessInfo accessInfo = new FeegowPatientAccessInfo(
                appointmentId,
                "310408",
                "CACILDA BORGES DE RAMOS",
                "79512046920",
                LocalDate.now(),
                LocalTime.of(14, 0),
                "10",
                "Dr. Teste",
                phone
        );

        when(feegowClientPort.fetchPatientAccessInfo(appointmentId)).thenReturn(Optional.of(accessInfo));

        assertThatThrownBy(() -> accessService.validateAccessChallenge(appointmentId, null, "token_falso_1234"))
                .isInstanceOf(InvalidChallengeException.class);
    }

    @Test
    @DisplayName("Deveria aceitar fallback para 4 dígitos do telefone se o token não for enviado")
    void shouldFallbackToPhoneDigitsWhenTokenNotProvided() {
        String appointmentId = "3470556";
        String phone = "(42) 99915-3868";

        FeegowPatientAccessInfo accessInfo = new FeegowPatientAccessInfo(
                appointmentId,
                "310408",
                "CACILDA BORGES DE RAMOS",
                "79512046920",
                LocalDate.now(),
                LocalTime.of(14, 0),
                "10",
                "Dr. Teste",
                phone
        );

        when(feegowClientPort.fetchPatientAccessInfo(appointmentId)).thenReturn(Optional.of(accessInfo));

        FeegowPatientAccessInfo result = accessService.validateAccessChallenge(appointmentId, "3868", null);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("CACILDA BORGES DE RAMOS");
    }
}
