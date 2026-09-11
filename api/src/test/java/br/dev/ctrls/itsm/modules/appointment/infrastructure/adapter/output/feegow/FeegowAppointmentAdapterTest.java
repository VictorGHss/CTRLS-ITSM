package br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.feegow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.client.FeegowAppointmentClient;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.FeegowProperties;

class FeegowAppointmentAdapterTest {

    private FeegowAppointmentClient appointmentClient;
    private FeegowProperties feegowProperties;
    private AppointmentMotorProperties motorProperties;
    private FeegowAppointmentAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        appointmentClient = mock(FeegowAppointmentClient.class);
        feegowProperties = mock(FeegowProperties.class);
        motorProperties = mock(AppointmentMotorProperties.class);
        objectMapper = new ObjectMapper();

        when(feegowProperties.getStatusUpdateUrl()).thenReturn("https://api.feegow.com/v1/appoints/status");
        when(feegowProperties.getApiKey()).thenReturn("fake-token");
        when(motorProperties.getFeegowConfirmedStatusId()).thenReturn("7");

        @SuppressWarnings("unchecked")
        ObjectProvider<AppointmentDoctorMappingRepositoryPort> doctorMappingProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ProfessionalExternalPort> professionalProvider = mock(ObjectProvider.class);

        adapter = new FeegowAppointmentAdapter(
                motorProperties,
                feegowProperties,
                objectMapper,
                appointmentClient,
                doctorMappingProvider,
                professionalProvider
        );
    }

    @Test
    @DisplayName("updateAppointmentStatus: Deve lançar RuntimeException quando Feegow retornar HTTP 200 com success=false")
    void shouldThrowWhenFeegowReturnsSuccessFalse() {
        String errorJson = """
                {
                    "success": false,
                    "message": "O agendamento não pode ser alterado pois já se encontra finalizado"
                }
                """;

        when(appointmentClient.updateStatus(any(URI.class), eq("fake-token"), any()))
                .thenReturn(ResponseEntity.ok(errorJson));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                adapter.updateAppointmentStatus("3383960", "7")
        );

        assertTrue(ex.getMessage().contains("O agendamento não pode ser alterado"));
    }

    @Test
    @DisplayName("updateAppointmentStatus: Deve executar com sucesso quando Feegow retornar HTTP 200 com success=true")
    void shouldSucceedWhenFeegowReturnsSuccessTrue() {
        String successJson = """
                {
                    "success": true,
                    "message": "Status atualizado com sucesso"
                }
                """;

        when(appointmentClient.updateStatus(any(URI.class), eq("fake-token"), any()))
                .thenReturn(ResponseEntity.ok(successJson));

        assertDoesNotThrow(() -> adapter.updateAppointmentStatus("3383960", "7"));
    }

    @Test
    @DisplayName("updateAppointmentStatus: Deve lançar exceção quando Feegow retornar status HTTP 500")
    void shouldThrowWhenFeegowReturnsHttp500() {
        when(appointmentClient.updateStatus(any(URI.class), eq("fake-token"), any()))
                .thenReturn(ResponseEntity.status(500).body("Internal Server Error"));

        assertThrows(RuntimeException.class, () ->
                adapter.updateAppointmentStatus("3383960", "7")
        );
    }
}
