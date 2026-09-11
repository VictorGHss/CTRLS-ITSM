package br.dev.ctrls.itsm.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.model.FeegowAppointmentStatus;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável por gerenciar a resposta do usuário a mensagens de Nudge do Blip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class BlipNudgeResponseHandler {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final AppointmentExternalPort appointmentExternalPort;
    private final BlipProperties blipProperties;
    private final BlipContextService blipContextService;
    private final BlipIdentityReconciler blipIdentityReconciler;

    /**
     * Intercepta a resposta do nudge do WhatsApp para manter ou cancelar a consulta,
     * executando os status na Feegow e redirecionando para suporte humano (Desk).
     */
    public boolean handleNudgeResponse(String normalizedAction, String action, String fromPhone, String bsuid) {
        boolean isManterAgendamento = normalizedAction.equals("manter agendamento")
                || normalizedAction.contains("manter_agendamento");
        boolean isCancelarConsulta = normalizedAction.equals("cancelar consulta")
                || normalizedAction.contains("cancelar_consulta");

        if (!isManterAgendamento && !isCancelarConsulta) {
            return false;
        }

        if (fromPhone != null && !fromPhone.isBlank()) {
            String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, bsuid);
            log.info("[WEBHOOK-NUDGE] Interceptando resposta do nudge: '{}' para o telefone: {} (DB Phone: {})",
                action, fromPhone, dbPhone);
            
            List<AppointmentSession> activeSessions = transactionTemplate.execute(status -> 
                appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
            );
            
            if (activeSessions != null && !activeSessions.isEmpty()) {
                log.info("[WEBHOOK-NUDGE] Encontradas {} sessões ativas para processar.", activeSessions.size());
                for (AppointmentSession session : activeSessions) {
                    processSessionUpdate(session, isManterAgendamento);
                }
                transferToDesk(fromPhone);
            } else {
                log.warn("[WEBHOOK-NUDGE] Resposta de Nudge '{}' recebida de {}, mas nenhuma sessão ativa encontrada.",
                    action, fromPhone);
            }
        } else {
            log.warn("[WEBHOOK-NUDGE] Resposta de Nudge '{}' recebida sem 'fromPhone' identificado.", action);
        }
        return true;
    }

    private void transferToDesk(String fromPhone) {
        blipContextService.clearConfirmationContext(fromPhone);
        String deskBlockId = blipProperties.getBlocks().getDeskStateId();
        blipContextService.setMasterState(fromPhone, "desk@msging.net", deskBlockId);
        log.info("[WEBHOOK-NUDGE] Transbordo concluído para {} direcionando ao Bloco: 'desk:{}'", fromPhone, deskBlockId);
    }

    private void processSessionUpdate(AppointmentSession session, boolean isManterAgendamento) {
        try {
            if (isManterAgendamento) {
                log.info("[WEBHOOK-NUDGE] Confirmando sessão local e Feegow para sessionId={}, feegowAppointmentId={}",
                    session.getId(), session.getFeegowAppointmentId());
                transactionTemplate.executeWithoutResult(status -> {
                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                    if (lockedSession != null) {
                        confirmationStateMachineService.markConfirmed(lockedSession);
                        appointmentSessionRepository.save(lockedSession);
                    }
                });
                appointmentExternalPort.updateAppointmentStatus(
                    session.getFeegowAppointmentId(),
                    String.valueOf(FeegowAppointmentStatus.MARCADO_CONFIRMADO.getId())
                );
            } else {
                log.info("[WEBHOOK-NUDGE] Paciente solicitou cancelamento via WhatsApp. Atualizando sessão local para CANCELED para suspender lembretes automáticos sem remover da agenda Feegow. sessionId={}, feegowAppointmentId={}",
                    session.getId(), session.getFeegowAppointmentId());
                transactionTemplate.executeWithoutResult(status -> {
                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                    if (lockedSession != null) {
                        confirmationStateMachineService.markCanceled(lockedSession);
                        appointmentSessionRepository.save(lockedSession);
                    }
                });
                // NÃO executa o cancelamento/desmarcação na API Feegow para manter o paciente visível na grade da agenda
                // permitindo que as secretárias façam o contato, remanejamento ou cancelamento manual seguro.
            }
        } catch (TransactionException | RestClientException | DataAccessException ex) {
            log.error("[WEBHOOK-NUDGE] Falha ao atualizar sessão no lote. sessionId={}, erro={}",
                session.getId(), ex.getMessage(), ex);
        }
    }
}


