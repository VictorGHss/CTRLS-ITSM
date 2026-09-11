package br.dev.ctrls.itsm.modules.ticket.application.usecase;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.itsm.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.itsm.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.itsm.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.itsm.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.itsm.modules.ticket.domain.port.output.DiscordTicketPort;
import br.dev.ctrls.itsm.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.itsm.modules.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso responsável por unificar um chamado filho a um chamado pai/mestre.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkTicketUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final DiscordTicketPort discordTicketPort;

    @Transactional
    public TicketResponseDTO execute(UUID childId, UUID parentTicketId) {
        Ticket childTicket = ticketRepository.findById(childId)
                .orElseThrow(() -> new NotFoundException("Chamado filho não encontrado com id: " + childId));

        Ticket parentTicket = ticketRepository.findById(parentTicketId)
                .orElseThrow(() -> new NotFoundException("Chamado pai não encontrado com id: " + parentTicketId));

        // Marca vínculo e encerra o chamado filho como unificado/resolvido
        childTicket.setParentTicketId(parentTicketId);
        childTicket.setStatus(TicketStatus.RESOLVED);
        childTicket.setClosedAt(LocalDateTime.now());
        childTicket.setSolutionText("Chamado unificado ao Chamado Mestre #" + parentTicket.getNumber() + " (" + parentTicket.getTitle() + ").");

        if (childTicket.getRelatedTickets() == null) {
            childTicket.setRelatedTickets(new HashSet<>());
        }
        if (parentTicket.getRelatedTickets() == null) {
            parentTicket.setRelatedTickets(new HashSet<>());
        }

        childTicket.getRelatedTickets().add(parentTicket);
        parentTicket.getRelatedTickets().add(childTicket);

        // Migra solicitante e usuários adicionais do chamado filho para os usuários adicionais do chamado pai
        if (parentTicket.getAdditionalUsers() == null) {
            parentTicket.setAdditionalUsers(new HashSet<>());
        }
        if (childTicket.getRequester() != null && !childTicket.getRequester().getId().equals(parentTicket.getRequester().getId())) {
            parentTicket.getAdditionalUsers().add(childTicket.getRequester());
        }
        if (childTicket.getAdditionalUsers() != null) {
            for (User u : childTicket.getAdditionalUsers()) {
                if (!u.getId().equals(parentTicket.getRequester().getId())) {
                    parentTicket.getAdditionalUsers().add(u);
                }
            }
        }

        Ticket savedChild = ticketRepository.save(childTicket);
        ticketRepository.save(parentTicket);

        log.info("[LINK-TICKET] Chamado #{} (filho) unificado e encerrado no Chamado Mestre #{} (pai)",
                savedChild.getNumber(), parentTicket.getNumber());

        // Publica notificação e arquiva canal no Discord
        try {
            discordTicketPort.notifyMerged(savedChild, parentTicket);
        } catch (Exception ex) {
            log.error("[LINK-TICKET] Falha ao notificar unificação no Discord para o chamado #{}", savedChild.getNumber(), ex);
        }

        return TicketResponseDTO.from(savedChild);
    }
}
