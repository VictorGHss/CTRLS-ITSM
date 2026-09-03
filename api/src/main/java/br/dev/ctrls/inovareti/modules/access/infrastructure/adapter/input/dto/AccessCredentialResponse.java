package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto;

import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import java.util.UUID;

/**
 * DTO de resposta contendo os dados da credencial para o portal React.
 * Traduzido para o inglês seguindo as Regras de Nomenclatura Cruciais.
 * Comentários mantidos em PT-BR.
 */
public record AccessCredentialResponse(
    String appointmentId,
    String name,
    UserType userType,
    String locator,
    String credentialCode,
    String cpf,
    String doctorName,
    String appointmentDateTime,
    String opensAt,
    String closesAt,
    UUID id
) {
    public AccessCredentialResponse(
        String appointmentId,
        String name,
        UserType userType,
        String locator,
        String credentialCode,
        String cpf,
        String doctorName,
        String appointmentDateTime,
        String opensAt,
        String closesAt
    ) {
        this(appointmentId, name, userType, locator, credentialCode, cpf, doctorName, appointmentDateTime, opensAt, closesAt, null);
    }
}
