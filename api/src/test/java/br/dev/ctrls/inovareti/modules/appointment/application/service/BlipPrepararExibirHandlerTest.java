package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.access.domain.port.output.BlipContactClientPort;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipWebhookPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookResult;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlipPrepararExibirHandlerTest {

    @Mock
    private BlipIdentityReconciler blipIdentityReconciler;

    @Mock
    private BlipContextService blipContextService;

    @Mock
    private BlipWebhookPreprocessor preprocessor;

    @Mock
    private AppointmentSessionRepositoryPort appointmentSessionRepository;

    @Mock
    private AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;

    @Mock
    private PatientExternalPort patientExternalPort;

    @Mock
    private BlipContactClientPort blipContactClientPort;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private NotificationGroupRepositoryPort notificationGroupRepository;

    @Mock
    private BlipAppointmentFormatter blipAppointmentFormatter;

    @InjectMocks
    private BlipPrepararExibirHandler handler;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
    }

    @Test
    @DisplayName("handlePrepararOuExibir: Preparar_Atendimento sincroniza contato e resolve grupo")
    void shouldHandlePrepararAtendimento() {
        String phone = "554299999999";
        BlipWebhookPayload payload = new BlipWebhookPayload("msg-1", null, "preparar_atendimento", phone, "tok", null, Map.of(), null, "text/plain");

        when(blipIdentityReconciler.resolveAndReconcileIdentity(phone, null)).thenReturn(phone);
        when(preprocessor.purifyPhoneNumberForSearch(phone)).thenReturn("4299999999");

        AppointmentSession session = AppointmentSession.builder()
                .id(UUID.randomUUID())
                .feegowAppointmentId("1001")
                .doctorProfissionalId("26")
                .patientId("5005")
                .build();
        when(appointmentSessionRepository.findActiveByPhoneNumber("4299999999")).thenReturn(List.of(session));

        AppointmentDoctorMapping doctorMapping = AppointmentDoctorMapping.builder()
                .profissionalId("26")
                .blipQueueId("queue-23")
                .build();
        when(appointmentDoctorMappingRepository.findByProfissionalId("26")).thenReturn(Optional.of(doctorMapping));
        when(blipContextService.resolveQueueName("queue-23")).thenReturn("Recepção 2° Andar - Direita");

        WebhookResult result = handler.handlePrepararOuExibir(payload, true);

        assertThat(result.action()).isEqualTo("processed");
        verify(blipContactClientPort).syncContact(eq(phone), any(), any(), eq("Recepção 2° Andar - Direita"), eq("26"));
    }

    @Test
    @DisplayName("handlePrepararOuExibir: Exibir_Agenda injeta lista_detalhada e groupId no contexto Blip")
    void shouldHandleExibirAgenda() {
        String phone = "554299999999";
        UUID groupId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        BlipWebhookPayload payload = new BlipWebhookPayload("msg-2", null, "exibir_agenda", phone, "tok", groupId.toString(), Map.of(), null, "text/plain");

        when(blipIdentityReconciler.resolveAndReconcileIdentity(phone, null)).thenReturn(phone);

        AppointmentSession session = AppointmentSession.builder()
                .id(UUID.randomUUID())
                .appointmentAt(LocalDateTime.now())
                .currentGroupId(groupId)
                .build();
        when(appointmentSessionRepository.findActiveByPhoneNumber(phone)).thenReturn(List.of(session));

        NotificationGroup group = NotificationGroup.builder()
                .groupId(groupId)
                .preCompiledScheduleText("• 24/08 às 09:00 - Dra. Vania Gulin")
                .build();
        when(notificationGroupRepository.findByGroupId(groupId)).thenReturn(List.of(group));

        WebhookResult result = handler.handlePrepararOuExibir(payload, false);

        assertThat(result.action()).isEqualTo("processed");
        verify(blipContextService).setUserContextForUser(phone, "lista_detalhada", "• 24/08 às 09:00 - Dra. Vania Gulin");
        verify(blipContextService).setUserContextForUser(phone, "groupId", groupId.toString());
    }
}
