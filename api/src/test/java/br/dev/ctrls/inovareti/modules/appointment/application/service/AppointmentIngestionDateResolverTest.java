package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentIngestionDateResolver.ResolvedDatesAndAppointments;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentIngestionDateResolverTest {

    @Mock
    private DoctorConfigurationRepository doctorConfigurationRepository;

    @Mock
    private FeegowAppointmentSearcher feegowAppointmentSearcher;

    @InjectMocks
    private AppointmentIngestionDateResolver dateResolver;

    @Test
    @DisplayName("Segunda-feira: Calcula D+0 e D+1")
    void shouldResolveDatesOnMonday() {
        LocalDate monday = LocalDate.of(2026, 8, 24); // Segunda
        when(doctorConfigurationRepository.findByIsActiveTrue()).thenReturn(List.of());

        FeegowAppointment appt1 = new FeegowAppointment("1", "10", "26", "Dra. Vania", "Matriz", monday.atTime(9, 0), "1", "Consulta", "100", false);
        when(feegowAppointmentSearcher.searchAppointments(monday, List.of("26"))).thenReturn(List.of(appt1));
        when(feegowAppointmentSearcher.searchAppointments(monday.plusDays(1), List.of("26"))).thenReturn(List.of());

        ResolvedDatesAndAppointments result = dateResolver.resolveDatesAndFetchAppointments(monday, DayOfWeek.MONDAY, List.of("26"));

        assertThat(result.targetDates()).containsExactly(monday, monday.plusDays(1));
        assertThat(result.appointments()).hasSize(1);
    }

    @Test
    @DisplayName("Sexta-feira: Inclui D+0 (Sexta), D+1 (Sábado) e D+3 (Segunda)")
    void shouldResolveDatesOnFriday() {
        LocalDate friday = LocalDate.of(2026, 8, 28); // Sexta
        when(doctorConfigurationRepository.findByIsActiveTrue()).thenReturn(List.of());
        when(feegowAppointmentSearcher.searchAppointments(any(), any())).thenReturn(List.of());

        ResolvedDatesAndAppointments result = dateResolver.resolveDatesAndFetchAppointments(friday, DayOfWeek.FRIDAY, List.of("26"));

        assertThat(result.targetDates()).containsExactly(friday, friday.plusDays(1), friday.plusDays(3));
    }

    @Test
    @DisplayName("Antecedência Personalizada D+2 na Quarta-feira: Busca Sexta-feira para o médico configurado")
    void shouldFetchCustomAdvanceOnWednesday() {
        LocalDate wednesday = LocalDate.of(2026, 8, 26); // Quarta
        DoctorConfiguration docConfig = DoctorConfiguration.builder()
                .feegowProfissionalId(12L)
                .doctorName("Dr. Cesar Oda")
                .advanceNoticeDays(2)
                .isActive(true)
                .build();
        when(doctorConfigurationRepository.findByIsActiveTrue()).thenReturn(List.of(docConfig));

        LocalDate fridayD2 = wednesday.plusDays(2);
        FeegowAppointment apptD2 = new FeegowAppointment("99", "20", "12", "Dr. Cesar", "Matriz", fridayD2.atTime(10, 0), "1", "Consulta", "100", false);
        when(feegowAppointmentSearcher.searchAppointments(wednesday, List.of("12"))).thenReturn(List.of());
        when(feegowAppointmentSearcher.searchAppointments(wednesday.plusDays(1), List.of("12"))).thenReturn(List.of());
        when(feegowAppointmentSearcher.searchAppointments(fridayD2, List.of("12"))).thenReturn(List.of(apptD2));

        ResolvedDatesAndAppointments result = dateResolver.resolveDatesAndFetchAppointments(wednesday, DayOfWeek.WEDNESDAY, List.of("12"));

        assertThat(result.appointments()).contains(apptD2);
    }
}
