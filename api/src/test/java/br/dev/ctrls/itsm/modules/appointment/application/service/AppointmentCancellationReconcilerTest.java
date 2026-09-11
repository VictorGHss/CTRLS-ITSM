package br.dev.ctrls.itsm.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.FeegowAppointment;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentCancellationReconcilerTest {

    @Mock
    private AppointmentSessionRepositoryPort appointmentSessionRepository;

    @Mock
    private AppointmentExternalPort appointmentExternalPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private AppointmentCancellationReconciler reconciler;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
    }

    @Test
    @DisplayName("reconcile: Invalida para CANCELED agendamento local que sumiu do Feegow e retornou desmarcado")
    void shouldCancelLocalSessionWhenFeegowCanceled() {
        LocalDate targetDate = LocalDate.of(2026, 8, 24);
        LocalDateTime appointmentTime = targetDate.atTime(10, 0);

        AppointmentSession localSession = AppointmentSession.builder()
                .id(UUID.randomUUID())
                .feegowAppointmentId("5001")
                .appointmentAt(appointmentTime)
                .status(AppointmentSessionStatus.PENDING)
                .build();

        when(appointmentSessionRepository.findByAppointmentAtBetween(any(), any()))
                .thenReturn(List.of(localSession));

        // No Feegow, a consulta individual tem status "11" (Desmarcado pelo paciente)
        FeegowAppointment feegowAppt = new FeegowAppointment("5001", "10", "26", "Dra. Vania", "Matriz", appointmentTime, "11", "Consulta", "100", false);
        when(appointmentExternalPort.findById("5001")).thenReturn(feegowAppt);

        reconciler.reconcile(List.of(targetDate), List.of());

        ArgumentCaptor<AppointmentSession> captor = ArgumentCaptor.forClass(AppointmentSession.class);
        verify(appointmentSessionRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentSessionStatus.CANCELED);
        assertThat(captor.getValue().getStatusDetails()).isEqualTo("CANCELLED_OR_RESCHEDULED_ON_FEEGOW");
    }

    @Test
    @DisplayName("reconcile: NÃO altera sessões que já se encontram CONFIRMED")
    void shouldNeverCancelAlreadyConfirmedSessions() {
        LocalDate targetDate = LocalDate.of(2026, 8, 24);
        LocalDateTime appointmentTime = targetDate.atTime(11, 0);

        AppointmentSession localSession = AppointmentSession.builder()
                .id(UUID.randomUUID())
                .feegowAppointmentId("5002")
                .appointmentAt(appointmentTime)
                .status(AppointmentSessionStatus.CONFIRMED)
                .build();

        when(appointmentSessionRepository.findByAppointmentAtBetween(any(), any()))
                .thenReturn(List.of(localSession));

        reconciler.reconcile(List.of(targetDate), List.of());

        verify(appointmentSessionRepository, never()).save(any());
    }
}
