package br.dev.ctrls.itsm.modules.settings.infrastructure.adapter.input;

import br.dev.ctrls.itsm.modules.settings.application.dto.BrandingDTO;
import br.dev.ctrls.itsm.modules.settings.application.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class BrandingController {

    private final SystemSettingService systemSettingService;

    @GetMapping({"/v1/branding", "/api/v1/branding"})
    public ResponseEntity<BrandingDTO> getBranding() {
        return ResponseEntity.ok(systemSettingService.getBranding());
    }

    @PutMapping({"/v1/admin/branding", "/api/v1/admin/branding"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BrandingDTO> updateBranding(@RequestBody BrandingDTO brandingDTO) {
        return ResponseEntity.ok(systemSettingService.updateBranding(brandingDTO));
    }
}
