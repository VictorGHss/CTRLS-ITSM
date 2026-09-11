package br.dev.ctrls.itsm.modules.settings.application.service;

import br.dev.ctrls.itsm.modules.settings.application.dto.BrandingDTO;
import br.dev.ctrls.itsm.modules.settings.domain.model.SystemSetting;
import br.dev.ctrls.itsm.modules.settings.domain.port.output.SystemSettingRepositoryPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepositoryPort systemSettingRepository;

    @Transactional(readOnly = true)
    public List<SystemSetting> listAll() {
        return systemSettingRepository.findAllByOrderByIdAsc();
    }

    @Transactional
    public List<SystemSetting> updateSettings(Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();

            Optional<SystemSetting> maybe = systemSettingRepository.findById(key);
            if (maybe.isPresent()) {
                SystemSetting setting = maybe.get();
                setting.setValue(value);
                systemSettingRepository.save(setting);
            } else {
                // create new setting when missing (upsert)
                SystemSetting created = SystemSetting.builder().id(key).value(value).description(null).build();
                systemSettingRepository.save(created);
            }
        }

        return systemSettingRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public BrandingDTO getBranding() {
        BrandingDTO defaults = BrandingDTO.defaults();

        String appName = getSettingValue("BRANDING_APP_NAME", defaults.appName());
        String companyName = getSettingValue("BRANDING_COMPANY_NAME", defaults.companyName());
        String logoUrl = getSettingValue("BRANDING_LOGO_URL", defaults.logoUrl());
        String primaryColor = getSettingValue("BRANDING_PRIMARY_COLOR", defaults.primaryColor());
        String primaryDarkColor = getSettingValue("BRANDING_PRIMARY_DARK_COLOR", defaults.primaryDarkColor());
        String secondaryColor = getSettingValue("BRANDING_SECONDARY_COLOR", defaults.secondaryColor());
        String supportEmail = getSettingValue("BRANDING_SUPPORT_EMAIL", defaults.supportEmail());

        return BrandingDTO.builder()
                .appName(appName)
                .companyName(companyName)
                .logoUrl(logoUrl)
                .primaryColor(primaryColor)
                .primaryDarkColor(primaryDarkColor)
                .secondaryColor(secondaryColor)
                .supportEmail(supportEmail)
                .build();
    }

    @Transactional
    public BrandingDTO updateBranding(BrandingDTO dto) {
        if (dto == null) {
            return getBranding();
        }

        Map<String, String> updates = new HashMap<>();
        if (dto.appName() != null) updates.put("BRANDING_APP_NAME", dto.appName());
        if (dto.companyName() != null) updates.put("BRANDING_COMPANY_NAME", dto.companyName());
        if (dto.logoUrl() != null) updates.put("BRANDING_LOGO_URL", dto.logoUrl());
        if (dto.primaryColor() != null) updates.put("BRANDING_PRIMARY_COLOR", dto.primaryColor());
        if (dto.primaryDarkColor() != null) updates.put("BRANDING_PRIMARY_DARK_COLOR", dto.primaryDarkColor());
        if (dto.secondaryColor() != null) updates.put("BRANDING_SECONDARY_COLOR", dto.secondaryColor());
        if (dto.supportEmail() != null) updates.put("BRANDING_SUPPORT_EMAIL", dto.supportEmail());

        updateSettings(updates);
        return getBranding();
    }

    private String getSettingValue(String key, String defaultValue) {
        return systemSettingRepository.findById(key)
                .map(s -> s.getValue())
                .filter(val -> !val.trim().isEmpty())
                .orElse(defaultValue);
    }
}
