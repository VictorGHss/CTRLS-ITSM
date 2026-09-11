package br.dev.ctrls.itsm.modules.ticket.infrastructure.adapter.output.jpa.repository;
import br.dev.ctrls.itsm.modules.ticket.domain.model.ItsmCategory;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório JPA para {@link ItsmCategory}.
 */
public interface ItsmCategoryJpaRepository extends JpaRepository<ItsmCategory, Integer> {

    /** Busca uma categoria ITSM pelo nome exato. */
    Optional<ItsmCategory> findByName(String name);
}
