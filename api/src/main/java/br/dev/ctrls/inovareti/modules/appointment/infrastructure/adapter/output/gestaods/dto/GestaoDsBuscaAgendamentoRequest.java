package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de requisição para busca de agendamentos do paciente no Gestão DS.
 */
public record GestaoDsBuscaAgendamentoRequest(
        @NotBlank @JsonProperty("cpf") String cpf,
        @NotBlank @JsonProperty("token") String token
) {}
