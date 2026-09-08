package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;

public class BlipNotificationServiceTest {

    private BlipNotificationService service;
    private AppointmentMotorProperties properties;

    @BeforeEach
    public void setUp() {
        BlipLIMEClient limeClient = mock(BlipLIMEClient.class);
        BlipTemplateParameterResolver templateParameterResolver = mock(BlipTemplateParameterResolver.class);
        properties = new AppointmentMotorProperties();
        BlipPayloadBuilder payloadBuilder = mock(BlipPayloadBuilder.class);
        BlipContextService contextService = mock(BlipContextService.class);
        AppointmentSessionRepositoryPort sessionRepository = mock(AppointmentSessionRepositoryPort.class);
        BlipAppointmentFormatter formatter = mock(BlipAppointmentFormatter.class);
        BlipReviewNotificationService reviewNotificationService = mock(BlipReviewNotificationService.class);

        service = new BlipNotificationService(
                limeClient,
                templateParameterResolver,
                properties,
                payloadBuilder,
                contextService,
                sessionRepository,
                formatter,
                reviewNotificationService
        );
    }

    @Test
    public void testDoctorInBlocklistReturnsFalse() {
        service.setRawBlockedDoctorIds("46");

        assertFalse(service.isDoctorAllowed("46"), "Médico 46 presente na lista de bloqueio deve retornar false");
        assertFalse(service.isDoctorAllowed(" 46 "), "Médico 46 com espaços deve retornar false");
    }

    @Test
    public void testDoctorOutsideBlocklistWithAllowlistReturnsTrue() {
        service.setRawBlockedDoctorIds("46");
        properties.setTestDoctorId("1,70");

        assertTrue(service.isDoctorAllowed("1"), "Médico 1 na allowlist de teste deve retornar true");
        assertTrue(service.isDoctorAllowed("70"), "Médico 70 na allowlist de teste deve retornar true");
        assertFalse(service.isDoctorAllowed("46"), "Médico 46 bloqueado deve retornar false mesmo se estivesse em allowlist");
    }

    @Test
    public void testEmptyBlocklistFailOpenDefault() {
        service.setRawBlockedDoctorIds("");

        assertTrue(service.isDoctorAllowed("99"), "Com lista de bloqueio e allowlist vazias, o comportamento padrão deve ser fail-open (true)");
        assertTrue(service.isDoctorAllowed("46"), "Sem bloqueio configurado e sem allowlist, o médico 46 deve ser permitido");
    }

    @Test
    public void testIsStaticZeroParamTemplate() {
        assertTrue(BlipNotificationService.isStaticZeroParamTemplate("aviso_agendamento_grupo"));
        assertTrue(BlipNotificationService.isStaticZeroParamTemplate("aviso agendamento grupo"));
        assertTrue(BlipNotificationService.isStaticZeroParamTemplate(" AVISO_AGENDAMENTO_GRUPO "));
        assertFalse(BlipNotificationService.isStaticZeroParamTemplate("confirmacao_consulta_v6_itsm"));
        assertFalse(BlipNotificationService.isStaticZeroParamTemplate(""));
        assertFalse(BlipNotificationService.isStaticZeroParamTemplate(null));
    }

    @Test
    public void testDoctorConfiguredInDatabaseAllowed() {
        var docRepo = mock(br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository.class);
        var mappingRepo = mock(br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort.class);

        var activeDocConfig = br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration.builder()
                .feegowProfissionalId(32L)
                .isActive(true)
                .build();

        org.mockito.Mockito.when(docRepo.findById(32L)).thenReturn(java.util.Optional.of(activeDocConfig));

        BlipNotificationService customService = new BlipNotificationService(
                mock(BlipLIMEClient.class),
                mock(BlipTemplateParameterResolver.class),
                properties,
                mock(BlipPayloadBuilder.class),
                mock(BlipContextService.class),
                mock(AppointmentSessionRepositoryPort.class),
                mock(BlipAppointmentFormatter.class),
                mock(BlipReviewNotificationService.class),
                docRepo,
                mappingRepo
        );

        // Properties has only active doctor 8, but doctor 32 is in DB
        properties.setActiveDoctorIds(java.util.List.of("8"));

        assertTrue(customService.isDoctorAllowed("32"), "Médico 32 ativo no banco deve ser permitido mesmo se activeDoctorIds contiver apenas outros médicos");
        assertFalse(customService.isDoctorAllowed("999"), "Médico 999 não existente no banco nem no properties deve ser negado quando activeDoctorIds estiver restrito");
    }

    @Test
    public void testDoctorConfiguredInMappingRepoAllowed() {
        var docRepo = mock(br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository.class);
        var mappingRepo = mock(br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort.class);

        var mapping = br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping.builder()
                .profissionalId("15")
                .profissionalNome("Dr. Teste")
                .build();

        org.mockito.Mockito.when(mappingRepo.findByProfissionalId("15")).thenReturn(java.util.Optional.of(mapping));

        BlipNotificationService customService = new BlipNotificationService(
                mock(BlipLIMEClient.class),
                mock(BlipTemplateParameterResolver.class),
                properties,
                mock(BlipPayloadBuilder.class),
                mock(BlipContextService.class),
                mock(AppointmentSessionRepositoryPort.class),
                mock(BlipAppointmentFormatter.class),
                mock(BlipReviewNotificationService.class),
                docRepo,
                mappingRepo
        );

        properties.setActiveDoctorIds(java.util.List.of("8"));

        assertTrue(customService.isDoctorAllowed("15"), "Médico 15 mapeado na tabela deve ser permitido");
    }

    @Test
    public void testSendTemplateMessageThrowsOnResourceError() {
        BlipLIMEClient mockLime = mock(BlipLIMEClient.class);
        BlipTemplateParameterResolver mockResolver = mock(BlipTemplateParameterResolver.class);
        BlipPayloadBuilder mockPayload = mock(BlipPayloadBuilder.class);

        org.mockito.Mockito.when(mockLime.executeCommand(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of("status", "resource-error", "message", "Read timed out"));

        org.mockito.Mockito.when(mockResolver.buildDynamicParameters(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(java.util.Map.of("text", "João")));

        BlipNotificationService serviceWithMock = new BlipNotificationService(
                mockLime,
                mockResolver,
                properties,
                mockPayload,
                mock(BlipContextService.class),
                mock(AppointmentSessionRepositoryPort.class),
                mock(BlipAppointmentFormatter.class),
                mock(BlipReviewNotificationService.class)
        );

        var data = new br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateData(
                "1", "100", "João", "42999999999", "1", "Dr. A", "Geral", "Unidade", "2026-09-08", "08/09", "10:00", "2026-09-08"
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                br.dev.ctrls.inovareti.modules.appointment.domain.exception.BlipNotificationException.class,
                () -> serviceWithMock.sendTemplateMessage("5542999999999", "confirmacao_consulta_v6_itsm", data),
                "Deve lançar BlipNotificationException quando Blip responder resource-error em vez de fingir sucesso"
        );
    }
}
