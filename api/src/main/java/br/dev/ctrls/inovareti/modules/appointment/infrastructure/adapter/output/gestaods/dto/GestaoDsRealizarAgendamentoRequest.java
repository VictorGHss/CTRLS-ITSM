package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de requisição para realização de agendamento no Gestão DS.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GestaoDsRealizarAgendamentoRequest(
        @NotBlank @JsonProperty("data_agendamento") String dataAgendamento,
        @NotBlank @JsonProperty("data_fim_agendamento") String dataFimAgendamento,
        @NotBlank @JsonProperty("cpf") String cpf,
        @NotBlank @JsonProperty("token") String token,
        @JsonProperty("primeiro_atendimento") Boolean primeiroAtendimento
) {}
