package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.dev.ctrls.inovareti.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipDeliveryFailureCommand;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipDeliveryFailureRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.metrics.BlipNotificationMetrics;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlipDeliveryFailureHandlerTest {

    @Mock
    private BlipNotificationMetrics blipNotificationMetrics;

    @Mock
    private BlipDeliveryFailureRepositoryPort blipDeliveryFailureRepository;

    @Mock
    private AppointmentSessionRepositoryPort appointmentSessionRepository;

    @Mock
    private AuditPort auditPort;

    @InjectMocks
    private BlipDeliveryFailureHandler failureHandler;

    @Test
    @DisplayName("executeNotificationFailure: Incrementa métrica, grava failure e atualiza sessão para ERROR_DELIVERY")
    void shouldHandleNotificationFailureSuccessfully() {
        BlipDeliveryFailureCommand cmd = new BlipDeliveryFailureCommand("msg-err-1", "99001", 1005, "Phone not found on WhatsApp", "trace-123");

        AppointmentSession session = AppointmentSession.builder()
                .feegowAppointmentId("99001")
                .status(AppointmentSessionStatus.PENDING)
                .build();
        when(appointmentSessionRepository.findByFeegowAppointmentId("99001")).thenReturn(Optional.of(session));

        failureHandler.executeNotificationFailure(cmd);

        verify(blipNotificationMetrics).incrementFailureCount(1005, "Phone not found on WhatsApp");

        ArgumentCaptor<BlipDeliveryFailure> failureCaptor = ArgumentCaptor.forClass(BlipDeliveryFailure.class);
        verify(blipDeliveryFailureRepository).save(failureCaptor.capture());
        assertThat(failureCaptor.getValue().getMessageId()).isEqualTo("msg-err-1");
        assertThat(failureCaptor.getValue().getAppointmentId()).isEqualTo("99001");
        assertThat(failureCaptor.getValue().getErrorCode()).isEqualTo(1005);

        ArgumentCaptor<AppointmentSession> sessionCaptor = ArgumentCaptor.forClass(AppointmentSession.class);
        verify(appointmentSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(AppointmentSessionStatus.ERROR_DELIVERY);
        assertThat(sessionCaptor.getValue().getStatusDetails()).contains("1005");

        verify(auditPort).record(eq("APPOINTMENT_MOTOR"), eq("BLIP_DELIVERY_FAILURE"), anyString(), eq("trace-123"));
    }
}
