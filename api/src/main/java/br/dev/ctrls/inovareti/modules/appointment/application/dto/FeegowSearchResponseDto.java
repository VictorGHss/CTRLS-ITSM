package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeegowSearchResponseDto(
        @JsonProperty("content") @JsonAlias({"content", "data", "result", "appointments", "agendamentos", "Consultas"}) List<FeegowSearchAppointmentDto> content,
        @JsonProperty("data") List<FeegowSearchAppointmentDto> data) {

    public List<FeegowSearchAppointmentDto> appointments() {
        if (content != null && !content.isEmpty()) {
            return content;
        }

        if (data != null && !data.isEmpty()) {
            return data;
        }

        return List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeegowSearchAppointmentDto(
            @JsonProperty("agendamento_id") @JsonAlias({"id", "agendamento_id", "agendamentoId", "appointment_id", "appointmentId"}) Object appointmentId,
            @JsonProperty("paciente_id") @JsonAlias({"patientId", "paciente_id", "pacienteId", "patient_id"}) String patientId,
            @JsonProperty("profissional_id") @JsonAlias({"doctorId", "profissional_id", "profissionalId", "doctor_id", "medico_id", "medicoId"}) String doctorId,
            @JsonProperty("nome_profissional") @JsonAlias({"nome_profissional", "profissional_nome", "nome_medico", "medico", "profissional", "nome", "doctor_name", "doctorName"}) String doctorName,
            @JsonProperty("unidade") @JsonAlias({"unidade", "unidade_nome", "local", "local_nome", "unit_name", "unitName"}) String unitName,
            @JsonProperty("data") @JsonAlias({"data", "data_agendamento", "date", "appointment_date", "appointmentDate"}) String appointmentDate,
            @JsonProperty("horario") @JsonAlias({"horario", "hora", "hora_agendamento", "time", "appointment_time", "appointmentTime"}) String appointmentTime,
            @JsonProperty("status_id") @JsonAlias({"statusId", "status_id", "status", "statusID"}) Object statusId,
            @JsonProperty("procedimento_nome") @JsonAlias({"procedimento_nome", "procedimento", "procedimentoNome", "procedure_name", "procedureName", "procedimento_descricao", "especialidade_nome", "especialidade", "nome_especialidade"}) String procedureName,
            @JsonProperty("procedimento_id") @JsonAlias({"procedimento_id", "procedimentoId", "procedure_id", "procedureId"}) String procedureId,
            @JsonProperty("especialidade_id") @JsonAlias({"especialidade_id", "especialidadeId"}) String specialtyId,
            @JsonProperty("encaixe") Object encaixe) {
    }
}
