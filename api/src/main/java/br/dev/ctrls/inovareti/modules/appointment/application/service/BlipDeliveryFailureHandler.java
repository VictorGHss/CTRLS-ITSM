package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipDeliveryFailureCommand;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipDeliveryFailureRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.metrics.BlipNotificationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler especializado no tratamento e registro de falhas de entrega de mensagens do Blip:
 * - Incrementa métricas Micrometer.
 * - Registra logs em nível ERROR com contexto MDC.
 * - Persiste a falha no banco de dados e atualiza o status da sessão para ERROR_DELIVERY.
 * - Registra trilha de auditoria.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlipDeliveryFailureHandler {

    private final BlipNotificationMetrics blipNotificationMetrics;
    private final BlipDeliveryFailureRepositoryPort blipDeliveryFailureRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AuditPort auditPort;

    public void executeNotificationFailure(BlipDeliveryFailureCommand failureCommand) {
        if (failureCommand == null) {
            log.warn("Tentativa de processar falha de entrega com comando nulo.");
            return;
        }

        // Injeta os metadados da falha no MDC
        MDC.put("blipMessageId", failureCommand.messageId());
        MDC.put("blipErrorCode", failureCommand.errorCode() != null ? failureCommand.errorCode().toString() : "UNKNOWN");
        MDC.put("appointmentId", failureCommand.appointmentId() != null ? failureCommand.appointmentId() : "N/A");

        try {
            // Dispara log detalhado nível ERROR para monitoramento de SRE
            log.error("[BLIP-DELIVERY-FAILURE] ❌ Falha crítica de entrega de mensagem Blip! " +
                      "MessageID: '{}' | AppointmentID: '{}' | Código: '{}' | Motivo: '{}' | TraceID: '{}'",
                      failureCommand.messageId(),
                      failureCommand.appointmentId(),
                      failureCommand.errorCode(),
                      failureCommand.errorMessage(),
                      failureCommand.traceId());

            // Atualiza métricas de observabilidade via Micrometer
            blipNotificationMetrics.incrementFailureCount(failureCommand.errorCode(), failureCommand.errorMessage());

            // Persiste a falha no banco de dados através da porta de saída
            BlipDeliveryFailure domainModel = BlipDeliveryFailure.builder()
                    .messageId(failureCommand.messageId())
                    .appointmentId(failureCommand.appointmentId())
                    .errorCode(failureCommand.errorCode())
                    .errorMessage(failureCommand.errorMessage())
                    .traceId(failureCommand.traceId())
                    .build();

            blipDeliveryFailureRepository.save(domainModel);

            // Busca a sessão do agendamento correspondente e atualiza para ERROR_DELIVERY
            if (failureCommand.appointmentId() != null && !failureCommand.appointmentId().isBlank()) {
                log.error("[MOTOR-MENSAGERIA] Falha crítica de entrega notificada pela Blip. Atualizando agendamento ID: {} para ERROR_DELIVERY. Motivo: {}",
                        failureCommand.appointmentId(), failureCommand.errorMessage());
                Optional<AppointmentSession> sessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(failureCommand.appointmentId());
                if (sessionOpt.isPresent()) {
                    AppointmentSession session = sessionOpt.get();
                    session.setStatus(AppointmentSessionStatus.ERROR_DELIVERY);
                    session.setStatusDetails(String.format("Erro de entrega Blip: %s - %s",
                            failureCommand.errorCode(), failureCommand.errorMessage()));
                    appointmentSessionRepository.save(session);
                } else {
                    log.warn("[MOTOR-MENSAGERIA] Sessão não encontrada para atualizar status de falha de entrega. Agendamento ID: {}", failureCommand.appointmentId());
                }
            }

            // Registra o evento no histórico de auditoria
            auditPort.record(
                    "APPOINTMENT_MOTOR",
                    "BLIP_DELIVERY_FAILURE",
                    String.format("Falha de entrega Blip para agendamento %s. Código: %s - %s",
                            failureCommand.appointmentId(), failureCommand.errorCode(), failureCommand.errorMessage()),
                    failureCommand.traceId()
            );

        } finally {
            // Limpa as chaves inseridas no MDC para evitar poluição de thread em requisições concorrentes
            MDC.remove("blipMessageId");
            MDC.remove("blipErrorCode");
            MDC.remove("appointmentId");
        }
    }
}
