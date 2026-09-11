package br.dev.ctrls.itsm.modules.appointment.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.itsm.modules.appointment.domain.model.FeegowAppointmentStatus;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.utils.AppointmentIdNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável pela reconciliação e invalidação de sessões locais
 * que foram canceladas ou remarcadas no Feegow ERP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentCancellationReconciler {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentExternalPort appointmentExternalPort;
    private final TransactionTemplate transactionTemplate;

    public void reconcile(List<LocalDate> targetDates, List<FeegowAppointment> activeAppointments) {
        Set<String> validFeegowAppointmentIds = activeAppointments.stream()
                .map(a -> AppointmentIdNormalizer.normalize(a.id()))
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        for (LocalDate targetDate : targetDates) {
            try {
                LocalDateTime startOfDay = targetDate.atStartOfDay();
                LocalDateTime endOfDay = targetDate.atTime(23, 59, 59);
                List<AppointmentSession> localSessionsForDate = appointmentSessionRepository.findByAppointmentAtBetween(startOfDay, endOfDay);

                for (AppointmentSession localSession : localSessionsForDate) {
                    String feegowId = localSession.getFeegowAppointmentId();
                    if (feegowId == null || feegowId.isBlank()) continue;

                    AppointmentSessionStatus status = localSession.getStatus();
                    // NUNCA invalidar sessões já confirmadas (seja pelo bot ou pela clínica)
                    if (status == AppointmentSessionStatus.CONFIRMED) {
                        continue;
                    }

                    if (status != AppointmentSessionStatus.CANCELED && status != AppointmentSessionStatus.CANCELED_NO_RESPONSE) {
                        if (!validFeegowAppointmentIds.contains(feegowId.trim())) {
                            // Antes de cancelar, consulta o status real individual no Feegow
                            boolean reconciled = false;
                            try {
                                FeegowAppointment feegowAppt = appointmentExternalPort.findById(feegowId.trim());
                                if (feegowAppt != null) {
                                    String statusId = feegowAppt.statusId() != null ? feegowAppt.statusId().trim() : "";
                                    FeegowAppointmentStatus feegowStatus = FeegowAppointmentStatus.fromId(statusId);
                                    if (feegowStatus.isConfirmedOrPresent()) {
                                        log.info("[INGESTÃO-RECONCILIAÇÃO] Agendamento local ID={} (Feegow ID={}) já está confirmado/atendido no Feegow (statusId={}, {}). Atualizando para CONFIRMED.",
                                                localSession.getId(), feegowId, statusId, feegowStatus.getDescription());
                                        transactionTemplate.execute(txStatus -> {
                                            localSession.setStatus(AppointmentSessionStatus.CONFIRMED);
                                            localSession.setClosedAt(LocalDateTime.now());
                                            localSession.setStatusDetails("CONFIRMED_ON_FEEGOW");
                                            return appointmentSessionRepository.save(localSession);
                                        });
                                        reconciled = true;
                                    } else if (feegowStatus.isEligibleForInitialDispatch()) {
                                        // Permanece agendado no Feegow (apenas não entrou no lote de busca por procedimento ou pauta)
                                        log.info("[INGESTÃO-RECONCILIAÇÃO] Agendamento local ID={} (Feegow ID={}) permanece ativo no Feegow (statusId={}, {}). Mantendo status local {}.",
                                                localSession.getId(), feegowId, statusId, feegowStatus.getDescription(), status);
                                        reconciled = true;
                                    }
                                }
                            } catch (Exception ex) {
                                log.warn("[INGESTÃO-RECONCILIAÇÃO] Falha ao consultar status individual no Feegow para ID {}: {}", feegowId, ex.getMessage());
                            }

                            if (!reconciled) {
                                log.info("[INGESTÃO-INVALIDAÇÃO] Agendamento local ID={} (Feegow ID={}) não consta mais na lista ativa da Feegow para a data {}. Atualizando status local para CANCELED (Remarcado ou Cancelado na Feegow).",
                                        localSession.getId(), feegowId, targetDate);

                                transactionTemplate.execute(txStatus -> {
                                    localSession.setStatus(AppointmentSessionStatus.CANCELED);
                                    localSession.setClosedAt(LocalDateTime.now());
                                    localSession.setStatusDetails("CANCELLED_OR_RESCHEDULED_ON_FEEGOW");
                                    return appointmentSessionRepository.save(localSession);
                                });
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[INGESTÃO-INVALIDAÇÃO] Falha ao verificar invalidação de agendamentos locais para a data {}: {}", targetDate, ex.getMessage());
            }
        }
    }
}
