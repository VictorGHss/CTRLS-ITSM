package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;

class DoctorEligibilityServiceTest {

    private AppointmentMotorProperties properties;
    private DoctorConfigurationRepository doctorConfigRepo;
    private AppointmentDoctorMappingRepositoryPort mappingRepo;
    private DoctorEligibilityService service;

    @BeforeEach
    void setUp() {
        properties = new AppointmentMotorProperties();
        doctorConfigRepo = mock(DoctorConfigurationRepository.class);
        mappingRepo = mock(AppointmentDoctorMappingRepositoryPort.class);
        service = new DoctorEligibilityService(properties, doctorConfigRepo, mappingRepo);
    }

    @Test
    @DisplayName("Blocklist: Médico 46 é bloqueado sumariamente mesmo se listado em activeDoctorIds ou testDoctorIds")
    void testBlocklistDoctor46IsBlocked() {
        properties.setActiveDoctorIds(List.of("46", "10", "20"));
        properties.setTestDoctorId("46");

        assertThat(service.isDoctorAllowed("46")).isFalse();
        assertThat(service.isDoctorAllowed(" 46 ")).isFalse();
        assertThat(service.isDoctorAllowed("10")).isTrue();
    }

    @Test
    @DisplayName("Custom Blocklist: Bloqueia médicos configurados na blocklist personalizada")
    void testCustomBlocklist() {
        service.setBlockedDoctorIdsRaw("99, 100; 101");
        properties.setActiveDoctorIds(List.of("99", "50"));

        assertThat(service.isDoctorAllowed("99")).isFalse();
        assertThat(service.isDoctorAllowed("100")).isFalse();
        assertThat(service.isDoctorAllowed("101")).isFalse();
        assertThat(service.isDoctorAllowed("50")).isTrue();
    }

    @Test
    @DisplayName("Test Mode: Quando testMode=true, somente médicos de testDoctorIds são permitidos")
    void testTestModeRestrictsStrictlyToTestDoctors() {
        properties.setTestMode(true);
        properties.setTestDoctorId("10, 20");
        properties.setActiveDoctorIds(List.of("30", "40"));

        assertThat(service.isDoctorAllowed("10")).isTrue();
        assertThat(service.isDoctorAllowed("20")).isTrue();
        assertThat(service.isDoctorAllowed("30")).isFalse();
        assertThat(service.isDoctorAllowed("40")).isFalse();
    }

    @Test
    @DisplayName("Mapping com ignoreAutoSchedule=true: Bloqueia médico imediatamente")
    void testMappingWithIgnoreAutoScheduleIsBlocked() {
        properties.setActiveDoctorIds(List.of("15"));

        AppointmentDoctorMapping mapping = AppointmentDoctorMapping.builder()
                .profissionalId("15")
                .blipQueueId("queue-general")
                .ignoreAutoSchedule(true)
                .isActive(true)
                .build();
        when(mappingRepo.findByProfissionalId("15")).thenReturn(Optional.of(mapping));

        assertThat(service.isDoctorAllowed("15")).isFalse();
    }

    @Test
    @DisplayName("Mapping com blipQueueId='inactive': Bloqueia médico imediatamente")
    void testMappingWithInactiveQueueIsBlocked() {
        properties.setActiveDoctorIds(List.of("15"));

        AppointmentDoctorMapping mapping = AppointmentDoctorMapping.builder()
                .profissionalId("15")
                .blipQueueId("inactive")
                .ignoreAutoSchedule(false)
                .isActive(true)
                .build();
        when(mappingRepo.findByProfissionalId("15")).thenReturn(Optional.of(mapping));

        assertThat(service.isDoctorAllowed("15")).isFalse();
    }

    @Test
    @DisplayName("DoctorConfiguration com isActive=false: Bloqueia médico")
    void testDoctorConfigWithIsActiveFalseIsBlocked() {
        properties.setActiveDoctorIds(List.of("25"));

        DoctorConfiguration config = DoctorConfiguration.builder()
                .feegowProfissionalId(25L)
                .isActive(false)
                .blipQueueId("queue-cardio")
                .build();
        when(doctorConfigRepo.findById(25L)).thenReturn(Optional.of(config));

        assertThat(service.isDoctorAllowed("25")).isFalse();
    }

    @Test
    @DisplayName("DoctorConfiguration com blipQueueId='inactive': Bloqueia médico")
    void testDoctorConfigWithInactiveQueueIsBlocked() {
        DoctorConfiguration config = DoctorConfiguration.builder()
                .feegowProfissionalId(25L)
                .isActive(true)
                .blipQueueId("inactive")
                .build();
        when(doctorConfigRepo.findById(25L)).thenReturn(Optional.of(config));

        assertThat(service.isDoctorAllowed("25")).isFalse();
    }

    @Test
    @DisplayName("Strict Whitelist: Quando activeDoctorIds está configurado, médicos fora da lista são bloqueados mesmo se ativos no banco")
    void testDoctorConfigActiveWithValidQueueBlockedWhenNotInActiveDoctorIds() {
        properties.setActiveDoctorIds(List.of("8"));

        DoctorConfiguration config = DoctorConfiguration.builder()
                .feegowProfissionalId(32L)
                .doctorName("Dr. Silva")
                .isActive(true)
                .blipQueueId("queue-pediatria")
                .build();
        when(doctorConfigRepo.findById(32L)).thenReturn(Optional.of(config));

        assertThat(service.isDoctorAllowed("32")).isFalse(); // Fora da whitelist da .env
        assertThat(service.isDoctorAllowed("8")).isTrue();   // Presente na whitelist da .env
        assertThat(service.isDoctorAllowed("999")).isFalse(); // fail-closed
    }

