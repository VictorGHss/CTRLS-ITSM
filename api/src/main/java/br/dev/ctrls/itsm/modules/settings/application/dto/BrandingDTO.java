package br.dev.ctrls.itsm.modules.settings.application.dto;

import lombok.Builder;

@Builder
public record BrandingDTO(
        String appName,
        String companyName,
        String logoUrl,
        String primaryColor,
        String primaryDarkColor,
        String secondaryColor,
        String supportEmail
) {
    public static BrandingDTO defaults() {
        return BrandingDTO.builder()
                .appName("CTRLS ITSM")
                .companyName("CTRLS Tecnologia")
                .logoUrl("")
                .primaryColor("#feb56c")
                .primaryDarkColor("#f1a154")
                .secondaryColor("#fed8b0")
                .supportEmail("suporte@ctrls.dev.br")
                .build();
    }
}
