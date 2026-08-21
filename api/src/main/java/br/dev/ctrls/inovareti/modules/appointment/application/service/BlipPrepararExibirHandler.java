package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.access.domain.port.output.BlipContactClientPort;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output.BlipContactClientAdapter;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.BlipWebhookPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookResult;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler especializado na interceptação e processamento das ações do fluxo Blip:
 * - Preparar_Atendimento: resolve identificação do paciente, médico e fila, sincronizando com o Blip.
 * - Exibir_Agenda: resolve agrupamentos e lista detalhada para exibição ao paciente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlipPrepararExibirHandler {

    private final BlipIdentityReconciler blipIdentityReconciler;
    private final BlipContextService blipContextService;
    private final BlipWebhookPreprocessor preprocessor;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final PatientExternalPort patientExternalPort;
    private final BlipContactClientPort blipContactClientPort;
    private final TransactionTemplate transactionTemplate;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final BlipAppointmentFormatter blipAppointmentFormatter;

    public WebhookResult handlePrepararOuExibir(BlipWebhookPayload payload, boolean isPrepararAtendimento) {
        String from = payload.from();
        if (from == null || from.isBlank()) {
            return new WebhookResult("", "", "", "", "processed", "");
        }

        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(from, payload.bsuid());
        String normalizedPhone = from.trim();

        if (isPrepararAtendimento) {
            return handlePrepararAtendimento(normalizedPhone, dbPhone);
        } else {
            return handleExibirAgenda(payload, normalizedPhone, dbPhone);
        }
    }

    private WebhookResult handlePrepararAtendimento(String normalizedPhone, String dbPhone) {
        log.info("[WEBHOOK-BLOCK] Interceptando Preparar_Atendimento para {} (DB Phone: {})", normalizedPhone, dbPhone);

        String resolvedQueueName = null;
        String resolvedDoctorId = null;
        String resolvedPatientName = null;
        String resolvedCpf = null;

        // PRIORIDADE ABSOLUTA DO CPF: busca primeiro por CPF/feegowId autenticado na sessão ativa
        String flowCpf = blipContextService.getUserContext(normalizedPhone, "cpf");
        if (flowCpf == null || flowCpf.isBlank()) {
            flowCpf = blipContextService.getUserContext(normalizedPhone, "userCpf");
        }
        String flowPatientId = blipContextService.getUserContext(normalizedPhone, "feegowId");

        if ((flowCpf != null && !flowCpf.isBlank()) || (flowPatientId != null && !flowPatientId.isBlank())) {
            String queryParam = (flowPatientId != null && !flowPatientId.isBlank())
                    ? flowPatientId
                    : (flowCpf != null ? flowCpf.replaceAll("\\D", "") : null);
            if (queryParam != null && !queryParam.isBlank()) {
                try {
                    var patient = patientExternalPort.patientInfo(queryParam);
                    if (patient != null) {
                        if (patient.name() != null && !patient.name().isBlank() && !BlipContactClientAdapter.isInvalidName(patient.name()) && !BlipContactClientAdapter.isGenericName(patient.name())) {
                            resolvedPatientName = patient.name().trim();
                        }
                        if (patient.cpf() != null && !patient.cpf().isBlank()) {
                            resolvedCpf = patient.cpf().replaceAll("\\D", "");
                        }
                    }
                } catch (Exception ex) {
                    log.debug("[WEBHOOK-BLOCK] Erro ao buscar paciente por CPF/feegowId da sessão: {}", ex.getMessage());
                }
            }
        }

        try {
            String searchPhone = (dbPhone != null && !dbPhone.isEmpty()) ? dbPhone : normalizedPhone;
            String purifiedPhone = preprocessor.purifyPhoneNumberForSearch(searchPhone);
            if (purifiedPhone.isEmpty()) {
                purifiedPhone = preprocessor.purifyPhoneNumberForSearch(normalizedPhone);
            }

            List<AppointmentSession> activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(purifiedPhone);
            if ((activeSessions == null || activeSessions.isEmpty()) && !purifiedPhone.startsWith("55")) {
                activeSessions = appointmentSessionRepository.findActiveByPhoneNumber("55" + purifiedPhone);
            }

            if (activeSessions != null && !activeSessions.isEmpty()) {
                if (resolvedPatientName == null) {
                    Set<String> patientIds = activeSessions.stream()
                            .filter(Objects::nonNull)
                            .map(AppointmentSession::getPatientId)
                            .filter(Objects::nonNull)
                            .filter(id -> !id.isBlank())
                            .collect(Collectors.toSet());

                    if (patientIds.size() == 1) {
                        String singlePatientId = patientIds.iterator().next();
                        var patient = patientExternalPort.patientInfo(singlePatientId);
                        if (patient != null) {
                            if (patient.name() != null && !patient.name().isBlank() && !BlipContactClientAdapter.isInvalidName(patient.name()) && !BlipContactClientAdapter.isGenericName(patient.name())) {
                                resolvedPatientName = patient.name().trim();
                            }
                            if (patient.cpf() != null && !patient.cpf().isBlank()) {
                                resolvedCpf = patient.cpf().replaceAll("\\D", "");
                            }
                        }
                    } else if (patientIds.size() > 1) {
                        log.info("[WEBHOOK-BLOCK] Múltiplos pacientes ({}) no telefone {}. NÃO sobrescrevendo nome/CPF por telefone sem autenticação por CPF.",
                                patientIds.size(), normalizedPhone);
                    }
                }

                AppointmentSession session = activeSessions.get(0);
                resolvedDoctorId = session.getDoctorProfissionalId();
                if (resolvedDoctorId != null && !resolvedDoctorId.isBlank()) {
                    Optional<AppointmentDoctorMapping> doctorMappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(resolvedDoctorId);
                    if (doctorMappingOpt.isPresent()) {
                        String blipQueueId = doctorMappingOpt.get().getBlipQueueId();
                        if (blipQueueId != null && !blipQueueId.isBlank()) {
                            String resolved = blipContextService.resolveQueueName(blipQueueId);
                            resolvedQueueName = (resolved != null && !resolved.isBlank()) ? resolved : blipQueueId;
                            try {
                                blipContextService.setQueueRedirect(normalizedPhone, blipQueueId.trim());
                            } catch (Exception qEx) {
                                log.warn("[WEBHOOK-BLOCK] Falha ao executar setQueueRedirect em Preparar_Atendimento: {}", qEx.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("[WEBHOOK-BLOCK] Falha defensiva ao resolver agendamento ativo em Preparar_Atendimento: {}", ex.getMessage());
        }

        try {
            blipContactClientPort.syncContact(
                normalizedPhone,
                resolvedPatientName,
                resolvedCpf != null ? resolvedCpf : "",
                resolvedQueueName,
                resolvedDoctorId != null ? resolvedDoctorId : ""
            );
        } catch (Exception ex) {
            log.warn("[WEBHOOK-BLOCK] Falha ao sincronizar contato no Preparar_Atendimento: {}", ex.getMessage());
        }

        record GroupInfo(boolean isGroup, UUID groupId) {}
        GroupInfo groupInfo = transactionTemplate.execute(status -> {
            List<AppointmentSession> activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(dbPhone);
            if (activeSessions != null) {
                for (AppointmentSession activeSession : activeSessions) {
                    List<NotificationGroup> groups = notificationGroupRepository.findBySessionId(activeSession.getId());
                    if (groups != null && !groups.isEmpty()) {
                        return new GroupInfo(true, groups.get(0).getGroupId());
                    }
                }
            }
            return new GroupInfo(false, null);
        });

        boolean isGroup = groupInfo != null && groupInfo.isGroup();
        UUID groupId = groupInfo != null ? groupInfo.groupId() : null;

        blipContextService.setUserContextForUser(normalizedPhone, "isGroupFlow", String.valueOf(isGroup));
        if (isGroup && groupId != null) {
            blipContextService.setUserContextForUser(normalizedPhone, "groupId", groupId.toString());
        } else {
            blipContextService.deleteUserContext(normalizedPhone, "groupId");
        }
        return new WebhookResult("", "", "", "", "processed", "");
    }

    private WebhookResult handleExibirAgenda(BlipWebhookPayload payload, String normalizedPhone, String dbPhone) {
        log.info("[WEBHOOK-BLOCK] Interceptando Exibir_Agenda para {} (DB Phone: {})", normalizedPhone, dbPhone);

        // ─── Estratégia 1: groupId enviado explicitamente no content do payload ──────
        UUID resolvedGroupId = null;
        String rawContent = payload.content() != null ? payload.content().toString().trim() : "";
        Pattern uuidPat = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        Matcher uuidMatcher = uuidPat.matcher(rawContent);
        if (uuidMatcher.find()) {
            try {
                resolvedGroupId = UUID.fromString(uuidMatcher.group());
                log.info("[WEBHOOK-BLOCK] groupId extraído do content do payload: {}", resolvedGroupId);
            } catch (IllegalArgumentException ignored) {}
        }

        final UUID initialResolvedGroupId = resolvedGroupId;
        record ExibirAgendaDbResult(String listaDetalhada, UUID finalGroupId) {}

        ExibirAgendaDbResult dbResult = transactionTemplate.execute(status -> {
            UUID groupToUse = initialResolvedGroupId;
            String textResult = null;
            try {
                List<AppointmentSession> activeSessionsResult = appointmentSessionRepository.findActiveByPhoneNumber(dbPhone);
                if (activeSessionsResult != null && !activeSessionsResult.isEmpty()) {
                    List<AppointmentSession> activeSessions = new ArrayList<>(activeSessionsResult);
                    activeSessions.sort((s1, s2) -> {
                        if (s1.getAppointmentAt() == null && s2.getAppointmentAt() == null) return 0;
                        if (s1.getAppointmentAt() == null) return 1;
                        if (s2.getAppointmentAt() == null) return -1;
                        return s1.getAppointmentAt().compareTo(s2.getAppointmentAt());
                    });

                    // Estratégia 2: currentGroupId persistido no banco pelo clique do botão
                    if (groupToUse == null) {
                        for (AppointmentSession s : activeSessions) {
                            if (s.getCurrentGroupId() != null) {
                                groupToUse = s.getCurrentGroupId();
                                log.info("[WEBHOOK-BLOCK] groupId resolvido via currentGroupId do banco: {}", groupToUse);
                                break;
                            }
                        }
                    }

                    // Busca lista_detalhada pelo groupId resolvido
                    if (groupToUse != null) {
                        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupToUse);
                        if (groups != null && !groups.isEmpty()) {
                            for (NotificationGroup g : groups) {
                                if (g.getPreCompiledScheduleText() != null && !g.getPreCompiledScheduleText().isBlank()) {
                                    textResult = g.getPreCompiledScheduleText();
                                    log.info("[WEBHOOK-BLOCK] Recuperado preCompiledScheduleText do banco para groupId={}", groupToUse);
                                    break;
                                }
                            }
                        }
                    }

                    // Estratégia 3: último grupo do paciente como fallback
                    if (textResult == null) {
                        Optional<NotificationGroup> latestGroupOpt = notificationGroupRepository.findLatestByPhone(dbPhone);
                        if (latestGroupOpt.isPresent()) {
                            NotificationGroup latestGroup = latestGroupOpt.get();
                            if (groupToUse == null) {
                                groupToUse = latestGroup.getGroupId();
                            }
                            List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(latestGroup.getGroupId());
                            if (groups != null && !groups.isEmpty()) {
                                for (NotificationGroup g : groups) {
                                    if (g.getPreCompiledScheduleText() != null && !g.getPreCompiledScheduleText().isBlank()) {
                                        textResult = g.getPreCompiledScheduleText();
                                        log.info("[WEBHOOK-BLOCK] Recuperado preCompiledScheduleText do banco para o último groupId={}", latestGroup.getGroupId());
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // Estratégia 4: compila em tempo de execução
                    if (textResult == null) {
                        log.info("[WEBHOOK-BLOCK] Nenhuma lista pré-compilada encontrada. Gerando lista detalhada via Feegow...");
                        textResult = blipAppointmentFormatter.buildListaDetalhada(activeSessions);
                    }
                }
            } catch (RuntimeException ex) {
                log.error("[WEBHOOK-BLOCK] Erro ao buscar/gerar lista formatada do banco.", ex);
            }
            return new ExibirAgendaDbResult(textResult, groupToUse);
        });

        String listaDetalhada = dbResult != null ? dbResult.listaDetalhada() : null;
        resolvedGroupId = dbResult != null ? dbResult.finalGroupId() : resolvedGroupId;

        if (listaDetalhada != null && !listaDetalhada.isBlank()) {
            blipContextService.setUserContextForUser(normalizedPhone, "lista_detalhada", listaDetalhada);
            blipContextService.setUserContextForUser(normalizedPhone, "listaDetalhada", listaDetalhada);
            log.info("[WEBHOOK-BLOCK] Injetada lista_detalhada e listaDetalhada para {}.", normalizedPhone);
        } else {
            log.warn("[WEBHOOK-BLOCK] lista_detalhada vazia ou nula para {}.", normalizedPhone);
        }

        if (resolvedGroupId != null) {
            blipContextService.setUserContextForUser(normalizedPhone, "groupId", resolvedGroupId.toString());
            blipContextService.setUserContextForUser(normalizedPhone, "isConfirmingAgenda", "true");
            log.info("[WEBHOOK-BLOCK] Injetado groupId={} e isConfirmingAgenda=true para {}.", resolvedGroupId, normalizedPhone);
        }

        log.info("[WEBHOOK-HTTP] Requisição síncrona de Exibir_Agenda respondida com sucesso para o usuário: {}", normalizedPhone);
        return new WebhookResult("", "", "", "", "processed", "");
    }
}