    @Test
    @DisplayName("Fallback DB: Quando activeDoctorIds está vazio na .env, médicos ativos no banco são permitidos")
    void testDoctorConfigActiveWithValidQueueAllowedWhenActiveDoctorIdsIsEmpty() {
        properties.setActiveDoctorIds(List.of());

        DoctorConfiguration config = DoctorConfiguration.builder()
                .feegowProfissionalId(32L)
                .doctorName("Dr. Silva")
                .isActive(true)
                .blipQueueId("queue-pediatria")
                .build();
        when(doctorConfigRepo.findById(32L)).thenReturn(Optional.of(config));

        assertThat(service.isDoctorAllowed("32")).isTrue();
        assertThat(service.isDoctorAllowed("999")).isFalse(); // fail-closed
    }

    @Test
    @DisplayName("Fail-Closed: Médico desconhecido sem configuração nunca é permitido (elimina fail-open)")
    void testFailClosedForUnknownDoctors() {
        // activeDoctorIds vazio
        properties.setActiveDoctorIds(List.of());

        assertThat(service.isDoctorAllowed("99")).isFalse();
        assertThat(service.isDoctorAllowed(null)).isFalse();
        assertThat(service.isDoctorAllowed("")).isFalse();
    }

    @Test
    @DisplayName("isDoctorAllowed com requestedDoctorIds: Respeita filtro sob demanda")
    void testRequestedDoctorIdsFilter() {
        properties.setActiveDoctorIds(List.of("10", "20", "30"));

        List<String> requested = List.of("10", "20");

        assertThat(service.isDoctorAllowed("10", requested)).isTrue();
        assertThat(service.isDoctorAllowed("20", requested)).isTrue();
        assertThat(service.isDoctorAllowed("30", requested)).isFalse();
    }

    @Test
    @DisplayName("getActiveAllowedDoctorIds: Em produção com activeDoctorIds restringe estritamente aos configurados na .env")
    void testGetActiveAllowedDoctorIdsStrictWhitelist() {
        properties.setActiveDoctorIds(List.of("10", "20", "46")); // 46 é bloqueado

        DoctorConfiguration activeConfig = DoctorConfiguration.builder()
                .feegowProfissionalId(30L)
                .isActive(true)
                .blipQueueId("queue-derma")
                .build();
        when(doctorConfigRepo.findByIsActiveTrue()).thenReturn(List.of(activeConfig));
        when(doctorConfigRepo.findById(30L)).thenReturn(Optional.of(activeConfig));

        AppointmentDoctorMapping activeMapping = AppointmentDoctorMapping.builder()
                .profissionalId("40")
                .isActive(true)
                .ignoreAutoSchedule(false)
                .blipQueueId("queue-orto")
                .build();
        when(mappingRepo.findAll()).thenReturn(List.of(activeMapping));
        when(mappingRepo.findByProfissionalId("40")).thenReturn(Optional.of(activeMapping));

        List<String> result = service.getActiveAllowedDoctorIds();

        // 10 e 20 estão na allowlist; 46 é bloqueado; 30 e 40 NÃO entram porque activeDoctorIds é estrito
        assertThat(result).containsExactly("10", "20");
        assertThat(result).doesNotContain("46", "30", "40");
    }

    @Test
    @DisplayName("getActiveAllowedDoctorIds: Fallback para banco quando activeDoctorIds não está configurado")
    void testGetActiveAllowedDoctorIdsFallbackToDb() {
        properties.setActiveDoctorIds(List.of());

        DoctorConfiguration activeConfig = DoctorConfiguration.builder()
                .feegowProfissionalId(30L)
                .isActive(true)
                .blipQueueId("queue-derma")
                .build();
        when(doctorConfigRepo.findByIsActiveTrue()).thenReturn(List.of(activeConfig));
        when(doctorConfigRepo.findById(30L)).thenReturn(Optional.of(activeConfig));

        AppointmentDoctorMapping activeMapping = AppointmentDoctorMapping.builder()
                .profissionalId("40")
                .isActive(true)
                .ignoreAutoSchedule(false)
                .blipQueueId("queue-orto")
                .build();
        when(mappingRepo.findAll()).thenReturn(List.of(activeMapping));
        when(mappingRepo.findByProfissionalId("40")).thenReturn(Optional.of(activeMapping));

        List<String> result = service.getActiveAllowedDoctorIds();

        assertThat(result).contains("30", "40");
    }

    @Test
    @DisplayName("Modo DB: Quando activeDoctorIds='DB', busca médicos ativos dinamicamente no banco e bloqueia inativos")
    void testDbDrivenModeWithExplicitDbSetting() {
        properties.setActiveDoctorIds(List.of("DB"));
        assertThat(service.isDbDrivenMode()).isTrue();

        // Médico 15 ativo no banco
        DoctorConfiguration activeConfig = DoctorConfiguration.builder()
                .feegowProfissionalId(15L)
                .isActive(true)
                .build();
        when(doctorConfigRepo.findById(15L)).thenReturn(Optional.of(activeConfig));
        when(doctorConfigRepo.findByIsActiveTrue()).thenReturn(List.of(activeConfig));

        // Médico 75 inativo no banco
        DoctorConfiguration inactiveConfig = DoctorConfiguration.builder()
                .feegowProfissionalId(75L)
                .isActive(false)
                .build();
        when(doctorConfigRepo.findById(75L)).thenReturn(Optional.of(inactiveConfig));

        assertThat(service.isDoctorAllowed("15")).isTrue();
        assertThat(service.isDoctorAllowed("75")).isFalse();

        List<String> activeIds = service.getActiveAllowedDoctorIds();
        assertThat(activeIds).contains("15");
        assertThat(activeIds).doesNotContain("75");
    }
}
