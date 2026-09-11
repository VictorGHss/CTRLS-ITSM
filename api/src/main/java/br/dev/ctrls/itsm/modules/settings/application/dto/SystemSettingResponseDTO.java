package br.dev.ctrls.itsm.modules.settings.application.dto;

import br.dev.ctrls.itsm.modules.settings.domain.model.SystemSetting;

public record SystemSettingResponseDTO(
        String id,
        String value,
        String description
) {
    public static SystemSettingResponseDTO from(SystemSetting setting) {
        return new SystemSettingResponseDTO(
                setting.getId(),
                setting.getValue(),
                setting.getDescription()
        );
    }
}
