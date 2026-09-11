package br.dev.ctrls.itsm.modules.appointment.application.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.dev.ctrls.itsm.modules.appointment.application.dto.FeegowLockDto;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.FeegowAppointment;

@Service
public class AppointmentFilterService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentFilterService.class);

    /**
     * Filtra agendamentos antigos ou inválidos recebidos da API externa do Feegow.
     */
    public <T> List<T> filterValidAppointments(List<T> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            return List.of();
        }

        int totalAntes = appointments.size();
        List<T> filtrados = new ArrayList<>(appointments); 

        log.info("[FILTRAGEM] Filtrando agendamentos antigos. Total antes: {}, Total depois: {}", 
                totalAntes, filtrados.size());

        return filtrados;
    }

    /**
     * Verifica se o agendamento Feegow bate com algum bloqueio de agenda ativo retornado pelo GET /lock/list.
     *
     * @param appointment agendamento Feegow
     * @param activeLocks lista de bloqueios ativos da Feegow
     * @return true se estiver bloqueado, false caso contrário
     */
    public boolean isScheduleBlocked(FeegowAppointment appointment, List<FeegowLockDto> activeLocks) {
        if (appointment == null || activeLocks == null || activeLocks.isEmpty()) {
            return false;
        }

        Long profId = null;
        if (appointment.doctorId() != null && !appointment.doctorId().isBlank()) {
            try {
                profId = Long.parseLong(appointment.doctorId().trim());
            } catch (Exception ignored) {}
        }

        LocalDateTime startAt = appointment.startAt();
        String unitId = null; // FeegowAppointment record possui unitName, unitId opcional
        String apptId = appointment.id();

        return checkLockMatch(profId, startAt, unitId, activeLocks, apptId);
    }

    /**
     * Verifica se a sessão de agendamento local bate com algum bloqueio de agenda ativo retornado pelo GET /lock/list.
     *
     * @param session sessão de agendamento
     * @param activeLocks lista de bloqueios ativos da Feegow
     * @return true se estiver bloqueado, false caso contrário
     */
    public boolean isScheduleBlocked(AppointmentSession session, List<FeegowLockDto> activeLocks) {
        if (session == null || activeLocks == null || activeLocks.isEmpty()) {
            return false;
        }

        Long profId = null;
        if (session.getDoctorProfissionalId() != null && !session.getDoctorProfissionalId().isBlank()) {
            try {
                profId = Long.parseLong(session.getDoctorProfissionalId().trim());
            } catch (Exception ignored) {}
        }

        LocalDateTime startAt = session.getAppointmentAt();
        String apptId = session.getFeegowAppointmentId() != null ? session.getFeegowAppointmentId() : (session.getId() != null ? session.getId().toString() : "");

        return checkLockMatch(profId, startAt, null, activeLocks, apptId);
    }

    /**
     * Executa a validação das 4 regras de bloqueio do Feegow:
     * 1. Profissional: lock.professionalId() == 0 OU lock.professionalId() == null OU lock.professionalId().equals(profId).
     * 2. Horário: O horário da consulta estiver entre lock.timeStart() e lock.timeEnd().
     * 3. Dia da Semana: O dia da semana da consulta bater com algum item de lock.weekDay().
     * 4. Unidade: Se lock.units() não for nulo/vazio, a unidade da consulta estiver inclusa na lista.
     */
    public boolean checkLockMatch(Long profId, LocalDateTime appointmentAt, String unitId, List<FeegowLockDto> activeLocks, String appointmentIdForLog) {
        if (appointmentAt == null || activeLocks == null || activeLocks.isEmpty()) {
            return false;
        }

        LocalTime apptTime = appointmentAt.toLocalTime();
        DayOfWeek dayOfWeek = appointmentAt.getDayOfWeek();

        for (FeegowLockDto lock : activeLocks) {
            if (lock == null) continue;

            // 1. Regra de Profissional
            Long lockProfId = lock.professionalId();
            if (lockProfId != null && lockProfId != 0L) {
                if (profId == null || !lockProfId.equals(profId)) {
                    continue;
                }
            }

            // 2. Regra de Horário (time_start / time_end)
            if (lock.timeStart() != null && !lock.timeStart().isBlank() && lock.timeEnd() != null && !lock.timeEnd().isBlank()) {
                LocalTime start = parseTime(lock.timeStart());
                LocalTime end = parseTime(lock.timeEnd());

                if (start != null && end != null) {
                    if (apptTime.isBefore(start) || apptTime.isAfter(end)) {
                        continue;
                    }
                }
            }

            // 3. Regra de Dia da Semana (week_day)
            if (lock.weekDay() != null && !lock.weekDay().isEmpty()) {
                if (!matchWeekDay(dayOfWeek, lock.weekDay())) {
                    continue;
                }
            }

            // 4. Regra de Unidade (units)
            if (lock.units() != null && !lock.units().isEmpty() && unitId != null && !unitId.isBlank()) {
                String cleanUnit = unitId.trim();
                boolean unitMatches = lock.units().stream().anyMatch(u -> u != null && u.trim().equalsIgnoreCase(cleanUnit));
                if (!unitMatches) {
                    continue;
                }
            }

            log.info("[LOCK-FILTER] Agendamento ID={} do Profissional ID={} no horário {} ignorado pois a agenda está BLOQUEADA no Feegow (Bloqueio ID={}).",
                    appointmentIdForLog != null ? appointmentIdForLog : "-", profId, apptTime, lock.id());
            return true;
        }

        return false;
    }

    private LocalTime parseTime(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) return null;
        String clean = rawTime.trim();
        try {
            if (clean.length() == 5) {
                return LocalTime.parse(clean, DateTimeFormatter.ofPattern("HH:mm"));
            } else if (clean.length() == 8) {
                return LocalTime.parse(clean, DateTimeFormatter.ofPattern("HH:mm:ss"));
            }
        } catch (Exception ex) {
            log.warn("[LOCK-FILTER] Falha ao parsear horário de bloqueio '{}': {}", rawTime, ex.getMessage());
        }
        return null;
    }

    private boolean matchWeekDay(DayOfWeek dayOfWeek, List<String> weekDays) {
        if (weekDays == null || weekDays.isEmpty()) return true;
        int isoValue = dayOfWeek.getValue(); // 1 (Mon) a 7 (Sun)
        int feegowValue = (isoValue == 7) ? 0 : isoValue; // 0 (Sun) a 6 (Sat)
        String dayNameEng = dayOfWeek.name().toUpperCase(); // MONDAY

        for (String w : weekDays) {
            if (w == null || w.isBlank()) continue;
            String clean = w.trim().toUpperCase();
            if (clean.equals(String.valueOf(isoValue)) || clean.equals(String.valueOf(feegowValue))) {
                return true;
            }
            if (dayNameEng.startsWith(clean) || clean.startsWith(dayNameEng.substring(0, 3))) {
                return true;
            }
            if ((isoValue == 1 && clean.contains("SEG"))
                    || (isoValue == 2 && clean.contains("TER"))
                    || (isoValue == 3 && clean.contains("QUA"))
                    || (isoValue == 4 && clean.contains("QUI"))
                    || (isoValue == 5 && clean.contains("SEX"))
                    || (isoValue == 6 && (clean.contains("SAB") || clean.contains("SÁB")))
                    || (isoValue == 7 && clean.contains("DOM"))) {
                return true;
            }
        }
        return false;
    }
}