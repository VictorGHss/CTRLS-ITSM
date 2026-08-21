package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentBatchFilterPipelineTest {

    @Mock
    private AppointmentFilterService appointmentFilterService;

    @Mock
    private AppointmentExternalPort appointmentExternalPort;

    @Mock
    private AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;

    @Mock
    private DoctorConfigurationRepository doctorConfigurationRepository;

    @Mock
    private AppointmentMotorProperties appointmentMotorProperties;

    @InjectMocks
    private AppointmentBatchFilterPipeline pipeline;

    @BeforeEach
    void setUp() {
        when(appointmentMotorProperties.getTestDoctorIds()).thenReturn(List.of("10", "20"));
        when(appointmentMotorProperties.getActiveDoctorIds()).thenReturn(List.of("26", "12"));
    }

    @Test
    @DisplayName("isProcedureEligible: Aceita consultas e bloqueia cirurgias hospitalares sem isenção")
    void testIsProcedureEligible() {
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("1", "Consulta Médica", null)).isTrue();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("2", "Retorno", null)).isTrue();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("3", "Conversar Cirurgia", null)).isTrue();

        // Cirurgias bloqueadas
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("4", "Cirurgia Dr. Murilo", null)).isFalse();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("5", "CIRURGIAS MU", null)).isFalse();
    }

    @Test
    @DisplayName("filterEligibleAppointments: Filtra encaixes e status não elegíveis")
    void shouldFilterEncaixeAndIneligibleStatus() {
        // Encaixe = true -> Deve ser descartado
        FeegowAppointment encaixe = new FeegowAppointment("1", "10", "26", "Dra. Vania", "Matriz", LocalDateTime.now(), "1", "Consulta", "100", true);
        // Status 11 (Cancelado) -> Deve ser descartado
        FeegowAppointment cancelado = new FeegowAppointment("2", "11", "26", "Dra. Vania", "Matriz", LocalDateTime.now(), "11", "Consulta", "100", false);
        // Válido (Status 1, sem encaixe) -> Deve passar
        FeegowAppointment valido = new FeegowAppointment("3", "12", "26", "Dra. Vania", "Matriz", LocalDateTime.now(), "1", "Consulta", "100", false);

        when(appointmentExternalPort.listLocks(any(), any(), any())).thenReturn(List.of());
        when(appointmentFilterService.isScheduleBlocked(any(FeegowAppointment.class), anyList())).thenReturn(false);

        List<FeegowAppointment> result = pipeline.filterEligibleAppointments(List.of(encaixe, cancelado, valido), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("3");
    }
}
