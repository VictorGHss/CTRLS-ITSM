package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessService;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipWebhookPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookResult;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlipPhysicalAccessHandlerTest {

    @Mock
    private AppointmentSessionRepositoryPort appointmentSessionRepository;

    @Mock
    private AppointmentMotorProperties appointmentMotorProperties;

    @Mock
    private DoctorConfigurationRepository doctorConfigurationRepository;

    @Mock
    private AccessService accessService;

    @Mock
    private BlipContextService blipContextService;

    @Mock
    private br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort patientExternalPort;

    @Captor
    private ArgumentCaptor<List<CompanionAccessInfo>> companionsCaptor;

    @InjectMocks
    private BlipPhysicalAccessHandler handler;

    @BeforeEach
    void setUp() {
        when(appointmentMotorProperties.getTestDoctorIds()).thenReturn(List.of("10", "20"));
        when(appointmentMotorProperties.getActiveDoctorIds()).thenReturn(List.of("30", "40"));
    }

    @Test
    @DisplayName("Integrar_GerAcesso: Sucesso quando médico é ativo nas propriedades")
    void shouldProcessIntegrarGerAcessoWhenDoctorAllowed() {
        Map<String, Object> content = Map.of(
                "idAgendamentoFeegow", "12345",
                "cpf", "11122233344"
        );
        BlipWebhookPayload payload = new BlipWebhookPayload("msg-1", "12345", "Integrar_GerAcesso", "554299999999", "tok", content, Map.of());

        AppointmentSession session = AppointmentSession.builder()
                .feegowAppointmentId("12345")
                .doctorProfissionalId("10")
                .build();
        when(appointmentSessionRepository.findByFeegowAppointmentId("12345")).thenReturn(Optional.of(session));

        DoctorConfiguration docConfig = DoctorConfiguration.builder()
                .feegowProfissionalId(10L)
                .gerAcessoMatricula("MAT-10")
                .gerAcessoCpf("99988877766")
                .build();
        when(doctorConfigurationRepository.findById(10L)).thenReturn(Optional.of(docConfig));

        WebhookResult result = handler.handleIntegrarGerAcesso(payload, "554299999999");

        assertThat(result.patientCPF()).isEqualTo("12345");
        assertThat(result.action()).isEqualTo("Integrar_GerAcesso");
        verify(accessService).processAccessRequest("12345", "11122233344", List.of());
    }

    @Test
    @DisplayName("Integrar_GerAcesso: Bypass quando médico não é permitido")
    void shouldBypassIntegrarGerAcessoWhenDoctorNotAllowed() {
        Map<String, Object> content = Map.of("appointmentId", "55555");
        BlipWebhookPayload payload = new BlipWebhookPayload("msg-2", "55555", "Integrar_GerAcesso", "554299999999", "tok", content, Map.of());

        AppointmentSession session = AppointmentSession.builder()
                .feegowAppointmentId("55555")
                .doctorProfissionalId("99")
                .build();
        when(appointmentSessionRepository.findByFeegowAppointmentId("55555")).thenReturn(Optional.of(session));
        when(doctorConfigurationRepository.findById(99L)).thenReturn(Optional.empty());

        WebhookResult result = handler.handleIntegrarGerAcesso(payload, "554299999999");

        assertThat(result.patientCPF()).isEqualTo("55555");
        verify(accessService, never()).processAccessRequest(anyString(), any(), anyList());
    }

    @Test
    @DisplayName("Não (Sem acompanhantes): Invoca AccessService com lista vazia de acompanhantes")
    void shouldProcessNaoAction() {
        Map<String, Object> content = Map.of(
                "appointmentId", "77777",
                "cpf", "00011122233"
        );
        BlipWebhookPayload payload = new BlipWebhookPayload("msg-3", "77777", "Não", "554299999999", "tok", content, Map.of());

        WebhookResult result = handler.handleNaoAction(payload, "554299999999");

        assertThat(result.patientCPF()).isEqualTo("77777");
        assertThat(result.action()).isEqualTo("Não");
        verify(accessService).processAccessRequest("77777", "00011122233", List.of());
    }

    @Test
    @DisplayName("Finalizar_Agendamento: Extrai acompanhantes com campos heterogêneos (camelCase e snake_case)")
    void shouldProcessFinalizarAgendamentoWithCompanions() {
        Map<String, Object> comp1 = Map.of("nome", "Acompanhante Um", "cpf", "111", "telefone", "4299991", "email", "a@a.com", "data_nascimento", "1990-01-01");
        Map<String, Object> comp2 = Map.of("name", "Acompanhante Dois", "cpf", "222", "phone", "4299992", "email", "b@b.com", "birthDate", "1995-05-05");

        Map<String, Object> content = Map.of(
                "appointmentId", "88888",
                "cpf", "33344455566",
                "listaAcompanhantes", List.of(comp1, comp2)
        );
        BlipWebhookPayload payload = new BlipWebhookPayload("msg-4", "88888", "Finalizar_Agendamento", "554299999999", "tok", content, Map.of());

        WebhookResult result = handler.handleFinalizarAgendamento(payload, "554299999999");

        assertThat(result.patientCPF()).isEqualTo("88888");
        assertThat(result.action()).isEqualTo("Finalizar_Agendamento");

        verify(accessService).processAccessRequest(eq("88888"), eq("33344455566"), companionsCaptor.capture());

        List<CompanionAccessInfo> captured = companionsCaptor.getValue();
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).name()).isEqualTo("Acompanhante Um");
        assertThat(captured.get(0).phone()).isEqualTo("4299991");
        assertThat(captured.get(0).birthDate()).isEqualTo("1990-01-01");
        assertThat(captured.get(1).name()).isEqualTo("Acompanhante Dois");
        assertThat(captured.get(1).phone()).isEqualTo("4299992");
        assertThat(captured.get(1).birthDate()).isEqualTo("1995-05-05");
    }
}
