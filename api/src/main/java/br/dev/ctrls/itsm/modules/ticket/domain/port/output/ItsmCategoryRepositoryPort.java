package br.dev.ctrls.itsm.modules.ticket.domain.port.output;

import java.util.List;
import java.util.Optional;

import br.dev.ctrls.itsm.modules.ticket.domain.model.ItsmCategory;

public interface ItsmCategoryRepositoryPort {
    ItsmCategory save(ItsmCategory entity);
    Optional<ItsmCategory> findById(Integer id);
    List<ItsmCategory> findAll();
    void deleteById(Integer id);
    boolean existsById(Integer id);
    // Add custom methods manually if needed
}
