package br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.gestaods.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.gestaods.config.GestaoDsProperties;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsAgendamentoItemDto;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsPacienteDto;

class GestaoDsRestClientTest {

    private GestaoDsProperties properties;
    private GestaoDsRestClient client;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new GestaoDsProperties();
        properties.setBaseUrl("https://apidev.gestaods.com.br");
        properties.setToken("test-token-123");
        properties.setEnabled(true);
        properties.setDevMode(false);

        objectMapper = new ObjectMapper();

        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl(properties.getBaseUrl());

        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        client = new GestaoDsRestClient(restClient, properties, objectMapper);
    }

    @Test
    @DisplayName("Deve buscar paciente por CPF com sucesso")
    void shouldFindPatientByCpfSuccessfully() {
        String cpf = "123.456.789-00";
        String cleanCpf = "12345678900";
        String expectedUrl = "https://apidev.gestaods.com.br/api/paciente/test-token-123/" + cleanCpf + "/";

        String jsonResponse = """
            {
                "id": 101,
                "nome_completo": "Maria Silva Santos",
                "cpf": "12345678900",
                "email": "maria@example.com",
                "celular": "42999998888",
                "nascimento": "15/05/1985"
            }
            """;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Optional<GestaoDsPacienteDto> result = client.findPatientByCpf(cpf);

        assertThat(result).isPresent();
        assertThat(result.get().nomeCompleto()).isEqualTo("Maria Silva Santos");
        assertThat(result.get().primeiroNome()).isEqualTo("Maria");
        assertThat(result.get().cpf()).isEqualTo("12345678900");
        assertThat(result.get().celular()).isEqualTo("42999998888");
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve retornar vazio quando o Gestão DS retornar token inválido (HTTP 400)")
    void shouldReturnEmptyWhenTokenIsInvalid() {
        String cpf = "12345678900";
        String expectedUrl = "https://apidev.gestaods.com.br/api/paciente/test-token-123/" + cpf + "/";

        String jsonError = "{\"message\":\"Informe um token valido!\",\"status\":400}";

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withBadRequest().body(jsonError).contentType(MediaType.APPLICATION_JSON));

        Optional<GestaoDsPacienteDto> result = client.findPatientByCpf(cpf);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve buscar agendamentos do paciente com sucesso")
    void shouldFindPatientAppointmentsSuccessfully() {
        String cpf = "12345678900";
        String expectedUrl = "https://apidev.gestaods.com.br/api/paciente/agendamentos/";

        String jsonResponse = """
            [
                {
                    "agendamento": "TOKEN-APP-001",
                    "data_agendamento": "2026-09-08 14:30:00",
                    "confirmado": true,
                    "cancelado": false,
                    "profissional_nome": "Dr. Carlos Eduardo",
                    "procedimento_nome": "Consulta Cardiologia"
                }
            ]
            """;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        List<GestaoDsAgendamentoItemDto> items = client.findPatientAppointments(cpf);

        assertThat(items).hasSize(1);
        GestaoDsAgendamentoItemDto appt = items.get(0);
        assertThat(appt.agendamento()).isEqualTo("TOKEN-APP-001");
        assertThat(appt.profissionalNome()).isEqualTo("Dr. Carlos Eduardo");
        assertThat(appt.confirmado()).isTrue();
        assertThat(appt.isAtivo()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve atualizar status do agendamento com sucesso")
    void shouldUpdateAppointmentStatusSuccessfully() {
        String expectedUrl = "https://apidev.gestaods.com.br/api/paciente/agendamentos/";

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        boolean success = client.updateAppointmentStatus("TOKEN-APP-001", true, false, null);

        assertThat(success).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("Não deve chamar a API se a integração estiver desabilitada")
    void shouldNotCallApiIfDisabled() {
        properties.setEnabled(false);

        Optional<GestaoDsPacienteDto> result = client.findPatientByCpf("12345678900");
        assertThat(result).isEmpty();

        List<GestaoDsAgendamentoItemDto> appts = client.findPatientAppointments("12345678900");
        assertThat(appts).isEmpty();
    }
}
