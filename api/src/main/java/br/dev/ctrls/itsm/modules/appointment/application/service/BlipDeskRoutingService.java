package br.dev.ctrls.itsm.modules.appointment.application.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.dev.ctrls.itsm.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipWebhookPayload;
import br.dev.ctrls.itsm.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookResult;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pela lógica de transbordo e roteamento silencioso para o Desk / Atendimento Humano:
 * - Validação de horário comercial (07:00 às 18:30, seg-sex).
 * - Resolução da fila do médico e dados cadastrais do paciente (nome, CPF, nascimento).
 * - Sincronização de contexto e redirecionamento no Blip Desk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlipDeskRoutingService {

    private final BlipContextService blipContextService;
    private final BlipIdentityReconciler blipIdentityReconciler;
    private final BlipWebhookPreprocessor preprocessor;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final PatientExternalPort patientExternalPort;

    public static boolean isWithinBusinessHours() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(LocalTime.of(7, 0)) && !time.isAfter(LocalTime.of(18, 30));
    }

    public WebhookResult applySilentDeskRouting(String fromPhone) {
        return applySilentDeskRouting(fromPhone, null);
    }

    public WebhookResult applySilentDeskRouting(String fromPhone, BlipWebhookPayload payload) {
        if (!isWithinBusinessHours()) {
            log.info("[DESK-ROUTING] Solicitação de transbordo recebida fora do expediente (07:00 às 18:30). Não criando ticket no Desk para o contato: {}", fromPhone);
            return new WebhookResult("", "", "", "", "out_of_hours_ignored", "");
        }

        log.info("[DESK-ROUTING] Processando transbordo para Desk/Atendimento Humano para o contato: {}", fromPhone);

        // 1. Limpa sincronamente variáveis de contexto de confirmação em ambos os escopos (Master e Túnel)
        blipContextService.clearConfirmationContext(fromPhone);

        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, payload != null ? payload.bsuid() : null);
        String searchPhone = (dbPhone != null && !dbPhone.isBlank()) ? dbPhone : fromPhone;
        String purifiedPhone = preprocessor.purifyPhoneNumberForSearch(searchPhone);
        if (purifiedPhone.isEmpty()) {
            purifiedPhone = preprocessor.purifyPhoneNumberForSearch(fromPhone);
        }

        String resolvedQueue = "Recepção Central / Suporte";
        String resolvedPatientName = "";
        String resolvedCpf = "";
        String resolvedDoctorName = "";
        String resolvedBirthdate = "";

        try {
            List<AppointmentSession> activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(purifiedPhone);
            if ((activeSessions == null || activeSessions.isEmpty()) && !purifiedPhone.startsWith("55")) {
                activeSessions = appointmentSessionRepository.findActiveByPhoneNumber("55" + purifiedPhone);
            }

            if (activeSessions != null && !activeSessions.isEmpty()) {
                AppointmentSession session = activeSessions.getFirst();
                if (session.getDoctorProfissionalId() != null && !session.getDoctorProfissionalId().isBlank()) {
                    Optional<AppointmentDoctorMapping> doctorMappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(session.getDoctorProfissionalId());
                    if (doctorMappingOpt.isPresent()) {
                        String queueId = doctorMappingOpt.get().getBlipQueueId();
                        if (queueId != null && !queueId.isBlank()) {
                            resolvedQueue = blipContextService.resolveQueueName(queueId.trim());
                        }
                        if (doctorMappingOpt.get().getProfissionalNome() != null) {
                            resolvedDoctorName = doctorMappingOpt.get().getProfissionalNome();
                        }
                    }
                }

                if (session.getPatientId() != null && !session.getPatientId().isBlank()) {
                    try {
                        var patient = patientExternalPort.patientInfo(session.getPatientId());
                        if (patient != null) {
                            if (patient.name() != null) resolvedPatientName = patient.name();
                            if (patient.cpf() != null) resolvedCpf = patient.cpf().replaceAll("\\D", "");
                            if (patient.birthdate() != null) resolvedBirthdate = patient.birthdate();
                        }
                    } catch (Exception ex) {
                        log.debug("[DESK-ROUTING] Erro ao consultar paciente para {}: {}", session.getPatientId(), ex.getMessage());
                    }
                }

                // Sincroniza redirecionamento de fila para contatos vinculados a um agendamento ativo
                try {
                    blipContextService.setQueueRedirect(fromPhone, resolvedQueue);
                } catch (Exception ex) {
                    log.warn("[DESK-ROUTING] Falha ao configurar redirecionamento de fila para {}: {}", fromPhone, ex.getMessage());
                }
            } else {
                log.info("[DESK-ROUTING] Contato {} sem sessão de agendamento ativa. Mantendo pauta/fila definida pelo fluxo do bot ou contato existente.", fromPhone);
            }
        } catch (Exception ex) {
            log.warn("[DESK-ROUTING] Falha defensiva ao carregar dados do agendamento para Desk: {}", ex.getMessage());
        }

        return new WebhookResult(resolvedQueue, resolvedPatientName, resolvedCpf, resolvedBirthdate, "Atendimento humano", resolvedDoctorName);
    }
}
