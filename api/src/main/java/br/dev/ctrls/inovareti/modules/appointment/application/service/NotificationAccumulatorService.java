package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.SendAppointmentTemplateUseCase;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Observed
public class NotificationAccumulatorService {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final PatientExternalPort patientExternalPort;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final BlipNotificationService blipNotificationService;
    private final BlipContextService blipContextService;
    private final AppointmentMotorProperties motorProperties;
    private final TransactionTemplate transactionTemplate;
    private final DoctorEligibilityService doctorEligibilityService;

    public NotificationAccumulatorService(
            AppointmentSessionRepositoryPort appointmentSessionRepository,
            NotificationGroupRepositoryPort notificationGroupRepository,
            PatientExternalPort patientExternalPort,
            SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase,
            BlipNotificationService blipNotificationService,
            BlipContextService blipContextService,
            AppointmentMotorProperties motorProperties,
            TransactionTemplate transactionTemplate) {
        this(appointmentSessionRepository, notificationGroupRepository, patientExternalPort,
             sendAppointmentTemplateUseCase, blipNotificationService, blipContextService,
             motorProperties, transactionTemplate, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationAccumulatorService(
            AppointmentSessionRepositoryPort appointmentSessionRepository,
            NotificationGroupRepositoryPort notificationGroupRepository,
            PatientExternalPort patientExternalPort,
            SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase,
            BlipNotificationService blipNotificationService,
            BlipContextService blipContextService,
            AppointmentMotorProperties motorProperties,
            TransactionTemplate transactionTemplate,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            DoctorEligibilityService doctorEligibilityService) {
        this.appointmentSessionRepository = appointmentSessionRepository;
        this.notificationGroupRepository = notificationGroupRepository;
        this.patientExternalPort = patientExternalPort;
        this.sendAppointmentTemplateUseCase = sendAppointmentTemplateUseCase;
        this.blipNotificationService = blipNotificationService;
        this.blipContextService = blipContextService;
        this.motorProperties = motorProperties;
        this.transactionTemplate = transactionTemplate;
        this.doctorEligibilityService = doctorEligibilityService != null
                ? doctorEligibilityService
                : new DoctorEligibilityService(motorProperties, null, null);
    }

    @Scheduled(cron = "${app.appointment.motor.accumulator-cron:0 0/15 * * * ?}")
    public void accumulateAndSendNotifications() {
        if (!motorProperties.isEnabled()) {
            return;
        }

        log.info("[ACÚMULO] Iniciando rotina de acúmulo de notificações");

        // 1. Buscar agendamentos pendentes de notificação no banco de dados
        List<AppointmentSession> pendingSessions = appointmentSessionRepository.findPendingNotifications();
        if (pendingSessions.isEmpty()) {
            log.info("[ACÚMULO] Nenhum agendamento pendente de notificação encontrado");
            return;
        }

        log.info("[ACÚMULO] Encontrados {} agendamentos pendentes", pendingSessions.size());

        // 2. Agrupar essas entidades pelo telefone (contact_identity / patient_phone), filtrando médicos autorizados
        Map<String, List<AppointmentSession>> groupedByPhone = pendingSessions.stream()
                .filter(s -> s.getPhoneNumber() != null && !s.getPhoneNumber().isBlank())
                .filter(s -> {
                    boolean allowed = doctorEligibilityService.isDoctorAllowed(s.getDoctorProfissionalId());
                    if (!allowed) {
                        log.warn("[ACÚMULO-FILTRO] Sessão Feegow ID {} descartada: médico ID '{}' não autorizado.",
                                s.getFeegowAppointmentId(), s.getDoctorProfissionalId());
                    }
                    return allowed;
                })
                .collect(Collectors.groupingBy(s -> s.getPhoneNumber()));

        for (Map.Entry<String, List<AppointmentSession>> entry : groupedByPhone.entrySet()) {
            String phoneNumber = entry.getKey();
            List<AppointmentSession> sessions = entry.getValue();

            // 3. Verificar se a lista tem tamanho > 1 (é um grupo) ou == 1 (individual)
            if (sessions.size() > 1) {
                processGroupNotification(phoneNumber, sessions);
            } else {
                processIndividualNotification(sessions.getFirst());
            }

            // Cadenciamento (staggered delay de 50ms) entre envios do lote para não sobrecarregar a API da Blip
            applyStaggeredBatchDelay();
        }
    }

    private void applyStaggeredBatchDelay() {
        try {
            java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(50));
        } catch (Exception ignored) {}
    }

    private void processIndividualNotification(AppointmentSession session) {
        if (!doctorEligibilityService.isDoctorAllowed(session.getDoctorProfissionalId())) {
            log.warn("[ACÚMULO] Disparo individual cancelado. Médico ID '{}' não está autorizado.", session.getDoctorProfissionalId());
            return;
        }

        log.info("[ACÚMULO] Processando notificação individual para o agendamento Feegow ID: {}", session.getFeegowAppointmentId());

        // Blindagem contra disparo solo de consultas que pertencem a um grupo de agendamentos
        if (session.getCurrentGroupId() != null) {
            List<AppointmentSession> groupSessions = appointmentSessionRepository.findByCurrentGroupId(session.getCurrentGroupId());
            if (groupSessions != null && groupSessions.size() > 1) {
                log.warn("[ACÚMULO-GUARD] Sessão {} (Feegow ID {}) pertence ao grupo {} que possui {} consultas. Ignorando disparo solo indevido e sincronizando timestamp.",
                        session.getId(), session.getFeegowAppointmentId(), session.getCurrentGroupId(), groupSessions.size());
                transactionTemplate.executeWithoutResult(status -> {
                    LocalDateTime now = LocalDateTime.now();
                    for (AppointmentSession s : groupSessions) {
                        AppointmentSession locked = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(null);
                        if (locked != null && locked.getLastNotificationSentAt() == null) {
                            locked.setLastNotificationSentAt(now);
                            locked.setLastInteractionAt(now);
                            appointmentSessionRepository.save(locked);
                        }
                    }
                });
                return;
            }
        }
        
        if (session.getPhoneNumber() != null && !session.getPhoneNumber().isBlank()) {
            if (blipContextService.hasActiveTicket(session.getPhoneNumber(), session.getLastNotificationSentAt())) {
                log.info("[ATTENDANCE-GUARD] Contato {} possui ticket aberto no Desk. Ignorando disparo de notificação individual acumulada.", session.getPhoneNumber());
                return;
            }
        }

        try {
            boolean sent = sendAppointmentTemplateUseCase.execute(session, AppointmentCategory.CONFIRMATION);
            if (sent) {
                transactionTemplate.executeWithoutResult(status -> {
                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                    if (lockedSession != null) {
                        lockedSession.setLastNotificationSentAt(LocalDateTime.now());
                        appointmentSessionRepository.save(lockedSession);
                    }
                });
                log.info("[ACÚMULO] Notificação individual enviada com sucesso para: {}", session.getPhoneNumber());
            } else {
                log.warn("[ACÚMULO] Falha ao enviar notificação individual para: {}", session.getPhoneNumber());
            }
        } catch (RuntimeException e) {
            log.error("[ACÚMULO] Erro ao processar notificação individual para sessionId: {}", session.getId(), e);
        }
    }

    private void processGroupNotification(String phoneNumber, List<AppointmentSession> sessions) {
        List<AppointmentSession> allowedSessions = sessions.stream()
                .filter(s -> doctorEligibilityService.isDoctorAllowed(s.getDoctorProfissionalId()))
                .toList();
        if (allowedSessions.isEmpty()) {
            log.warn("[ACÚMULO-GRUPO] Todas as sessões do grupo pertencem a médicos não autorizados para o telefone {}. Disparo cancelado.", phoneNumber);
            return;
        }

        UUID groupId = UUID.randomUUID();
        log.info("[ACÚMULO] Processando notificação agrupada. group_id={}, telefone={}, total_consultas={}", 
            groupId, phoneNumber, allowedSessions.size());

        if (phoneNumber != null && !phoneNumber.isBlank()) {
            LocalDateTime lastSent = allowedSessions.isEmpty() ? null : allowedSessions.getFirst().getLastNotificationSentAt();
            if (blipContextService.hasActiveTicket(phoneNumber, lastSent)) {
                log.info("[ATTENDANCE-GUARD] Contato {} possui ticket aberto no Desk. Ignorando disparo de notificação agrupada acumulada.", phoneNumber);
                return;
            }
        }

        // 4. Salvar na tabela 'notification_groups'
        List<NotificationGroup> groupEntities = new ArrayList<>();
        for (AppointmentSession session : allowedSessions) {
            NotificationGroup group = NotificationGroup.builder()
                    .groupId(groupId)
                    .sessionId(session.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            groupEntities.add(group);
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                notificationGroupRepository.saveAll(groupEntities);
                
                // Atualizar o currentGroupId, lastNotificationSentAt e lastInteractionAt para todas as sessões do grupo
                LocalDateTime now = LocalDateTime.now();
                for (AppointmentSession session : allowedSessions) {
                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                    if (lockedSession != null) {
                        lockedSession.setCurrentGroupId(groupId);
                        lockedSession.setLastNotificationSentAt(now);
                        lockedSession.setLastInteractionAt(now);
                        appointmentSessionRepository.save(lockedSession);
                    }
                }
            });
        } catch (RuntimeException e) {
            log.error("[ACÚMULO] Erro ao persistir grupo de notificação ou atualizar sessões no banco de dados", e);
            return;
        }

        // Buscar nome do paciente para personalizar o template (usando o primeiro da lista)
        String patientName = "Paciente";
        try {
            FeegowPatient patientInfo = patientExternalPort.patientInfo(allowedSessions.getFirst().getPatientId());
            if (patientInfo != null && patientInfo.name() != null && !patientInfo.name().isBlank()) {
                patientName = patientInfo.name().trim();
            }
        } catch (Exception e) {
            log.warn("[ACÚMULO] Não foi possível recuperar o nome do paciente via API externa. Usando fallback.", e);
        }

        // 5. Disparar o template de aviso passando o group_id no payload
        try {
            String templateName = motorProperties.getBlipTemplateGroup();
            blipNotificationService.sendGroupTemplateMessage(phoneNumber, templateName, groupId, patientName);
            log.info("[ACÚMULO] Notificação agrupada enviada com sucesso para: {}", phoneNumber);
        } catch (Exception e) {
            log.error("[ACÚMULO] Erro ao disparar template de notificação agrupada para: {}", phoneNumber, e);
        }
    }

