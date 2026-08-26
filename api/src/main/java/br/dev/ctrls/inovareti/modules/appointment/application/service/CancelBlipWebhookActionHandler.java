package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estratégia de processamento específica para a ação de cancelamento de consulta ("cancel").
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class CancelBlipWebhookActionHandler implements BlipWebhookActionHandler {

    private final ConfirmationStateMachineService confirmationStateMachineService;

    @Override
    public boolean supports(String actionType) {
        return "cancel".equalsIgnoreCase(actionType);
    }

    @Override
    public void prePersistence(AppointmentSession session, String action, String fromIdentity) {
        log.info("[CANCEL] Paciente solicita cancelamento no WhatsApp (sessionId={}). Mantendo agendamento na grade do Feegow para remanejamento humano.", session.getId());
    }

    @Override
    public void applySessionState(AppointmentSession session, String action, String fromIdentity) {
        confirmationStateMachineService.markCanceled(session);
    }
}


