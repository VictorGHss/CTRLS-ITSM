package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso/serviço responsável pela validação e deduplicação estrita no envio de lembretes de agendamentos de grupo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendAppointmentReminderUseCase {

    private final NotificationGroupRepositoryPort notificationGroupRepository;

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
