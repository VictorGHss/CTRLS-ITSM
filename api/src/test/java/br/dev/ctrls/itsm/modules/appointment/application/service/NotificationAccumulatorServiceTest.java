package br.dev.ctrls.itsm.modules.appointment.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.itsm.modules.appointment.application.usecase.SendAppointmentTemplateUseCase;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.config.AppointmentMotorProperties;

class NotificationAccumulatorServiceTest {

    private AppointmentSessionRepositoryPort appointmentSessionRepository;
    private NotificationGroupRepositoryPort notificationGroupRepository;
    private PatientExternalPort patientExternalPort;
    private SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private BlipNotificationService blipNotificationService;
    private BlipContextService blipContextService;
    private AppointmentMotorProperties motorProperties;
    private TransactionTemplate transactionTemplate;

    private NotificationAccumulatorService service;

    @BeforeEach
    void setUp() {
        appointmentSessionRepository = mock(AppointmentSessionRepositoryPort.class);
        notificationGroupRepository = mock(NotificationGroupRepositoryPort.class);
        patientExternalPort = mock(PatientExternalPort.class);
        sendAppointmentTemplateUseCase = mock(SendAppointmentTemplateUseCase.class);
        blipNotificationService = mock(BlipNotificationService.class);
        blipContextService = mock(BlipContextService.class);
        motorProperties = mock(AppointmentMotorProperties.class);
        transactionTemplate = mock(TransactionTemplate.class);

        when(motorProperties.isEnabled()).thenReturn(true);
        when(motorProperties.getBlipTemplateGroup()).thenReturn("aviso_agendamento_grupo");

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        DoctorEligibilityService doctorEligibilityService = mock(DoctorEligibilityService.class);
        when(doctorEligibilityService.isDoctorAllowed(any())).thenReturn(true);

        service = new NotificationAccumulatorService(
                appointmentSessionRepository,
                notificationGroupRepository,
                patientExternalPort,
                sendAppointmentTemplateUseCase,
                blipNotificationService,
                blipContextService,
                motorProperties,
                transactionTemplate,
                doctorEligibilityService
        );
    }

    @Test
    @DisplayName("Deveria abortar envio individual caso a sessão pertença a um grupo com múltiplos agendamentos")
    void shouldAbortIndividualDispatchWhenSessionBelongsToGroup() {
        UUID groupId = UUID.randomUUID();
        UUID session1Id = UUID.randomUUID();
        UUID session2Id = UUID.randomUUID();

        AppointmentSession session1 = AppointmentSession.builder()
                .id(session1Id)
                .feegowAppointmentId("1001")
                .doctorProfissionalId("10")
                .phoneNumber("5542999999999")
                .currentGroupId(groupId)
                .status(AppointmentSessionStatus.PENDING)
                .build();

        AppointmentSession session2 = AppointmentSession.builder()
                .id(session2Id)
                .feegowAppointmentId("1002")
                .doctorProfissionalId("10")
                .phoneNumber("5542999999999")
                .currentGroupId(groupId)
                .status(AppointmentSessionStatus.PENDING)
                .build();

        when(appointmentSessionRepository.findPendingNotifications()).thenReturn(List.of(session1));
        when(appointmentSessionRepository.findByCurrentGroupId(groupId)).thenReturn(List.of(session1, session2));
        when(appointmentSessionRepository.findByIdLocked(session1Id)).thenReturn(Optional.of(session1));
        when(appointmentSessionRepository.findByIdLocked(session2Id)).thenReturn(Optional.of(session2));

        assertDoesNotThrow(() -> service.accumulateAndSendNotifications());

        // Garante que NUNCA enviou template individual de confirmação
        verify(sendAppointmentTemplateUseCase, never()).execute(any(AppointmentSession.class), eq(AppointmentCategory.CONFIRMATION));
    }

    @Test
    @DisplayName("Deveria disparar notificação individual caso seja consulta única sem grupo")
    void shouldDispatchIndividualNotificationForSingleSession() {
        UUID sessionId = UUID.randomUUID();
        AppointmentSession session = AppointmentSession.builder()
                .id(sessionId)
                .feegowAppointmentId("2001")
                .doctorProfissionalId("10")
                .phoneNumber("5542988888888")
                .status(AppointmentSessionStatus.PENDING)
                .build();

        when(appointmentSessionRepository.findPendingNotifications()).thenReturn(List.of(session));
        when(appointmentSessionRepository.findByIdLocked(sessionId)).thenReturn(Optional.of(session));
        when(sendAppointmentTemplateUseCase.execute(session, AppointmentCategory.CONFIRMATION)).thenReturn(true);

        assertDoesNotThrow(() -> service.accumulateAndSendNotifications());

        verify(sendAppointmentTemplateUseCase).execute(session, AppointmentCategory.CONFIRMATION);
    }
}
