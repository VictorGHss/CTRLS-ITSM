package br.dev.ctrls.itsm.modules.access.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;

public record CpfLookupRequest(
    @NotBlank(message = "O CPF é obrigatório")
    String cpf,

    String clinic
) {}
