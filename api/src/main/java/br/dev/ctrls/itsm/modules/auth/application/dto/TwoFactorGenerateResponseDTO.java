package br.dev.ctrls.itsm.modules.auth.application.dto;

public record TwoFactorGenerateResponseDTO(
        String qrCodeBase64,
        String otpauthUrl
) {
}
