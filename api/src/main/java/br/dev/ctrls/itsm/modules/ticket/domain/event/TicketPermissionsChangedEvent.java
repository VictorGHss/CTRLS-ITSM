package br.dev.ctrls.itsm.modules.ticket.domain.event;

import br.dev.ctrls.itsm.modules.ticket.domain.model.Ticket;

/**
 * Domain event published when permissions for a ticket's channel should be synced.
 */
public record TicketPermissionsChangedEvent(Ticket ticket) {
}
