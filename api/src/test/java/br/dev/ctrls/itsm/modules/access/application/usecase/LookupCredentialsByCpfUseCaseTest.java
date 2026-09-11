package br.dev.ctrls.itsm.modules.access.application.usecase;

import br.dev.ctrls.itsm.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.itsm.modules.access.domain.model.UserType;
import br.dev.ctrls.itsm.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.itsm.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.itsm.modules.access.infrastructure.adapter.input.dto.AccessCredentialResponse;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.PatientExternalPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LookupCredentialsByCpfUseCaseTest {

    @Mock
    private AccessCredentialRepositoryPort accessCredentialRepositoryPort;

    @Mock
    private PatientExternalPort patientExternalPort;

    @Mock
    private AppointmentExternalPort appointmentExternalPort;

    @Mock
    private ProcessAccessRequestUseCase processAccessRequestUseCase;

    @Mock
    private FeegowClientPort feegowClientPort;

    @Test
    @DisplayName("Deveria executar o caso de uso completo retornando AccessCredentialResponse formatado")
    void shouldExecuteAndReturnAccessCredentialResponseSuccessfully() {
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

        var useCase = new LookupCredentialsByCpfUseCase(
                accessCredentialRepositoryPort,
                patientExternalPort,
                appointmentExternalPort,
                processAccessRequestUseCase,
                feegowClientPort
        );

        List<AccessCredentialResponse> responses = useCase.execute(cpf, "portal");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().appointmentId()).isEqualTo(autoApptId);
        assertThat(responses.getFirst().name()).isEqualTo("VICTOR HASS");
        assertThat(responses.getFirst().doctorName()).isEqualTo("Portal de Atendimento");
    }
}
