package br.dev.ctrls.itsm.modules.settings.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import br.dev.ctrls.itsm.modules.settings.domain.model.SystemSetting;

public interface SystemSettingJpaRepository extends JpaRepository<SystemSetting, String> {
    List<SystemSetting> findAllByOrderByIdAsc();
}
