package br.dev.ctrls.itsm.modules.ticket.domain.event;

import br.dev.ctrls.itsm.modules.ticket.domain.model.Ticket;

/**
 * Domain event published when a ticket is resolved.
 */
public record TicketResolvedEvent(Ticket ticket) {
}
