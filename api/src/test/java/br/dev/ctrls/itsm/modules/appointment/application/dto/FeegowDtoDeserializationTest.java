package br.dev.ctrls.itsm.modules.appointment.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class FeegowDtoDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("FeegowSearchAppointmentDto: Mapeia procedimento_id sem ser sobrescrito por especialidade_id")
    void shouldCorrectlyMapProcedureIdAndSpecialtyId() throws Exception {
        String json = """
            {
              "agendamento_id": 3467861,
              "data": "26-08-2026",
              "horario": "07:30:00",
              "paciente_id": 241325,
              "procedimento_id": 46,
              "valor": null,
              "status_id": 1,
              "duracao": 0,
              "local_id": 15,
              "profissional_id": 32,
              "especialidade_id": 128,
              "encaixe": false
            }
            """;

        FeegowSearchResponseDto.FeegowSearchAppointmentDto dto =
                objectMapper.readValue(json, FeegowSearchResponseDto.FeegowSearchAppointmentDto.class);

        assertThat(dto.appointmentId()).isEqualTo(3467861);
        assertThat(dto.procedureId()).isEqualTo("46");
        assertThat(dto.specialtyId()).isEqualTo("128");
        assertThat(dto.doctorId()).isEqualTo("32");
        assertThat(dto.patientId()).isEqualTo("241325");
        assertThat(dto.statusId()).isEqualTo(1);
    }

    @Test
    @DisplayName("FeegowLockDto: Deserializa bloqueios com lock_type_id e propriedades desconhecidas sem falhar")
    void shouldDeserializeFeegowLockDtoWithExtraFields() throws Exception {
        String json = """
            [
              {
                "id": 100,
                "lock_type_id": 3,
                "date_start": "2026-08-26",
                "date_end": "2026-08-26",
                "time_start": "08:00:00",
                "time_end": "12:00:00",
                "holiday_id": null,
                "professional_id": 32,
                "description": "Férias",
                "week_day": ["3"],
                "units": ["18"],
                "unknown_field_from_feegow": 12345
              }
            ]
            """;

        List<FeegowLockDto> locks = objectMapper.readValue(json, new TypeReference<List<FeegowLockDto>>() {});

        assertThat(locks).hasSize(1);
        FeegowLockDto lock = locks.get(0);
        assertThat(lock.id()).isEqualTo(100L);
        assertThat(lock.lockTypeId()).isEqualTo(3L);
        assertThat(lock.professionalId()).isEqualTo(32L);
        assertThat(lock.dateStart()).isEqualTo("2026-08-26");
        assertThat(lock.description()).isEqualTo("Férias");
    }
}
