package br.dev.ctrls.itsm.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.itsm.config.CacheConfig;
import br.dev.ctrls.itsm.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.client.FeegowAppointmentClient;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.FeegowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.net.URI;
import java.util.Optional;

import br.dev.ctrls.itsm.modules.access.domain.port.output.FeegowClientPort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig
class FeegowRestClientAdapterCacheTest {

    @TestConfiguration
    @EnableCaching(proxyTargetClass = true)
    @Import(CacheConfig.class)
    static class TestContextConfig {
        @Bean
        public FeegowAppointmentClient appointmentClient() {
            return mock(FeegowAppointmentClient.class);
        }

        @Bean
        public PatientExternalPort patientExternalPort() {
            return mock(PatientExternalPort.class);
        }

        @Bean
        public DoctorConfigurationRepository doctorConfigurationRepository() {
            return mock(DoctorConfigurationRepository.class);
        }

        @Bean
        public ProfessionalExternalPort professionalExternalPort() {
            return mock(ProfessionalExternalPort.class);
        }

        @Bean
        public AppointmentMotorProperties appointmentMotorProperties() {
            AppointmentMotorProperties props = new AppointmentMotorProperties();
            props.setFeegowBaseUrl("https://api.feegow.com");
            props.setFeegowSearchPath("/v1/api/appoints/search");
            return props;
        }

        @Bean
        public FeegowProperties feegowProperties() {
            FeegowProperties props = new FeegowProperties();
            props.setApiKey("test-token");
            return props;
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public FeegowRestClientAdapter feegowRestClientAdapter(
                FeegowAppointmentClient appointmentClient,
                PatientExternalPort patientExternalPort,
                DoctorConfigurationRepository doctorConfigurationRepository,
                ProfessionalExternalPort professionalExternalPort,
                AppointmentMotorProperties appointmentMotorProperties,
                FeegowProperties feegowProperties,
                ObjectMapper objectMapper
        ) {
            return new FeegowRestClientAdapter(
                    appointmentClient,
                    patientExternalPort,
                    doctorConfigurationRepository,
                    professionalExternalPort,
                    appointmentMotorProperties,
                    feegowProperties,
                    objectMapper
            );
        }
    }

    @Autowired
    private FeegowClientPort feegowRestClientAdapter;

    @Autowired
    private FeegowAppointmentClient appointmentClient;

    @BeforeEach
    void setUp() {
        reset(appointmentClient);
    }

    @Test
    @DisplayName("Deveria avaliar a condição unless do cache sem lançar SpelEvaluationException")
    void shouldCacheWithoutSpelEvaluationException() {
        String appointmentId = "3469606";
        String feegowResponseJson = """
                {
                    "success": true,
                    "content": [
                        {
                            "id": 3469606,
                            "paciente_id": "310300",
                            "paciente_cpf": "12345678901",
                            "paciente": "LUIS RICARDO MACHADO",
                            "paciente_nome": "LUIS RICARDO MACHADO",
                            "paciente_celular": "42999999999",
                            "nome_especialidade": "Urologia",
                            "data": "09-10-2026",
                            "horario": "12:40"
                        }
                    ]
                }
                """;

        when(appointmentClient.searchAppointments(any(URI.class), any()))
                .thenReturn(ResponseEntity.ok(feegowResponseJson));

        // 1ª chamada: vai no client HTTP e armazena no cache
        Optional<FeegowPatientAccessInfo> firstCall = feegowRestClientAdapter.fetchPatientAccessInfo(appointmentId);
        assertThat(firstCall).isPresent();
        assertThat(firstCall.get().name()).isEqualTo("LUIS RICARDO MACHADO");

        // 2ª chamada: deve vir do cache Caffeine, sem chamar o client HTTP de novo
        Optional<FeegowPatientAccessInfo> secondCall = feegowRestClientAdapter.fetchPatientAccessInfo(appointmentId);
        assertThat(secondCall).isPresent();
        assertThat(secondCall.get().name()).isEqualTo("LUIS RICARDO MACHADO");

        // O client HTTP deve ter sido invocado apenas 1 vez graças ao cache
        verify(appointmentClient, times(1)).searchAppointments(any(URI.class), any());
    }

    @Test
    @DisplayName("Deveria retornar Optional.empty() sem erro de SpEL quando agendamento não existir")
    void shouldHandleEmptyOptionalWithoutSpelError() {
        String appointmentId = "9999999";
        String emptyResponseJson = """
                {
                    "success": true,
                    "content": []
                }
                """;

        when(appointmentClient.searchAppointments(any(URI.class), any()))
                .thenReturn(ResponseEntity.ok(emptyResponseJson));

        Optional<FeegowPatientAccessInfo> result = feegowRestClientAdapter.fetchPatientAccessInfo(appointmentId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Deveria retornar Optional.empty() imediatamente sem consultar Feegow quando appointmentId não for numérico")
    void shouldReturnEmptyImmediatelyWhenAppointmentIdIsNotNumeric() {
        String nonNumericId = "INOV-20260904-33866473915";

        Optional<FeegowPatientAccessInfo> result = feegowRestClientAdapter.fetchPatientAccessInfo(nonNumericId);

        assertThat(result).isEmpty();
        verify(appointmentClient, never()).searchAppointments(any(), any());
    }
}
