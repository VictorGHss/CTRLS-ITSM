package br.dev.ctrls.inovareti.modules.access.application.usecase;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.DoctorAccessData;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.DoctorAccessResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactivateAccessUseCaseTest {

    @Mock
    private AccessCredentialRepositoryPort accessCredentialRepositoryPort;

    @Mock
    private FeegowClientPort feegowClientPort;

    @Mock
    private GerAcessoClientPort gerAcessoClientPort;

    @Mock
    private DoctorAccessResolver doctorAccessResolver;

    private ReactivateAccessUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReactivateAccessUseCase(
                accessCredentialRepositoryPort,
                feegowClientPort,
                gerAcessoClientPort,
                doctorAccessResolver
        );
    }

    @Test
    @DisplayName("Deveria reativar acesso na GerAcesso com nova credencial usando janela imediata")
    void shouldReactivateAccessWithNewCredentialSuccessfully() {
        String appointmentId = "3470777";
        String cpf = "13821025930";

        AccessCredential cred = AccessCredential.builder()
                .id(UUID.randomUUID())
                .appointmentId(appointmentId)
                .name("MARIA TESTE")
                .cpf(cpf)
                .userType(UserType.PATIENT)
                .accessCredential("000011112222")
                .locator("LOC-OLD")
                .doctorName("DR CARLOS")
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();

        when(accessCredentialRepositoryPort.findByAppointmentId(appointmentId)).thenReturn(List.of(cred));
        when(doctorAccessResolver.resolveDoctorAccessDataByName("DR CARLOS"))
                .thenReturn(new DoctorAccessData("10", "12345678900"));
        when(gerAcessoClientPort.registerAccess(any(GerAcessoRequest.class)))
                .thenReturn(Optional.of(new GerAcessoResponse("1", "OK", 3470777L, "1", 100L, "LOC-NEW", "000099998888")));
        when(accessCredentialRepositoryPort.save(any(AccessCredential.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<AccessCredential> result = useCase.reactivateAccess(appointmentId);

        assertThat(result).hasSize(1);
        AccessCredential updated = result.getFirst();
        assertThat(updated.getAccessCredential()).isEqualTo("000099998888");
        assertThat(updated.getLocator()).isEqualTo("LOC-NEW");

        ArgumentCaptor<GerAcessoRequest> captor = ArgumentCaptor.forClass(GerAcessoRequest.class);
        verify(gerAcessoClientPort).registerAccess(captor.capture());
        GerAcessoRequest sentRequest = captor.getValue();
        assertThat(sentRequest.cpf()).isEqualTo(cpf);
        assertThat(sentRequest.visitedRegistration()).isEqualTo("10");
        assertThat(sentRequest.visitedCpf()).isEqualTo("12345678900");
    }

    @Test
    @DisplayName("Deveria localizar agendamento via CPF caso appointmentId não encontre registros diretos")
    void shouldFallbackToCpfWhenAppointmentIdNotFound() {
        String cpf = "13821025930";
        String publicApptId = "IMG-20260909-13821025930";

        AccessCredential cred = AccessCredential.builder()
                .id(UUID.randomUUID())
                .appointmentId(publicApptId)
                .name("MARIA TESTE")
                .cpf(cpf)
                .userType(UserType.PATIENT)
                .accessCredential("000011112222")
                .locator("LOC-OLD")
                .createdAt(LocalDateTime.now())
                .build();

        when(accessCredentialRepositoryPort.findByAppointmentId(cpf)).thenReturn(List.of());
        when(accessCredentialRepositoryPort.findByCpf(cpf)).thenReturn(List.of(cred));
        when(gerAcessoClientPort.registerAccess(any(GerAcessoRequest.class)))
                .thenReturn(Optional.of(new GerAcessoResponse("1", "OK", null, "1", null, "LOC-NEW", "000055554444")));
        when(accessCredentialRepositoryPort.save(any(AccessCredential.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<AccessCredential> result = useCase.reactivateAccess(cpf);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getAccessCredential()).isEqualTo("000055554444");
    }

    @Test
    @DisplayName("Deveria lançar NotFoundException quando nenhum registro for localizado")
    void shouldThrowNotFoundExceptionWhenNoCredentialsFound() {
        when(accessCredentialRepositoryPort.findByAppointmentId("999999")).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.reactivateAccess("999999"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Nenhuma credencial encontrada para reativação");
    }
}
