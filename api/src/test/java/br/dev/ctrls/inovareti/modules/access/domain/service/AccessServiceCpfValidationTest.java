package br.dev.ctrls.inovareti.modules.access.domain.service;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessServiceCpfValidationTest {

    @Mock
    private FeegowClientPort feegowClientPort;

    @Mock
    private AppointmentExternalPort appointmentExternalPort;

    @Mock
    private PatientExternalPort patientExternalPort;

    @Mock
    private AccessCredentialRepositoryPort accessCredentialRepositoryPort;

    @Mock
    private GerAcessoClientPort gerAcessoClientPort;

    @Mock
    private DoctorConfigurationRepository doctorConfigurationRepository;

    @InjectMocks
    private AccessService accessService;

    @Test
    @DisplayName("Deveria retornar requiresCpfFallback=true quando o CPF do Feegow for matematicamente inválido (ex: Richard)")
    void shouldReturnCpfFallbackWhenFeegowCpfIsInvalid() {
        String appointmentId = "3470777";
        String invalidCpf = "01562089339"; // Invalido no Modulo 11

        FeegowPatientAccessInfo accessInfo = new FeegowPatientAccessInfo(
                appointmentId,
                "100",
                "RICHARD JOSEPH HOULE",
                invalidCpf,
                LocalDate.now(),
                LocalTime.of(14, 0),
                "8",
                "Dr. Carlos Koga",
                "42999999999"
        );

        when(accessCredentialRepositoryPort.findByAppointmentId(appointmentId)).thenReturn(List.of());
        when(feegowClientPort.fetchPatientAccessInfo(appointmentId)).thenReturn(Optional.of(accessInfo));
        when(patientExternalPort.patientInfo("100")).thenReturn(new FeegowPatient("100", "RICHARD JOSEPH HOULE", invalidCpf, "1980-01-01", "42999999999"));

        AccessService.AccessValidationResult result = accessService.processAccessRequest(appointmentId, null, null);

        assertThat(result.authorized()).isFalse();
        assertThat(result.requiresCpfFallback()).isTrue();
        assertThat(result.message()).contains("confirme seu CPF");
        verify(gerAcessoClientPort, never()).registerAccess(any());
    }

    @Test
    @DisplayName("Deveria registrar com sucesso no GerAcesso e sincronizar com Feegow quando o paciente envia CPF corrigido válido")
    void shouldProcessAndSyncWhenValidCpfIsProvided() {
        String appointmentId = "3470777";
        String invalidCpf = "01562089339";
        String validCpf = "13821025930"; // CPF valido

        FeegowPatientAccessInfo accessInfo = new FeegowPatientAccessInfo(
                appointmentId,
                "100",
                "RICHARD JOSEPH HOULE",
                invalidCpf,
                LocalDate.now(),
                LocalTime.of(14, 0),
                "8",
                "Dr. Carlos Koga",
                "42999999999"
        );

        when(accessCredentialRepositoryPort.findByAppointmentId(appointmentId)).thenReturn(List.of());
        when(feegowClientPort.fetchPatientAccessInfo(appointmentId)).thenReturn(Optional.of(accessInfo));
        when(patientExternalPort.patientInfo("100")).thenReturn(new FeegowPatient("100", "RICHARD JOSEPH HOULE", invalidCpf, "1980-01-01", "42999999999"));
        when(appointmentExternalPort.searchAppointments(any(LocalDate.class), anyInt())).thenReturn(List.of());
        when(gerAcessoClientPort.registerAccess(any(GerAcessoRequest.class)))
                .thenReturn(Optional.of(new GerAcessoResponse("1", "OK", 3470777L, "1", 100L, "PNWGN4", "000099894597")));

        AccessService.AccessValidationResult result = accessService.processAccessRequest(appointmentId, validCpf, null);

        assertThat(result.authorized()).isTrue();
        assertThat(result.requiresCpfFallback()).isFalse();
        assertThat(result.accessCredential()).isEqualTo("000099894597");

        // Verifica que sincronizou o CPF correto de volta no Feegow
        verify(patientExternalPort).updatePatientCpf(eq("100"), eq(validCpf), any(), any());
        // Verifica que chamou a catraca fisica
        verify(gerAcessoClientPort).registerAccess(any(GerAcessoRequest.class));
        // Verifica que persistiu a credencial real
        verify(accessCredentialRepositoryPort).save(any(AccessCredential.class));
    }
}
