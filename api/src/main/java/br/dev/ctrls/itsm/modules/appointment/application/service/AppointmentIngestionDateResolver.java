package br.dev.ctrls.itsm.modules.appointment.application.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import br.dev.ctrls.itsm.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.FeegowAppointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável pelo cálculo das datas-alvo da ingestão diária:
 * - Regra de datas globais (D+0, D+1; Sexta inclui Segunda D+3).
 * - Regra de antecedência personalizada de médicos (> 1 dia).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentIngestionDateResolver {

    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final FeegowAppointmentSearcher feegowAppointmentSearcher;

    public record ResolvedDatesAndAppointments(
        List<LocalDate> targetDates,
        List<FeegowAppointment> appointments
    ) {}

    public ResolvedDatesAndAppointments resolveDatesAndFetchAppointments(LocalDate today, DayOfWeek dayOfWeek, List<String> doctorIds) {
        List<LocalDate> targetDates = new ArrayList<>();

        switch (dayOfWeek) {
            case FRIDAY -> {
                // Sexta (D): Sexta (D), Sábado (D+1) e Segunda-feira (D+3)
                targetDates.addAll(List.of(today, today.plusDays(1), today.plusDays(3)));
            }
            default -> {
                // Segunda–Quinta: Hoje (D+0) e Amanhã (D+1). D+2 é exclusivo para médicos com configuração de antecedência
                targetDates.addAll(List.of(today, today.plusDays(1)));
            }
        }

        log.info("[INGESTÃO-DATAS-ALVO] Ingestão matinal iniciada. Dia da semana: {}. Datas-alvo globais (D+0/D+1): {}. Médicos com antecedência personalizada (>1 dia) serão consultados separadamente.",
                dayOfWeek, targetDates);

        List<FeegowAppointment> appointments = new ArrayList<>();
        for (LocalDate targetDate : targetDates) {
            long offsetDays = java.time.temporal.ChronoUnit.DAYS.between(today, targetDate);
            log.info("[INGESTÃO-GERAL] Buscando consultas no Feegow para a data-alvo: {} (D+{} - Dia da semana: {})...",
                    targetDate, offsetDays, targetDate.getDayOfWeek());
            List<FeegowAppointment> dailyAppointments = feegowAppointmentSearcher.searchAppointments(targetDate, doctorIds);
            appointments.addAll(dailyAppointments);
        }

        // Verifica configurações específicas de antecedência de médicos
        try {
            List<DoctorConfiguration> customAdvanceDoctors = doctorConfigurationRepository.findByIsActiveTrue().stream()
                    .filter(c -> c.getResolvedAdvanceNoticeDays() > 1)
                    .toList();

            for (var docConfig : customAdvanceDoctors) {
                int advanceDays = docConfig.getResolvedAdvanceNoticeDays();

                // Regra 2A: Trava D+2 - Se advanceDays == 2, executa na QUARTA-FEIRA (para Sexta D+2) e na QUINTA-FEIRA (para Sábado D+2)
                boolean isAllowedD2Day = today.getDayOfWeek() == DayOfWeek.WEDNESDAY || today.getDayOfWeek() == DayOfWeek.THURSDAY;
                if (advanceDays == 2 && !isAllowedD2Day) {
                    log.info("[INGESTÃO-ANTECEDÊNCIA] Ignorando busca D+2 para o médico {} (ID {}) pois hoje é {} (D+2 é executado exclusivamente nas quartas-feiras para sexta e nas quintas-feiras para sábado).",
                            docConfig.getDoctorName(), docConfig.getFeegowProfissionalId(), today.getDayOfWeek());
                    continue;
                }

                LocalDate advanceDate = today.plusDays(advanceDays);
                String docIdStr = String.valueOf(docConfig.getFeegowProfissionalId());

                if (doctorIds != null && !doctorIds.isEmpty() && !doctorIds.contains(docIdStr)) {
                    continue;
                }

                if (!targetDates.contains(advanceDate)) {
                    log.info("[INGESTÃO-ANTECEDÊNCIA] Médico {} (ID {}) possui antecedência configurada de {} dias. Buscando agendamentos especificamente para a data-alvo D+{}: {}",
                            docConfig.getDoctorName(), docConfig.getFeegowProfissionalId(), advanceDays, advanceDays, advanceDate);
                    List<FeegowAppointment> advanceAppointments = feegowAppointmentSearcher.searchAppointments(advanceDate, List.of(docIdStr));
                    appointments.addAll(advanceAppointments);
                }
            }
        } catch (Exception ex) {
            log.warn("[MOTOR-INGESTÃO] Falha ao verificar antecedência personalizada de médicos: {}", ex.getMessage());
        }

        return new ResolvedDatesAndAppointments(targetDates, appointments);
    }

    /**
     * Busca agendamentos no Feegow especificamente para datas-alvo customizadas fornecidas sob demanda (ex: feriados).
     */
    public ResolvedDatesAndAppointments resolveCustomDatesAndFetchAppointments(List<LocalDate> customDates, List<String> doctorIds) {
        if (customDates == null || customDates.isEmpty()) {
            return new ResolvedDatesAndAppointments(List.of(), List.of());
        }

        log.info("[INGESTÃO-DATAS-CUSTOMIZADAS] Executando busca manual para as datas específicas: {} (médicos: {})",
                customDates, doctorIds != null ? doctorIds : "todos ativos");

        List<FeegowAppointment> appointments = new ArrayList<>();
        for (LocalDate targetDate : customDates) {
            log.info("[INGESTÃO-CUSTOMIZADA] Buscando consultas no Feegow para a data-alvo personalizada: {} (Dia da semana: {})...",
                    targetDate, targetDate.getDayOfWeek());
            List<FeegowAppointment> dailyAppointments = feegowAppointmentSearcher.searchAppointments(targetDate, doctorIds);
            appointments.addAll(dailyAppointments);
        }

        return new ResolvedDatesAndAppointments(customDates, appointments);
    }
}
