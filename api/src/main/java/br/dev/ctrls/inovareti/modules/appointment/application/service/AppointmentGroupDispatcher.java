package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipContactClientPort;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.SendAppointmentTemplateUseCase;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.AppointmentIdNormalizer;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável pelo agrupamento, persistência transacional atômica
 * e despacho de notificações individuais ou em lote (grupo) via Blip:
 * - Execução paralela em Virtual Threads.
 * - Controle de taxa com Semaphore para evitar HTTP 429.
 * - Suporte a idempotência e sobreescrita de telefone de teste.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentGroupDispatcher {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final BlipAppointmentFormatter blipAppointmentFormatter;
    private final BlipContextService blipContextService;
    private final BlipContactClientPort blipContactClientPort;
    private final TransactionTemplate transactionTemplate;

    @Nullable
    private final AppointmentSendIdempotencyService appointmentSendIdempotencyService;
    @Nullable
    private final NoopAppointmentSendIdempotencyService noopAppointmentSendIdempotencyService;

    @Value("${APP_APPOINTMENT_BLIP_INGEST_CONCURRENCY:10}")
    private int blipIngestConcurrency;

    private Semaphore blipSemaphore;

    @PostConstruct
    public void init() {
        this.blipSemaphore = new Semaphore(blipIngestConcurrency, true);
    }

    public record DispatchResult(
        int processedCount,
        int sessionsCreatedCount,
        int templatesSentCount,
        int skippedCount
    ) {}

    private record GroupPersistenceResult(
        List<AppointmentSession> savedSessions,
        String preCompiledText
    ) {}

    public DispatchResult dispatchGroups(
            Map<String, List<FeegowAppointment>> groupedByPhone,
            Map<String, FeegowPatient> patientDetailsMap,
            boolean forceSend,
            String overridePhone
    ) {
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = groupedByPhone.entrySet().stream()
                    .map(entry -> CompletableFuture.runAsync(() -> {
                        String phone = entry.getKey();
                        List<FeegowAppointment> phoneAppointments = entry.getValue();

                        try {
                            blipSemaphore.acquire();
                            processPhoneGroup(
                                    phone,
                                    phoneAppointments,
                                    patientDetailsMap,
                                    forceSend,
                                    overridePhone,
                                    processed,
                                    created,
                                    sent,
                                    skipped
                            );
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("[INGESTÃO-PARALELA] Virtual thread interrompida para telefone: {}", phone);
                        } catch (Exception ex) {
                            log.error("[INGESTÃO-PARALELA] Erro ao processar grupo para o telefone {}: {}", phone, ex.getMessage(), ex);
                        } finally {
                            blipSemaphore.release();
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        return new DispatchResult(processed.get(), created.get(), sent.get(), skipped.get());
    }

    private void processPhoneGroup(
            String phone,
            List<FeegowAppointment> phoneAppointments,
            Map<String, FeegowPatient> patientDetailsMap,
            boolean forceSend,
            String overridePhone,
            AtomicInteger processed,
            AtomicInteger created,
            AtomicInteger sent,
            AtomicInteger skipped
    ) {
        String effectivePhone = (overridePhone != null) ? overridePhone : phone;
        int groupSize = phoneAppointments.size();
        processed.addAndGet(groupSize);

        UUID groupId = (groupSize > 1) ? UUID.randomUUID() : null;

        // Persistência em transação atômica única por grupo
        GroupPersistenceResult persistenceResult = transactionTemplate.execute(status -> {
            List<AppointmentSession> sessionsToPersist = new ArrayList<>();
            for (FeegowAppointment appt : phoneAppointments) {
                String feegowId = AppointmentIdNormalizer.normalize(appt.id());
                Optional<AppointmentSession> existingOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowId);

                AppointmentSession session;
                if (existingOpt.isPresent()) {
                    session = existingOpt.get();
                    session.setPhoneNumber(effectivePhone);
                    if (groupId != null) {
                        session.setCurrentGroupId(groupId);
                    }
                } else {
                    created.incrementAndGet();
                    session = AppointmentSession.builder()
                            .feegowAppointmentId(feegowId)
                            .phoneNumber(effectivePhone)
                            .patientId(appt.patientId())
                            .doctorProfissionalId(appt.doctorId())
                            .appointmentAt(appt.startAt())
                            .status(AppointmentSessionStatus.PENDING)
                            .currentGroupId(groupId)
                            .lastInteractionAt(LocalDateTime.now())
                            .createdAt(LocalDateTime.now())
                            .build();
                }
                sessionsToPersist.add(appointmentSessionRepository.save(session));
            }

            String compiledText = null;
            if (groupId != null) {
                compiledText = blipAppointmentFormatter.buildListaDetalhada(sessionsToPersist);
                for (AppointmentSession s : sessionsToPersist) {
                    NotificationGroup ng = NotificationGroup.builder()
                            .groupId(groupId)
                            .sessionId(s.getId())
                            .phoneNumber(effectivePhone)
                            .preCompiledScheduleText(compiledText)
                            .createdAt(LocalDateTime.now())
                            .build();
                    notificationGroupRepository.save(ng);
                }
            }
            return new GroupPersistenceResult(sessionsToPersist, compiledText);
        });

        if (persistenceResult == null || persistenceResult.savedSessions().isEmpty()) {
            return;
        }

        List<AppointmentSession> savedSessions = persistenceResult.savedSessions();

        // 1 Consulta: Notificação Individual
        if (groupSize == 1) {
            AppointmentSession singleSession = savedSessions.getFirst();
            FeegowAppointment appt = phoneAppointments.getFirst();
            FeegowPatient patient = patientDetailsMap.get(appt.patientId());

            String patientName = (patient != null && patient.name() != null) ? patient.name() : "Paciente";
            String patientCpf = (patient != null && patient.cpf() != null) ? patient.cpf() : "";

            boolean canSend = forceSend || isEligibleForDispatch(singleSession);
            if (canSend) {
                try {
                    boolean success = sendAppointmentTemplateUseCase.execute(singleSession, AppointmentCategory.CONFIRMATION);
                    if (success) {
                        sent.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }

                    // Fire-and-forget sync contact
                    CompletableFuture.runAsync(() -> {
                        try {
                            blipContactClientPort.syncContact(effectivePhone, patientName, patientCpf, null, singleSession.getDoctorProfissionalId());
                        } catch (Exception ex) {
                            log.warn("[INGESTÃO-CONTATO] Falha ao sincronizar contato individual para {}: {}", effectivePhone, ex.getMessage());
                        }
                    });
                } catch (Exception ex) {
                    log.error("[INGESTÃO-DISPARO] Erro ao enviar template individual para {}: {}", effectivePhone, ex.getMessage());
                }
            } else {
                skipped.incrementAndGet();
            }
        }
        // Múltiplas Consultas: Notificação em Lote (Grupo)
        else {
            boolean anyEligible = forceSend || savedSessions.stream().anyMatch(this::isEligibleForDispatch);
            if (anyEligible) {
                try {
                    AppointmentSession firstSession = savedSessions.getFirst();
                    FeegowAppointment firstAppt = phoneAppointments.getFirst();
                    FeegowPatient patient = patientDetailsMap.get(firstAppt.patientId());
                    String patientName = (patient != null && patient.name() != null) ? patient.name() : "Paciente";
                    String patientCpf = (patient != null && patient.cpf() != null) ? patient.cpf() : "";

                    String scheduleText = persistenceResult.preCompiledText();
                    if (scheduleText == null || scheduleText.isBlank()) {
                        scheduleText = blipAppointmentFormatter.buildListaDetalhada(savedSessions);
                    }

                    boolean success = sendAppointmentTemplateUseCase.execute(firstSession, AppointmentCategory.GROUP_NOTIFICATION);
                    if (success) {
                        sent.addAndGet(groupSize);

                        // Sincroniza atomicamente lastNotificationSentAt e currentGroupId em TODAS as consultas do grupo
                        LocalDateTime now = LocalDateTime.now();
                        transactionTemplate.executeWithoutResult(status -> {
                            for (AppointmentSession s : savedSessions) {
                                AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(null);
                                if (lockedSession != null) {
                                    lockedSession.setLastNotificationSentAt(now);
                                    lockedSession.setLastInteractionAt(now);
                                    lockedSession.setStatus(AppointmentSessionStatus.PENDING);
                                    if (groupId != null) {
                                        lockedSession.setCurrentGroupId(groupId);
                                    }
                                    appointmentSessionRepository.save(lockedSession);
                                }
                            }
                        });
                    } else {
                        skipped.addAndGet(groupSize);
                    }

                    final String finalScheduleText = scheduleText;
                    final String groupIdStr = (groupId != null) ? groupId.toString() : "";
                    CompletableFuture.runAsync(() -> {
                        try {
                            blipContextService.setUserContextForUser(effectivePhone, "isGroupFlow", "true");
                            blipContextService.setUserContextForUser(effectivePhone, "groupId", groupIdStr);
                            blipContextService.setUserContextForUser(effectivePhone, "lista_detalhada", finalScheduleText);
                            blipContextService.setUserContextForUser(effectivePhone, "listaDetalhada", finalScheduleText);
                            blipContactClientPort.syncContact(effectivePhone, patientName, patientCpf, null, firstAppt.doctorId());
                        } catch (Exception ex) {
                            log.warn("[INGESTÃO-CONTATO] Falha ao sincronizar contexto de grupo para {}: {}", effectivePhone, ex.getMessage());
                        }
                    });
                } catch (Exception ex) {
                    log.error("[INGESTÃO-DISPARO] Erro ao enviar template de grupo para {}: {}", effectivePhone, ex.getMessage());
                }
            } else {
                skipped.addAndGet(groupSize);
            }
        }
    }

    private boolean isEligibleForDispatch(AppointmentSession session) {
        if (session.getStatus() != AppointmentSessionStatus.PENDING) {
            return false;
        }
        if (appointmentSendIdempotencyService != null) {
            return appointmentSendIdempotencyService.registerIfFirstSend(session.getFeegowAppointmentId());
        }
        if (noopAppointmentSendIdempotencyService != null) {
            return noopAppointmentSendIdempotencyService.registerIfFirstSend(session.getFeegowAppointmentId());
        }
        return true;
    }
}
