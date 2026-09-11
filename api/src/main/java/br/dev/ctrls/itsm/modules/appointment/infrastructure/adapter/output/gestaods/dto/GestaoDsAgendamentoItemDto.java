package br.dev.ctrls.itsm.modules.appointment.infrastructure.adapter.output.gestaods.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para agendamento individual retornado pela API do Gestão DS.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GestaoDsAgendamentoItemDto(
        @JsonProperty("agendamento") @JsonAlias({"id", "agendamento_id", "codigo", "token_agendamento"}) String agendamento,
        @JsonProperty("data_agendamento") @JsonAlias({"data", "inicio", "data_inicio", "start_at", "startAt"}) String dataAgendamento,
        @JsonProperty("data_fim_agendamento") @JsonAlias({"fim", "data_fim", "end_at", "endAt"}) String dataFimAgendamento,
        @JsonProperty("confirmado") @JsonAlias({"is_confirmado", "status_confirmado"}) Boolean confirmado,
        @JsonProperty("cancelado") @JsonAlias({"is_cancelado", "status_cancelado"}) Boolean cancelado,
        @JsonProperty("motivo_cancelamento") String motivoCancelamento,
        @JsonProperty("profissional_nome") @JsonAlias({"profissional", "medico", "medico_nome", "doctor_name", "nome_medico", "nome_profissional"}) String profissionalNome,
        @JsonProperty("profissional_id") @JsonAlias({"medico_id", "doctor_id"}) String profissionalId,
        @JsonProperty("procedimento_nome") @JsonAlias({"procedimento", "especialidade", "nome_procedimento", "procedure_name"}) String procedimentoNome,
        @JsonProperty("paciente_nome") @JsonAlias({"paciente", "nome_paciente", "patient_name"}) String pacienteNome,
        @JsonProperty("paciente_cpf") @JsonAlias({"cpf", "paciente_cpf", "patient_cpf"}) String pacienteCpf,
        @JsonProperty("unidade") @JsonAlias({"local", "clinica", "unidade_nome"}) String unidade,
        @JsonProperty("primeiro_atendimento") Boolean primeiroAtendimento
) {
    /**
     * Verifica se o agendamento está ativo (não cancelado).
     */
    public boolean isAtivo() {
        return cancelado == null || !cancelado;
    }
}
