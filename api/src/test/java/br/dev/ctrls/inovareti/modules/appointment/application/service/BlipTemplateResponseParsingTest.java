package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
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

    @Test
    @DisplayName("Deve extrair 'Confirmar Presença' mesmo com timestamp trailing '09:10' e correlacionar com idAgendamentoFeegow")
    void shouldExtractConfirmarPresencaWithTrailingTimestamp() {
        String incidentPayload = """
                confirmacao_consulta_v6_itsm
                Olá, SARA KREPEL MANN DE SOUZA. Aqui é a Clínica Inovare. Seguem os dados do seu atendimento:
                Profissional: Dr. Eduardo Mattos
                Data: 10/09/2026
                Horário: 09:40

                Para seguirmos com o registro, escolha uma opção abaixo:


                Confirmar Presença
                Solicitar Alteração
                07:00

                Modelo de mensagem: aviso confirmacao pendente v2

                Olá, SARA KREPEL MANN DE SOUZA.

                Identificamos que a confirmação do seu horário na Clínica Inovare está pendente.

                Para garantir a finalização do seu agendamento e evitar cancelamentos automáticos, precisamos de uma resposta sua.


                Confirmar
                Alterar
                09:05

                Resposta


                confirmacao_consulta_v6_itsm
                Olá, SARA KREPEL MANN DE SOUZA. Aqui é a Clínica Inovare. Seguem os dados do seu atendimento:
                Profissional: Dr. Eduardo Mattos
                Data: 10/09/2026
                Horário: 09:40

                Para seguirmos com o registro, escolha uma opção abaixo:
                Confirmar Presença

                09:10
                """;

        String userPhone = "554299998888@wa.gw.msging.net";
        when(blipContextService.getUserContext(userPhone, "last_pending_appointment_id"))
                .thenReturn(null);
        when(blipContextService.getUserContext(userPhone, "idAgendamentoFeegow"))
                .thenReturn("3383960");

        Map<String, Object> payload = Map.of(
                "from", userPhone,
                "id", "msg-incident-3383960",
                "content", incidentPayload,
                "type", "text/plain"
        );

        BlipWebhookInboundService.ParsedInbound parsed = inboundService.parse(payload);

        assertEquals("confirm_3383960", parsed.action());
        assertEquals("3383960", parsed.appointmentId());

        WebhookIntent intent = HandleBlipWebhookUseCase.detectIntent(incidentPayload);
        assertEquals(WebhookIntent.CONFIRM, intent);
    }

    @Test
    @DisplayName("Deve resolver breadcrumb via fallback de banco quando contexto do Blip for nulo")
    void shouldResolveBreadcrumbFromDatabaseFallbackWhenContextIsNull() {
        br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort repoMock =
                mock(br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort.class);
        BlipWebhookInboundService serviceWithDb = new BlipWebhookInboundService(blipContextService, new ObjectMapper(), repoMock);

        String phone = "554299998888@wa.gw.msging.net";
        when(blipContextService.getUserContext(any(), any())).thenReturn(null);

        br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession sessionMock =
                mock(br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession.class);
        when(sessionMock.getFeegowAppointmentId()).thenReturn("3383960");
        when(repoMock.findActiveByPhoneNumber("554299998888")).thenReturn(List.of(sessionMock));

        Map<String, Object> payload = Map.of(
                "from", phone,
                "id", "msg-db-fallback",
                "content", "Confirmar Presença\n\n09:10",
                "type", "text/plain"
        );

        BlipWebhookInboundService.ParsedInbound parsed = serviceWithDb.parse(payload);

        assertEquals("confirm_3383960", parsed.action());
        assertEquals("3383960", parsed.appointmentId());
    }
}
