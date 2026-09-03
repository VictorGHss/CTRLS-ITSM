package br.dev.ctrls.inovareti.modules.access.domain.service;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessServiceCpfLookupTest {

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

    @Mock
    private AppointmentSessionRepositoryPort appointmentSessionRepository;

    private AccessService accessService;

    @BeforeEach
    void setUp() {
        accessService = new AccessService(
                feegowClientPort,
                appointmentExternalPort,
                patientExternalPort,
                accessCredentialRepositoryPort,
                gerAcessoClientPort,
                doctorConfigurationRepository,
                appointmentSessionRepository
        );
    }

    @Test
    @DisplayName("Deveria retornar auto-cadastro ativo imediatamente em memória sem consultar API do Feegow")
    void shouldReturnAutoRegistrationImmediatelyWithoutFeegowCalls() {
        String cpf = "13821025930";
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String autoApptId = "INOV-" + todayStr + "-" + cpf;

        AccessCredential cred = AccessCredential.builder()
                .id(UUID.randomUUID())
                .appointmentId(autoApptId)
                .name("VICTOR HASS")
                .cpf(cpf)
                .userType(UserType.PATIENT)
                .accessCredential("00001234")
                .locator("LOC123")
                .createdAt(LocalDateTime.now())
                .build();

        when(accessCredentialRepositoryPort.findByCpf(cpf)).thenReturn(List.of(cred));
        when(accessCredentialRepositoryPort.findByAppointmentId(autoApptId)).thenReturn(List.of(cred));

        List<AccessCredential> result = accessService.lookupCredentialsByCpf(cpf, "inovare");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getAppointmentId()).isEqualTo(autoApptId);
        // Garante que NENHUMA chamada externa ao Feegow foi realizada
        verifyNoInteractions(patientExternalPort);
        verifyNoInteractions(appointmentExternalPort);
        verifyNoInteractions(feegowClientPort);
    }

    @Test
    @DisplayName("Deveria buscar agendamentos futuros em lote no Feegow e retornar credencial sem chamadas N+1 para histórico antigo")
    void shouldBatchQueryFeegowAndReturnCredentialWithoutNPlusOneCalls() {
        String cpf = "13821025930";
        String patientId = "289431";
        String upcomingApptId = "3479999";

        // Histórico de 5 agendamentos passados no banco local
        List<AccessCredential> oldHistory = List.of(
                createOldCredential("3441001", cpf),
                createOldCredential("3441002", cpf),
                createOldCredential("3441003", cpf),
                createOldCredential("3441004", cpf),
                createOldCredential("3441005", cpf)
        );

        when(accessCredentialRepositoryPort.findByCpf(cpf)).thenReturn(oldHistory);
        when(patientExternalPort.patientInfo(cpf)).thenReturn(new FeegowPatient(patientId, "VICTOR HASS", "42991617171", cpf, "2004-10-26"));

        // Retorna a consulta futura (de amanhã) na busca em lote
        FeegowAppointment upcoming = new FeegowAppointment(
                upcomingApptId,
                patientId,
                "10",
                "Dr. Teste",
                "Unidade Centro",
                LocalDateTime.now().plusDays(1),
                "1",
                "Consulta",
                "100",
                false
        );
        when(appointmentExternalPort.searchPatientAppointments(patientId)).thenReturn(List.of(upcoming));

        AccessCredential existingUpcomingCred = AccessCredential.builder()
                .id(UUID.randomUUID())
                .appointmentId(upcomingApptId)
                .name("VICTOR HASS")
                .cpf(cpf)
                .userType(UserType.PATIENT)
                .accessCredential("00009999")
                .locator("LOC999")
                .createdAt(LocalDateTime.now())
                .build();

        when(accessCredentialRepositoryPort.findByAppointmentId(upcomingApptId)).thenReturn(List.of(existingUpcomingCred));

        List<AccessCredential> result = accessService.lookupCredentialsByCpf(cpf, "imagem");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getAppointmentId()).isEqualTo(upcomingApptId);

        // Verifica que o Feegow foi consultado exatamente UMA vez para o paciente e UMA vez em lote para as consultas
        verify(patientExternalPort, times(1)).patientInfo(cpf);
        verify(appointmentExternalPort, times(1)).searchPatientAppointments(patientId);

        // E NENHUMA chamada 1-a-1 de fetchPatientAccessInfo foi disparada para os agendamentos antigos
        verifyNoInteractions(feegowClientPort);
    }

    private AccessCredential createOldCredential(String apptId, String cpf) {
        return AccessCredential.builder()
                .id(UUID.randomUUID())
                .appointmentId(apptId)
                .name("VICTOR HASS")
                .cpf(cpf)
                .userType(UserType.PATIENT)
                .accessCredential("CRED-" + apptId)
                .locator("LOC-" + apptId)
                .createdAt(LocalDateTime.now().minusMonths(2))
                .build();
    }
}