    @Scheduled(cron = "${app.appointment.motor.cleanup-cron:0 0 3 * * ?}")
    public void cleanupOldNotificationGroups() {
        log.info("[CLEANUP] Iniciando rotina de limpeza diária de grupos de notificação e sessões antigas");
        LocalDateTime threshold = LocalDateTime.now().minusDays(45);
        try {
            long count = transactionTemplate.execute(status -> 
                notificationGroupRepository.deleteByCreatedAtBefore(threshold)
            );
            log.info("[CLEANUP] Removidos grupos de notificação com mais de 45 dias. Total: {}", count);
        } catch (RuntimeException e) {
            log.error("[CLEANUP] Erro ao executar limpeza de grupos de notificação antigos", e);
        }

        cleanupOldClosedSessions(threshold);
    }

    private void cleanupOldClosedSessions(LocalDateTime threshold) {
        log.info("[CLEANUP] Iniciando expurgo de sessões de agendamento concluídas/canceladas antigas");
        try {
            List<br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus> finalStatuses = List.of(
                br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CONFIRMED,
                br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CANCELED,
                br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CANCELED_NO_RESPONSE
            );
            long countSessions = transactionTemplate.execute(status ->
                appointmentSessionRepository.deleteByStatusInAndCreatedAtBefore(finalStatuses, threshold)
            );
            log.info("[CLEANUP] Removidas sessões concluídas/canceladas com mais de 45 dias. Total: {}", countSessions);
        } catch (RuntimeException e) {
            log.error("[CLEANUP] Erro ao executar expurgo de sessões de agendamento antigas", e);
        }
    }
}


