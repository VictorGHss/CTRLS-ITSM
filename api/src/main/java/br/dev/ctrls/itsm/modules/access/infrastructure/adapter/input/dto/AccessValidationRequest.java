package br.dev.ctrls.itsm.modules.access.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * Representa o payload JSON recebido para validação e liberação de acesso físico.
 * Comentários mantidos em PT-BR.
 */
public record AccessValidationRequest(
    @NotBlank(message = "O ID do agendamento é obrigatório")
    String appointmentId,
    
    String cpf,
    
    List<CompanionRequest> companions,

    UUID credentialId,

    String targetName,

    String userType
) {
    public AccessValidationRequest(String appointmentId, String cpf, List<CompanionRequest> companions) {
        this(appointmentId, cpf, companions, null, null, null);
    }
}
