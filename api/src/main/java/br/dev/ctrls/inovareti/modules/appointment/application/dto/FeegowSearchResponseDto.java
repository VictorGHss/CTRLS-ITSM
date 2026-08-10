package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeegowSearchResponseDto(
        @JsonProperty("content") List<FeegowSearchAppointmentDto> content,
        @JsonProperty("data") List<FeegowSearchAppointmentDto> data) {

    public List<FeegowSearchAppointmentDto> appointments() {
        if (content != null && !content.isEmpty()) {
            return content;
        }

        if (data != null && !data.isEmpty()) {
            return data;
        }

        if (content != null) {
            return content;
        }

        if (data != null) {
            return data;
        }

        return List.of();
    }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record FeegowSearchAppointmentDto(
            @JsonProperty("agendamento_id") @JsonAlias({"id", "agendamento_id", "agendamentoId"}) Object appointmentId,
            @JsonProperty("paciente_id") @JsonAlias({"patientId", "paciente_id", "pacienteId"}) String patientId,
            @JsonProperty("profissional_id") @JsonAlias({"doctorId", "profissional_id", "profissionalId"}) String doctorId,
            @JsonProperty("nome") String doctorName,
            @JsonProperty("unidade") String unitName,
            @JsonProperty("data") String appointmentDate,
            @JsonProperty("horario") String appointmentTime,
            @JsonProperty("status_id") @JsonAlias({"statusId", "status_id", "status"}) Object statusId,
            @JsonProperty("procedimento_nome") @JsonAlias({"procedimento", "procedimento_nome", "procedimentoNome"}) String procedureName,
            @JsonProperty("procedimento_id") @JsonAlias({"procedimentoId", "procedimento_id"}) String procedureId,
            @JsonProperty("encaixe") Object encaixe) {
        }
}
