package br.dev.ctrls.itsm.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.dev.ctrls.itsm.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.itsm.modules.access.domain.service.AccessService;
import br.dev.ctrls.itsm.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipWebhookPayload;
import br.dev.ctrls.itsm.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookResult;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler especializado na integração física com o controle de acesso GerAcesso:
 * - Integrar_GerAcesso: valida médico e gera QR Code / autorização física na catraca.
 * - Finalizar_Agendamento e Não: persistência final de credenciais para paciente e acompanhantes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlipPhysicalAccessHandler {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final AccessService accessService;
    private final BlipContextService blipContextService;
    private final br.dev.ctrls.itsm.modules.appointment.domain.port.output.PatientExternalPort patientExternalPort;
    private final DoctorEligibilityService doctorEligibilityService;

    private String resolveAppointmentIdFallback(String fromPhone, String currentAppId) {
        if (currentAppId != null && !currentAppId.isBlank() && !"null".equalsIgnoreCase(currentAppId.trim())) {
            return currentAppId.trim();
        }
        if (fromPhone == null || fromPhone.isBlank()) {
            return null;
        }

        String ctxAppId = blipContextService.getUserContext(fromPhone, "idAgendamentoFeegow");
        if (ctxAppId != null && !ctxAppId.isBlank() && !"null".equalsIgnoreCase(ctxAppId.trim())) {
            return ctxAppId.trim();
        }
        ctxAppId = blipContextService.getUserContext(fromPhone, "appointmentId");
        if (ctxAppId != null && !ctxAppId.isBlank() && !"null".equalsIgnoreCase(ctxAppId.trim())) {
            return ctxAppId.trim();
        }

        try {
            List<AppointmentSession> activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(fromPhone);
            if (activeSessions != null && !activeSessions.isEmpty()) {
                for (AppointmentSession s : activeSessions) {
                    if (s.getFeegowAppointmentId() != null && !s.getFeegowAppointmentId().isBlank()) {
                        log.info("[FALLBACK-APPID] Agendamento ID {} recuperado da sessão ativa para o telefone {}", s.getFeegowAppointmentId(), fromPhone);
                        return s.getFeegowAppointmentId().trim();
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[FALLBACK-APPID] Erro ao consultar sessão ativa por telefone: {}", ex.getMessage());
        }

        return null;
    }

    private String resolvePatientCpfFallback(String fromPhone, String appointmentId, String currentCpf) {
        if (currentCpf != null && !currentCpf.isBlank() && !"null".equalsIgnoreCase(currentCpf.trim())) {
            return currentCpf.trim();
        }
        if (appointmentId != null && !appointmentId.isBlank()) {
            var sessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId);
            if (sessionOpt.isPresent() && sessionOpt.get().getPatientId() != null) {
                try {
                    var patient = patientExternalPort.patientInfo(sessionOpt.get().getPatientId());
                    if (patient != null && patient.cpf() != null && !patient.cpf().isBlank()) {
                        return patient.cpf().trim();
                    }
                } catch (Exception ignored) {}
            }
        }
        if (fromPhone != null) {
            String ctxCpf = blipContextService.getUserContext(fromPhone, "cpf");
            if (ctxCpf != null && !ctxCpf.isBlank() && !"null".equalsIgnoreCase(ctxCpf.trim())) {
                return ctxCpf.trim();
            }
        }
        return "";
    }

    public WebhookResult handleIntegrarGerAcesso(BlipWebhookPayload payload, String fromPhone) {
        log.info("[WEBHOOK] Recebida ação Integrar_GerAcesso. De: {} | ID: {}", fromPhone, payload.messageId());
        String catracaAppId = null;
        String catracaCpf = null;
        if (payload.content() instanceof Map<?, ?> contentMap) {
            Object appVal = contentMap.get("idAgendamentoFeegow");
            if (appVal == null) {
                appVal = contentMap.get("appointmentId");
            }
            if (appVal != null) {
                catracaAppId = appVal.toString().trim();
            }
            Object cpfVal = contentMap.get("cpf");
            if (cpfVal != null) {
                catracaCpf = cpfVal.toString().trim();
            }
        }

        catracaAppId = resolveAppointmentIdFallback(fromPhone, catracaAppId);
        catracaCpf = resolvePatientCpfFallback(fromPhone, catracaAppId, catracaCpf);

        if (catracaAppId != null && !catracaAppId.isBlank()) {
            try {
                String token = accessService.generateAccessToken(catracaAppId, fromPhone);
                String accessUrl = "https://itsm.ctrls.dev.br/" + catracaAppId + "?t=" + token;
                blipContextService.setUserContextForUser(fromPhone, "idAgendamentoFeegow", catracaAppId);
                blipContextService.setUserContextForUser(fromPhone, "tokenAcesso", token);
                blipContextService.setUserContextForUser(fromPhone, "urlAcesso", accessUrl);
                blipContextService.setContactExtra(fromPhone, "idAgendamentoFeegow", catracaAppId);
                blipContextService.setContactExtra(fromPhone, "tokenAcesso", token);
                blipContextService.setContactExtra(fromPhone, "urlAcesso", accessUrl);
            } catch (Exception ex) {
                log.warn("[BlipPhysicalAccessHandler] Falha ao injetar token de acesso no contexto: {}", ex.getMessage());
            }

            AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentId(catracaAppId).orElse(null);
            if (session == null) {
                log.warn("[CATRACA-ALERTA] Sessão não encontrada para o agendamento ID: {}", catracaAppId);
                return new WebhookResult("", "", catracaAppId, "", "Integrar_GerAcesso", "");
            }

            if (!isDoctorAllowed(session.getDoctorProfissionalId())) {
                log.warn("[WEBHOOK-BYPASS] Agendamento ID {} pertence ao médico ID {} (não listado na ENV de automação). Ignorando confirmação automática e liberando fluxo para atendimento manual/Blip.",
                        catracaAppId, session.getDoctorProfissionalId());
                return new WebhookResult("", "", catracaAppId, "", "Integrar_GerAcesso", "");
            }

            Long feegowProfissionalId = null;
            try {
                feegowProfissionalId = Long.parseLong(session.getDoctorProfissionalId());
            } catch (Exception ex) {
                log.warn("[CATRACA-ALERTA] Falha ao converter profissionalId {} para Long", session.getDoctorProfissionalId());
            }

            if (feegowProfissionalId == null) {
                log.warn("[CATRACA-ALERTA] Profissional ID é nulo ou inválido para o agendamento ID: {}", catracaAppId);
                return new WebhookResult("", "", catracaAppId, "", "Integrar_GerAcesso", "");
            }

            DoctorConfiguration doctorConfig = (feegowProfissionalId != null)
                    ? doctorConfigurationRepository.findById(feegowProfissionalId).orElse(null)
                    : null;
            String matriculaVisitado = (doctorConfig != null && doctorConfig.getGerAcessoMatricula() != null)
                    ? doctorConfig.getGerAcessoMatricula().trim()
                    : "";
            String cpfVisitado = (doctorConfig != null && doctorConfig.getGerAcessoCpf() != null)
                    ? doctorConfig.getGerAcessoCpf().trim()
                    : "";

            try {
                log.info("[WEBHOOK] Invocando GerAcesso para agendamento Feegow ID: {}, CPF Paciente: {}, Médico Feegow ID: {}, Matrícula Visitado: {}, CPF Visitado: {}",
                        catracaAppId, catracaCpf, feegowProfissionalId, matriculaVisitado, cpfVisitado);
                accessService.processAccessRequest(catracaAppId, catracaCpf, List.of());
            } catch (Exception ex) {
                log.error("[WEBHOOK] Falha ao processar integração GerAcesso para o agendamento ID: {}", catracaAppId, ex);
            }
        } else {
            log.warn("[CATRACA-ALERTA] Nenhum appointmentId pôde ser extraído para a ação Integrar_GerAcesso.");
        }

        return new WebhookResult("", "", catracaAppId != null ? catracaAppId : "", "", "Integrar_GerAcesso", "");
    }

    public WebhookResult handleNaoAction(BlipWebhookPayload payload, String fromPhone) {
        log.info("[WEBHOOK] Recebida ação Não (Sem acompanhantes). De: {} | ID: {}", fromPhone, payload.messageId());
        String appId = null;
        String cpf = null;
        if (payload.content() instanceof Map<?, ?> contentMap) {
            Object appVal = contentMap.get("idAgendamentoFeegow");
            if (appVal == null) {
                appVal = contentMap.get("appointmentId");
            }
            if (appVal != null) {
                appId = appVal.toString().trim();
            }
            Object cpfVal = contentMap.get("cpf");
            if (cpfVal != null) {
                cpf = cpfVal.toString().trim();
            }
        }

        appId = resolveAppointmentIdFallback(fromPhone, appId);
        cpf = resolvePatientCpfFallback(fromPhone, appId, cpf);

        if (appId != null && !appId.isBlank()) {
            try {
                log.info("[WEBHOOK] Persistindo credenciais finais GerAcesso para agendamento ID: {} (sem acompanhantes). CPF: {}", appId, cpf);
                accessService.processAccessRequest(appId, cpf, List.of());
            } catch (Exception ex) {
                log.error("[WEBHOOK] Erro ao processar integração física GerAcesso no caso 'Não' para agendamento ID: {}", appId, ex);
            }
        }
        return new WebhookResult("", "", appId != null ? appId : "", "", "Não", "");
    }

    public WebhookResult handleFinalizarAgendamento(BlipWebhookPayload payload, String fromPhone) {
        log.info("[WEBHOOK] Recebida ação Finalizar_Agendamento. De: {} | ID: {}", fromPhone, payload.messageId());
        String targetAppId = null;
        String patientCpf = null;
        List<CompanionAccessInfo> companionsList = new ArrayList<>();

        if (payload.content() instanceof Map<?, ?> contentMap) {
            Object appVal = contentMap.get("idAgendamentoFeegow");
            if (appVal == null) {
                appVal = contentMap.get("appointmentId");
            }
            if (appVal != null) {
                targetAppId = appVal.toString().trim();
            }

            Object cpfVal = contentMap.get("cpf");
            if (cpfVal != null) {
                patientCpf = cpfVal.toString().trim();
            }

            Object companionsVal = contentMap.get("listaAcompanhantes");
            if (companionsVal instanceof List<?> rawList) {
                for (Object item : rawList) {
                    if (item instanceof Map<?, ?> companionMap) {
                        String name = companionMap.get("nome") != null ? companionMap.get("nome").toString() : "";
                        if (name.isEmpty()) {
                            name = companionMap.get("name") != null ? companionMap.get("name").toString() : "";
                        }
                        String cCpf = companionMap.get("cpf") != null ? companionMap.get("cpf").toString() : "";
                        String phone = companionMap.get("telefone") != null ? companionMap.get("telefone").toString() : "";
                        if (phone.isEmpty()) {
                            phone = companionMap.get("phone") != null ? companionMap.get("phone").toString() : "";
                        }
                        String email = companionMap.get("email") != null ? companionMap.get("email").toString() : "";
                        String birthDate = companionMap.get("birthDate") != null ? companionMap.get("birthDate").toString() : "";
                        if (birthDate.isEmpty()) {
                            birthDate = companionMap.get("data_nascimento") != null ? companionMap.get("data_nascimento").toString() : "";
                        }

                        companionsList.add(new CompanionAccessInfo(name, cCpf, phone, email, birthDate));
                    }
                }
            }
        }

        targetAppId = resolveAppointmentIdFallback(fromPhone, targetAppId);
        patientCpf = resolvePatientCpfFallback(fromPhone, targetAppId, patientCpf);

        if (targetAppId != null && !targetAppId.isBlank()) {
            try {
                String token = accessService.generateAccessToken(targetAppId, fromPhone);
                String accessUrl = "https://itsm.ctrls.dev.br/" + targetAppId + "?t=" + token;
                blipContextService.setUserContextForUser(fromPhone, "idAgendamentoFeegow", targetAppId);
                blipContextService.setUserContextForUser(fromPhone, "tokenAcesso", token);
                blipContextService.setUserContextForUser(fromPhone, "urlAcesso", accessUrl);
                blipContextService.setContactExtra(fromPhone, "idAgendamentoFeegow", targetAppId);
                blipContextService.setContactExtra(fromPhone, "tokenAcesso", token);
                blipContextService.setContactExtra(fromPhone, "urlAcesso", accessUrl);
            } catch (Exception ex) {
                log.warn("[BlipPhysicalAccessHandler] Falha ao injetar token de acesso na finalização: {}", ex.getMessage());
            }

            try {
                log.info("[WEBHOOK] Persistindo credenciais finais GerAcesso para agendamento ID: {} com {} acompanhante(s). CPF: {}", targetAppId, companionsList.size(), patientCpf);
                accessService.processAccessRequest(targetAppId, patientCpf, companionsList);
            } catch (Exception ex) {
                log.error("[WEBHOOK] Erro ao processar integração física GerAcesso na finalização para agendamento ID: {}", targetAppId, ex);
            }
        } else {
            log.warn("[WEBHOOK] Ação Finalizar_Agendamento recebida, mas nenhum appointmentId pôde ser extraído do payload de {}", fromPhone);
        }

        return new WebhookResult("", "", targetAppId != null ? targetAppId : "", "", "Finalizar_Agendamento", "");
    }

    private boolean isDoctorAllowed(String doctorId) {
        if (doctorEligibilityService != null) {
            return doctorEligibilityService.isDoctorAllowed(doctorId);
        }
        return false;
    }
}
