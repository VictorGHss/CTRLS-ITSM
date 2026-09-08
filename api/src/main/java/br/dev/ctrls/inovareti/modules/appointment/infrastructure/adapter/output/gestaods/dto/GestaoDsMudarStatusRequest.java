package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de requisição para alterar status de confirmação ou cancelamento no Gestão DS.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GestaoDsMudarStatusRequest(
        @NotBlank @JsonProperty("token") String token,
        @NotBlank @JsonProperty("agendamento") String agendamento,
        @JsonProperty("confirmado") Boolean confirmado,
        @JsonProperty("cancelado") Boolean cancelado,
        @JsonProperty("motivo_cancelamento") String motivoCancelamento
) {}
