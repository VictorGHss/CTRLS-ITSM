package br.dev.ctrls.itsm.modules.finance.domain.port;

import br.dev.ctrls.itsm.modules.finance.domain.model.SystemAlert;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAlertRepository extends JpaRepository<SystemAlert, UUID> {

	List<SystemAlert> findAllByOrderByCreatedAtDesc();
}

