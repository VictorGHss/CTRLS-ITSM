package br.dev.ctrls.itsm.modules.ticket.application.dto;

import java.util.UUID;

import br.dev.ctrls.itsm.modules.ticket.domain.model.TicketCategory;

/**
 * DTO de saída com os dados de uma categoria de chamado.
 */
public record TicketCategoryResponseDTO(
        UUID id,
        String name,
        Integer baseSlaHours
) {
    /** Converte uma entidade {@link TicketCategory} para este DTO. */
    public static TicketCategoryResponseDTO from(TicketCategory category) {
        return new TicketCategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.getBaseSlaHours()
        );
    }
}
