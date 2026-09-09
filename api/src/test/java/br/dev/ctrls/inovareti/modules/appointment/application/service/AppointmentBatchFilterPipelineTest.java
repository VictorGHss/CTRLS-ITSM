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

    @Mock
    private DoctorEligibilityService doctorEligibilityService;

    @InjectMocks
    private AppointmentBatchFilterPipeline pipeline;

    @BeforeEach
    void setUp() {
        when(appointmentMotorProperties.getTestDoctorIds()).thenReturn(List.of("10", "20"));
        when(appointmentMotorProperties.getActiveDoctorIds()).thenReturn(List.of("26", "12"));
        when(appointmentMotorProperties.getEligibleProcedureIds()).thenReturn("1,2,16,17,100");
        when(doctorEligibilityService.isDoctorAllowed(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("isProcedureEligible: Validação estrita por ID configurado na .env")
    void testIsProcedureEligible() {
        List<String> allowedIds = List.of("1", "2", "16", "17", "100");

        // IDs permitidos configurados no .env
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("1", "Consulta Médica", allowedIds)).isTrue();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("2", "Retorno", allowedIds)).isTrue();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("16", "Telemedicina", allowedIds)).isTrue();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("100", "Qualquer Nome Autorizado", allowedIds)).isTrue();

        // IDs que não constam no .env (cirurgias hospitalares, recados, tarefas, etc.)
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("55", "Cirurgia Dr. Murilo", allowedIds)).isFalse();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("999", "Recado", allowedIds)).isFalse();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("888", "Tarefa", allowedIds)).isFalse();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("", "Consulta", allowedIds)).isFalse();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible(null, "Consulta", allowedIds)).isFalse();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("1", "Consulta", null)).isFalse();
        assertThat(AppointmentBatchFilterPipeline.isProcedureEligible("1", "Consulta", List.of())).isFalse();
    }

    @Test
    @DisplayName("filterEligibleAppointments: Filtra encaixes e status não elegíveis")
    void shouldFilterEncaixeAndIneligibleStatus() {
        // Encaixe = true -> Deve ser descartado
        FeegowAppointment encaixe = new FeegowAppointment("1", "10", "26", "Dra. Vania", "Matriz", LocalDateTime.now(), "1", "Consulta", "100", true);
        // Status 11 (Cancelado) -> Deve ser descartado
        FeegowAppointment cancelado = new FeegowAppointment("2", "11", "26", "Dra. Vania", "Matriz", LocalDateTime.now(), "11", "Consulta", "100", false);
        // Válido (Status 1, sem encaixe, procedimento 100 presente no .env) -> Deve passar
        FeegowAppointment valido = new FeegowAppointment("3", "12", "26", "Dra. Vania", "Matriz", LocalDateTime.now(), "1", "Consulta", "100", false);

        when(appointmentExternalPort.listLocks(any(), any(), any())).thenReturn(List.of());
        when(appointmentFilterService.isScheduleBlocked(any(FeegowAppointment.class), anyList())).thenReturn(false);

        List<FeegowAppointment> result = pipeline.filterEligibleAppointments(List.of(encaixe, cancelado, valido), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("3");
    }
}
