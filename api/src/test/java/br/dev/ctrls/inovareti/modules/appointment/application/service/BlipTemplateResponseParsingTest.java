package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookIntent;

class BlipTemplateResponseParsingTest {

    private BlipContextService blipContextService;
    private BlipWebhookInboundService inboundService;

    @BeforeEach
    void setUp() {
        blipContextService = mock(BlipContextService.class);
        inboundService = new BlipWebhookInboundService(blipContextService, new ObjectMapper());
    }

    @Test
    @DisplayName("Deve extrair 'Alterar' e correlacionar com breadcrumb de agendamento em payload multilinhas de template")
    void shouldExtractAlterarFromMultilineTemplate() {
        String templatePayload = """
                aviso_confirmacao_pendente_v2
                Olá, LETICIA SOUZA DE PAULA.

                Identificamos que a confirmação do seu horário na Clínica Inovare está pendente.

                Para garantir a finalização do seu agendamento e evitar cancelamentos automáticos, precisamos de uma resposta sua.


                Alterar
                """;

        when(blipContextService.getUserContext("5531997110512@wa.gw.msging.net", "last_pending_appointment_id"))
                .thenReturn("3445259");

        Map<String, Object> payload = Map.of(
                "from", "5531997110512@wa.gw.msging.net",
                "id", "msg-12345",
                "content", templatePayload,
                "type", "text/plain"
        );

        BlipWebhookInboundService.ParsedInbound parsed = inboundService.parse(payload);

        assertEquals("alter_3445259", parsed.action());
        assertEquals("3445259", parsed.appointmentId());

        WebhookIntent intent = HandleBlipWebhookUseCase.detectIntent(templatePayload);
        assertEquals(WebhookIntent.ALTER, intent);
    }

    @Test
    @DisplayName("Deve extrair 'Confirmar' e correlacionar com breadcrumb de agendamento em payload multilinhas de template")
    void shouldExtractConfirmarFromMultilineTemplate() {
        String templatePayload = """
                aviso_confirmacao_pendente_v2
                Olá, LETICIA SOUZA DE PAULA.

                Identificamos que a confirmação do seu horário na Clínica Inovare está pendente.

                Para garantir a finalização do seu agendamento e evitar cancelamentos automáticos, precisamos de uma resposta sua.


                Confirmar
                """;

        when(blipContextService.getUserContext("5531997110512@wa.gw.msging.net", "last_pending_appointment_id"))
                .thenReturn("3445259");

        Map<String, Object> payload = Map.of(
                "from", "5531997110512@wa.gw.msging.net",
                "id", "msg-12346",
                "content", templatePayload,
                "type", "text/plain"
        );

        BlipWebhookInboundService.ParsedInbound parsed = inboundService.parse(payload);

        assertEquals("confirm_3445259", parsed.action());
        assertEquals("3445259", parsed.appointmentId());

        WebhookIntent intent = HandleBlipWebhookUseCase.detectIntent(templatePayload);
        assertEquals(WebhookIntent.CONFIRM, intent);
    }
}
