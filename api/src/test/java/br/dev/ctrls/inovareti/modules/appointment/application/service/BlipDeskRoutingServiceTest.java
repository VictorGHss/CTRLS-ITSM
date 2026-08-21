package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookResult;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlipDeskRoutingServiceTest {

    @Mock
    private BlipContextService blipContextService;

    @Mock
    private BlipIdentityReconciler blipIdentityReconciler;

    @Mock
    private BlipWebhookPreprocessor preprocessor;

    @Mock
    private AppointmentSessionRepositoryPort appointmentSessionRepository;

    @Mock
    private AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;

    @Mock
    private PatientExternalPort patientExternalPort;

    @InjectMocks
    private BlipDeskRoutingService deskRoutingService;

    @Test
    @DisplayName("isWithinBusinessHours: Retorna boolean indicando conformidade do horário")
    void testIsWithinBusinessHours() {
        boolean within = BlipDeskRoutingService.isWithinBusinessHours();
        assertThat(within).isIn(true, false);
    }

    @Test
    @DisplayName("applySilentDeskRouting: Resolve fila do médico e dados do paciente quando há sessão ativa")
    void shouldResolveQueueAndPatientDetailsWhenSessionExists() {
        if (!BlipDeskRoutingService.isWithinBusinessHours()) {
            WebhookResult result = deskRoutingService.applySilentDeskRouting("554299999999");
            assertThat(result.action()).isEqualTo("out_of_hours_ignored");
            return;
        }

        String fromPhone = "554299999999";
        when(blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, null)).thenReturn(fromPhone);
        when(preprocessor.purifyPhoneNumberForSearch(fromPhone)).thenReturn("4299999999");

        AppointmentSession session = AppointmentSession.builder()
                .feegowAppointmentId("1001")
                .doctorProfissionalId("26")
                .patientId("5005")
                .build();
        when(appointmentSessionRepository.findActiveByPhoneNumber("4299999999")).thenReturn(List.of(session));

        AppointmentDoctorMapping doctorMapping = AppointmentDoctorMapping.builder()
                .profissionalId("26")
                .profissionalNome("Dra. Vania Gulin")
                .blipQueueId("queue-23")
                .build();
        when(appointmentDoctorMappingRepository.findByProfissionalId("26")).thenReturn(Optional.of(doctorMapping));
        when(blipContextService.resolveQueueName("queue-23")).thenReturn("Recepção 2° Andar - Direita");

        FeegowPatient patient = new FeegowPatient("5005", "Maria Silva", "4299999999", "123.456.789-00", "1985-05-15");
        when(patientExternalPort.patientInfo("5005")).thenReturn(patient);

        WebhookResult result = deskRoutingService.applySilentDeskRouting(fromPhone);

        assertThat(result.action()).isEqualTo("Atendimento humano");
        assertThat(result.queue()).isEqualTo("Recepção 2° Andar - Direita");
        assertThat(result.patientName()).isEqualTo("Maria Silva");
        assertThat(result.patientCPF()).isEqualTo("12345678900");
        assertThat(result.patientBirthdate()).isEqualTo("1985-05-15");
        assertThat(result.doctorName()).isEqualTo("Dra. Vania Gulin");

        verify(blipContextService).clearConfirmationContext(fromPhone);
        verify(blipContextService).setQueueRedirect(fromPhone, "Recepção 2° Andar - Direita");
    }
}
