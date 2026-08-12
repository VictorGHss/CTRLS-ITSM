package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso/serviço responsável pela validação, re-checagem estrita e deduplicação no envio de lembretes de agendamentos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendAppointmentReminderUseCase {

    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;

    /**
     * Re-valida obrigatoriamente o agendamento na API da Feegow antes de efetuar o disparo do lembrete (ex: lembrete_ativo_itsm_v1).
     * Confirma se o agendamento ainda existe na Feegow, se a data/hora corresponde exatamente à gravada no banco local
     * e se o status_id não é de cancelamento ou reagendamento.
     *
     * @param session Sessão de agendamento local
     * @return true se o agendamento permanece válido e ativo na Feegow; false se foi remarcado/cancelado.
     */
    public boolean validateAndRecheckAppointmentOnFeegow(AppointmentSession session) {
        if (session == null || session.getFeegowAppointmentId() == null || session.getFeegowAppointmentId().isBlank()) {
            return true;
        }

        String feegowId = session.getFeegowAppointmentId().trim();
        try {
            var feegowAppt = appointmentExternalPort.findById(feegowId);
            if (feegowAppt == null) {
                log.info("[LEMBRETE-CANCELADO] Disparo ignorado para o agendamento ID={}. Motivo: Consulta remarcada ou cancelada na Feegow.", feegowId);
                invalidateLocalSession(session, "NOT_FOUND_ON_FEEGOW");
                return false;
            }

            String statusId = feegowAppt.statusId() != null ? feegowAppt.statusId().trim() : "";
            // Status de cancelamento/desmarcação conhecidos no Feegow: 6 (Não compareceu), 11 (Desmarcado pelo paciente), 12 (Desmarcado clínica)
            if ("6".equals(statusId) || "11".equals(statusId) || "12".equals(statusId)) {
                log.info("[LEMBRETE-CANCELADO] Disparo ignorado para o agendamento ID={}. Motivo: Consulta remarcada ou cancelada na Feegow.", feegowId);
                invalidateLocalSession(session, "CANCELLED_ON_FEEGOW_STATUS_" + statusId);
                return false;
            }

            // Confirmação estrita de data e hora agendada
            if (feegowAppt.startAt() != null && session.getAppointmentAt() != null) {
                if (!feegowAppt.startAt().equals(session.getAppointmentAt())) {
                    log.info("[LEMBRETE-CANCELADO] Disparo ignorado para o agendamento ID={}. Motivo: Consulta remarcada ou cancelada na Feegow.", feegowId);
                    invalidateLocalSession(session, "RESCHEDULED_ON_FEEGOW_TO_" + feegowAppt.startAt());
                    return false;
                }
            }
        } catch (Exception ex) {
            log.warn("[LEMBRETE-VALIDACAO] Falha ao re-validar agendamento ID={} na Feegow pré-disparo: {}", feegowId, ex.getMessage());
        }

        return true;
    }

    private void invalidateLocalSession(AppointmentSession session, String reason) {
        try {
            session.setStatus(AppointmentSessionStatus.CANCELED);
            session.setClosedAt(LocalDateTime.now());
            session.setStatusDetails(reason);
            appointmentSessionRepository.save(session);
        } catch (Exception ex) {
            log.error("[LEMBRETE-INVALIDACAO] Falha ao salvar invalidação da sessão ID={}: {}", session.getId(), ex.getMessage());
        }
    }

    /**
     * Verifica se um lembrete de grupo já foi disparado para o telefone no dia atual (LocalDate.now()).
     *
     * @param phone número do telefone (normalizado ou com DDI)
     * @return true se o envio de grupo já ocorreu hoje (deve ser ignorado); false caso contrário.
     */
    public boolean isGroupReminderAlreadySentToday(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        String cleanPhone = phone.trim();
        try {
            Optional<NotificationGroup> latestOpt = notificationGroupRepository.findLatestByPhone(cleanPhone);
            if (latestOpt.isPresent() && latestOpt.get().getCreatedAt() != null) {
                LocalDateTime lastCreatedAt = latestOpt.get().getCreatedAt();
                if (lastCreatedAt.toLocalDate().equals(LocalDate.now())) {
                    log.info("[LEMBRETE-GRUPO] Telefone {} já recebeu aviso de agendamento de grupo hoje. Disparo duplicado ignorado.", cleanPhone);
                    return true;
                }
            }
        } catch (Exception ex) {
            log.warn("[LEMBRETE-GRUPO] Falha ao verificar deduplicação de grupo para {}: {}", cleanPhone, ex.getMessage());
        }

        return false;
    }
}
