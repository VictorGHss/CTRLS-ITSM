package br.dev.ctrls.itsm.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.dev.ctrls.itsm.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.itsm.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipWebhookPayload;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.BlipProperties;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlipWebhookPreprocessorTest {

    @Mock
    private BlipLIMEClient blipLimeClient;

    @Mock
    private AppointmentSessionRepositoryPort appointmentSessionRepository;

    @Mock
    private BlipContextService blipContextService;

    @Mock
    private BlipProperties blipProperties;

    @Mock
    private BlipIdentityReconciler blipIdentityReconciler;

    @Mock
    private BlipUserIdentityReconciliationRepositoryPort blipUserIdentityReconciliationRepository;

    @Mock
    private AppointmentMotorProperties appointmentMotorProperties;

    @Mock
    private AuditPort auditPort;

    @InjectMocks
    private BlipWebhookPreprocessor preprocessor;

    @Test
    @DisplayName("purifyPhoneNumberForSearch: Limpa @, ., 55 e caracteres especiais")
    void testPurifyPhoneNumberForSearch() {
        assertThat(preprocessor.purifyPhoneNumberForSearch("5542988079854@tunnel.msging.net")).isEqualTo("42988079854");
        assertThat(preprocessor.purifyPhoneNumberForSearch("+55 (42) 98807-9854")).isEqualTo("42988079854");
        assertThat(preprocessor.purifyPhoneNumberForSearch("4299991234")).isEqualTo("4299991234");
        assertThat(preprocessor.purifyPhoneNumberForSearch("")).isEmpty();
        assertThat(preprocessor.purifyPhoneNumberForSearch(null)).isEmpty();
    }

    @Test
    @DisplayName("isAntiGhostCommercialResponse: Identifica mensagens de secretária eletrônica ou ausência")
    void testAntiGhostDetection() {
        BlipWebhookPayload autoReply = new BlipWebhookPayload("1", null, null, "554299999999", "tok", "A clínica agradece seu contato! Como podemos ajudar?", Map.of(), null, "text/plain");
        assertThat(preprocessor.isAntiGhostCommercialResponse(autoReply)).isTrue();

        BlipWebhookPayload normalUser = new BlipWebhookPayload("2", null, null, "554299999999", "tok", "Quero confirmar meu horário amanhã", Map.of(), null, "text/plain");
        assertThat(preprocessor.isAntiGhostCommercialResponse(normalUser)).isFalse();

        BlipWebhookPayload interactiveClick = new BlipWebhookPayload("3", null, null, "554299999999", "tok", "confirm_12345", Map.of(), null, "text/plain");
        assertThat(preprocessor.isAntiGhostCommercialResponse(interactiveClick)).isFalse();
    }

    @Test
    @DisplayName("validateWebhookToken: Lança SecurityException se o token for divergente")
    void testValidateWebhookTokenInvalid() {
        var secProps = new AppointmentMotorProperties.Security();
        secProps.setWebhookToken("SECRET_TOKEN_123");
        when(appointmentMotorProperties.getSecurity()).thenReturn(secProps);

        BlipWebhookPayload invalidPayload = new BlipWebhookPayload("1", "123", "confirm", "554299", "WRONG_TOKEN", null, Map.of(), null, "text/plain");

        assertThatThrownBy(() -> preprocessor.validateWebhookToken(invalidPayload))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid token");
    }

    @Test
    @DisplayName("enrichPayload: Injeta contexto payloadclique quando a ação é confirm_ ou alter_")
    void testEnrichPayloadInjectsContext() {
        when(blipLimeClient.reconcileNinthDigit("554299999999", appointmentSessionRepository)).thenReturn("554299999999");
        when(blipProperties.getSubbotId()).thenReturn("atendimento@msging.net");

        BlipWebhookPayload payload = new BlipWebhookPayload("msg-1", "101", "confirm_101", "554299999999", "tok", null, Map.of(), null, "text/plain");

        BlipWebhookPayload enriched = preprocessor.enrichPayload(payload);

        assertThat(enriched).isNotNull();
        assertThat(enriched.action()).isEqualTo("confirm_101");
        verify(blipContextService).setUserContextForUser("554299999999", "payloadclique", "confirm_101");
    }
}
