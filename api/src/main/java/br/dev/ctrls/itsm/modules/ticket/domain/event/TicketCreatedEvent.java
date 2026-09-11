package br.dev.ctrls.itsm.modules.ticket.domain.event;

import java.util.List;
import br.dev.ctrls.itsm.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.itsm.modules.user.domain.model.User;

/**
 * Domain event published when a new ticket is created.
 */
public record TicketCreatedEvent(Ticket ticket, List<User> assignedUsers) {
}
