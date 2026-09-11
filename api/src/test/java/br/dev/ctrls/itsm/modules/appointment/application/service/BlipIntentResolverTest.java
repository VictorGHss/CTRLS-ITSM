package br.dev.ctrls.itsm.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.itsm.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipWebhookPayload;
import br.dev.ctrls.itsm.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookIntent;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.BlipProperties;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlipIntentResolverTest {

    @Mock
    private BlipIdentityReconciler blipIdentityReconciler;

    @Mock
    private BlipWebhookPreprocessor preprocessor;

    @Mock
    private AppointmentSessionRepositoryPort appointmentSessionRepository;

    @Mock
    private BlipNotificationService blipNotificationService;

    @Mock
    private BlipProperties blipProperties;

    @Mock
    private BlipContextService blipContextService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private BlipIntentResolver intentResolver;

    @Test
    @DisplayName("detectIntent: Detecta CONFIRM, ALTER e CANCEL com precisão")
    void testDetectIntent() {
        assertThat(BlipIntentResolver.detectIntent("Sim")).isEqualTo(WebhookIntent.CONFIRM);
        assertThat(BlipIntentResolver.detectIntent("Confirmo")).isEqualTo(WebhookIntent.CONFIRM);
        assertThat(BlipIntentResolver.detectIntent("Confirmar presença")).isEqualTo(WebhookIntent.CONFIRM);

        assertThat(BlipIntentResolver.detectIntent("Alterar")).isEqualTo(WebhookIntent.ALTER);
        assertThat(BlipIntentResolver.detectIntent("Quero remarcar")).isEqualTo(WebhookIntent.ALTER);

        assertThat(BlipIntentResolver.detectIntent("Cancelar")).isEqualTo(WebhookIntent.CANCEL);

        // Negações ou frases longas viram UNKNOWN
        assertThat(BlipIntentResolver.detectIntent("Não quero confirmar")).isEqualTo(WebhookIntent.UNKNOWN);
        assertThat(BlipIntentResolver.detectIntent("Se der eu confirmo depois")).isEqualTo(WebhookIntent.UNKNOWN);
        assertThat(BlipIntentResolver.detectIntent("Olá bom dia gostaria de saber onde fica a clínica")).isEqualTo(WebhookIntent.UNKNOWN);
    }

    @Test
    @DisplayName("resolveTextIntentions: Mapeia texto 'sim' para confirm_12345 quando há 1 agendamento pendente")
    void shouldResolveTextIntentionToSpecificAppointment() {
        String fromPhone = "554299999999";
        BlipWebhookPayload payload = new BlipWebhookPayload("msg-1", null, "sim", fromPhone, "tok", null, Map.of(), null, "text/plain");

        when(blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, null)).thenReturn(fromPhone);

        AppointmentSession session = AppointmentSession.builder()
                .id(UUID.randomUUID())
                .feegowAppointmentId("12345")
                .phoneNumber("4299999999")
                .status(AppointmentSessionStatus.PENDING)
                .build();
        when(appointmentSessionRepository.findActiveByPhoneNumber("554299999999")).thenReturn(List.of(session));

        String resolvedAction = intentResolver.resolveTextIntentions("sim", "sim", payload);

        assertThat(resolvedAction).isEqualTo("confirm_12345");
    }
}
