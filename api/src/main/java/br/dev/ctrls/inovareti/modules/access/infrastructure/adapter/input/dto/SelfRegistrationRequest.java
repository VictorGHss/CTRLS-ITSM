package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SelfRegistrationRequest(
    @NotBlank(message = "O nome do paciente é obrigatório")
    @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres")
    String name,

    @NotBlank(message = "O CPF é obrigatório")
    String cpf,

    String phone,

    String birthDate,

    String clinic,

    String visitDate,

    String doctorName,

    String appointmentId,

    CompanionRequest companion,

    List<CompanionRequest> companions
) {}
