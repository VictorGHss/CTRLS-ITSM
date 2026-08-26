package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.config.security.WebhookSignatureValidator;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipDeskGuardService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipIdentityReconciler;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookInboundService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookIntentMatcher;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest.BlipWebhookController;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;

class BlipHumanAttendanceGuardTest {

    private BlipLIMEClient limeClient;
    private ObjectMapper objectMapper;
    private BlipIdentityReconciler reconciler;
    private BlipProperties blipProperties;
    private BlipContextService blipContextService;
    private HandleBlipWebhookUseCase handleBlipWebhookUseCase;
    private BlipWebhookInboundService blipWebhookInboundService;
    private WebhookSignatureValidator webhookSignatureValidator;
    private BlipNotificationService blipNotificationService;
    private org.springframework.core.env.Environment env;
    private BlipWebhookController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        limeClient = mock(BlipLIMEClient.class);
        objectMapper = new ObjectMapper();
        reconciler = mock(BlipIdentityReconciler.class);
        blipProperties = new BlipProperties();
        blipProperties.setBlocks(new BlipProperties.Blocks());
        blipProperties.getBlocks().setDeskStateId("desk-state-uuid");
        blipProperties.getBlocks().setExibirAgenda("exibir-agenda-uuid");
        blipProperties.getBlocks().setWaitingResponse("waiting-response-uuid");

        BlipDeskGuardService blipDeskGuardService = new BlipDeskGuardService(limeClient);

        blipContextService = new BlipContextService(
                limeClient,
                objectMapper,
                new SimpleAsyncTaskExecutor(),
                reconciler,
                blipProperties,
                blipDeskGuardService
        );

        handleBlipWebhookUseCase = mock(HandleBlipWebhookUseCase.class);
        blipWebhookInboundService = mock(BlipWebhookInboundService.class);
        webhookSignatureValidator = mock(WebhookSignatureValidator.class);
        blipNotificationService = mock(BlipNotificationService.class);
        env = mock(org.springframework.core.env.Environment.class);

        when(env.acceptsProfiles(any(org.springframework.core.env.Profiles.class))).thenReturn(true);
        when(webhookSignatureValidator.isValid(any(byte[].class), any(), any())).thenReturn(true);

        org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);

        BlipWebhookIdempotencyService idempotencyService = new BlipWebhookIdempotencyService(redisProvider);
        BlipWebhookIntentMatcher intentMatcher = new BlipWebhookIntentMatcher(blipProperties);

        controller = new BlipWebhookController(
                handleBlipWebhookUseCase,
                blipWebhookInboundService,
                idempotencyService,
                intentMatcher,
                objectMapper,
                webhookSignatureValidator,
                env,
                blipContextService,
                blipNotificationService,
                blipProperties
        );
    }

    @Test
    @DisplayName("Deve detectar atendimento humano quando master-state aponta para desk")
    void testIsInHumanAttendanceWithDeskMasterState() {
        String phone = "5511999999999";
        when(limeClient.normalizeUserIdentity(phone)).thenReturn(phone + "@wa.gw.msging.net");

        when(limeClient.executeCommand(any(), eq(BlipLIMEClient.AuthorizationScope.ROUTER)))
                .thenReturn(Map.of("resource", "desk@msging.net"));

        assertTrue(blipContextService.isInHumanAttendance(phone));
    }

    @Test
    @DisplayName("Deve desativar State-Lock e nunca enviar advertência quando contato estiver em atendimento humano")
    void testStateLockBypassDuringHumanAttendance() {
        String phone = "5511999999999";
        String rawJson = """
                {
                    "id": "msg-uuid-1",
                    "from": "5511999999999@wa.gw.msging.net",
                    "type": "text/plain",
                    "content": "confirmar"
                }
                """;

        when(limeClient.normalizeUserIdentity(phone)).thenReturn(phone + "@wa.gw.msging.net");
        when(limeClient.normalizeUserIdentity(phone + "@wa.gw.msging.net")).thenReturn(phone + "@wa.gw.msging.net");

        when(blipWebhookInboundService.parse(any())).thenReturn(
                new BlipWebhookInboundService.ParsedInbound(
                        phone + "@wa.gw.msging.net",
                        "confirmar",
                        "msg-uuid-1",
                        null,
                        "confirmar",
                        "bsuid-123",
                        "text/plain",
                        false,
                        null
                )
        );

        when(handleBlipWebhookUseCase.execute(any())).thenReturn(
                new HandleBlipWebhookUseCase.WebhookResult("Recepção", "Paciente", "123", "01/01/2000", "Atendimento humano", "Dr.")
        );

        // Simula isConfirmingAgenda = true no contexto, mas o usuário está em atendimento humano com ticket aberto no Desk
        when(limeClient.executeCommand(any(), eq(BlipLIMEClient.AuthorizationScope.ROUTER)))
                .thenAnswer(invocation -> {
                    Map<String, Object> cmd = invocation.getArgument(0);
                    String uri = String.valueOf(cmd.get("uri"));
                    if (uri.contains("isConfirmingAgenda")) {
                        return Map.of("resource", "true");
                    }
                    if (uri.contains("/tickets")) {
                        return Map.of("resource", Map.of("items", java.util.List.of(
                                Map.of("customerIdentity", phone + "@wa.gw.msging.net", "status", "Open")
                        )));
                    }
                    return Map.of("resource", "ok");
                });

        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);

        ResponseEntity<?> response = controller.blipWebhook("test-token", null, request, rawJson);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        // Garante que NUNCA enviou a mensagem de orientação do State-Lock ("Por favor, utilize os botões acima...")
        verify(blipNotificationService, never()).sendPlainTextMessage(eq(phone + "@wa.gw.msging.net"), any());

        // Garante que o fluxo continuou para o caso de uso normalmente
        verify(handleBlipWebhookUseCase).execute(any());
    }
}
