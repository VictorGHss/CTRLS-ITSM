package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço centralizado responsável por determinar a elegibilidade de médicos
 * para recebimento de mensagens automáticas de confirmação e lembretes (nudges).
 *
 * Elimina disparos indevidos para médicos inativos, duplicados ou que não devem
 * receber automações.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorEligibilityService {

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;

    @Value("${notification.blocked-doctor-ids:46}")
    private String blockedDoctorIdsRaw = "46";

    public Set<String> getBlockedDoctorIds() {
        if (blockedDoctorIdsRaw == null || blockedDoctorIdsRaw.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> set = new java.util.HashSet<>();
        String[] tokens = blockedDoctorIdsRaw.split("[,;\\s]+");
        for (String token : tokens) {
            if (token != null) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    set.add(trimmed);
                }
            }
        }
        return set;
    }

    public void setBlockedDoctorIdsRaw(String blockedDoctorIdsRaw) {
        this.blockedDoctorIdsRaw = blockedDoctorIdsRaw;
    }

    /**
     * Valida se um médico é permitido para envio de confirmações e nudges,
     * considerando opcionalmente uma lista restrita sob demanda (requestedDoctorIds).
     */
    public boolean isDoctorAllowed(String doctorId, List<String> requestedDoctorIds) {
        if (requestedDoctorIds != null && !requestedDoctorIds.isEmpty()) {
            String clean = doctorId != null ? doctorId.trim() : "";
            if (!requestedDoctorIds.contains(clean)) {
                return false;
            }
        }
        return isDoctorAllowed(doctorId);
    }

    /**
     * Validação estrita de elegibilidade de médico:
     * 1. ID não nulo/em branco.
     * 2. Lista de bloqueio geral (ex: 46).
     * 3. Modo de teste restrito a testDoctorIds.
     * 4. Validação em AppointmentDoctorMapping (ignoreAutoSchedule, isActive, fila != 'inactive').
     * 5. Validação em DoctorConfiguration (isConfigActive, fila != 'inactive').
     * 6. Validação por allowlist ativa ou configuração explícita válida (sem fail-open).
     */
    public boolean isDoctorAllowed(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return false;
        }
        String docId = doctorId.trim();

        // 1. Bloqueio explícito por lista de bloqueio
        if (getBlockedDoctorIds().contains(docId)) {
            log.debug("[DOCTOR-ELIGIBILITY] Médico ID={} bloqueado por blocklist geral.", docId);
            return false;
        }

        // 2. Modo de teste ativo: permite estritamente médicos de teste
        if (appointmentMotorProperties.isTestMode()) {
            boolean allowedInTest = appointmentMotorProperties.getTestDoctorIds().contains(docId);
            if (!allowedInTest) {
                log.debug("[DOCTOR-ELIGIBILITY] Modo TESTE ativo. Médico ID={} não consta em testDoctorIds.", docId);
            }
            return allowedInTest;
        }

        // 3. Validação em AppointmentDoctorMapping
        Optional<AppointmentDoctorMapping> mappingOpt = Optional.empty();
        if (appointmentDoctorMappingRepository != null) {
            mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(docId);
            if (mappingOpt.isPresent()) {
                var mapping = mappingOpt.get();
                if (mapping.isIgnoreAutoSchedule()) {
                    log.info("[DOCTOR-ELIGIBILITY] Médico ID={} ignorado: ignoreAutoSchedule=true.", docId);
                    return false;
                }
                if (!mapping.isActive()) {
                    log.info("[DOCTOR-ELIGIBILITY] Médico ID={} ignorado: mapping.isActive=false.", docId);
                    return false;
                }
                if (mapping.getBlipQueueId() != null && mapping.getBlipQueueId().trim().equalsIgnoreCase("inactive")) {
                    log.info("[DOCTOR-ELIGIBILITY] Médico ID={} ignorado: blipQueueId='inactive'.", docId);
                    return false;
                }
            }
        }

        // 4. Validação em DoctorConfiguration
        Optional<DoctorConfiguration> configOpt = Optional.empty();
        if (doctorConfigurationRepository != null) {
            try {
                Long id = Long.parseLong(docId);
                configOpt = doctorConfigurationRepository.findById(id);
                if (configOpt.isPresent()) {
                    var cfg = configOpt.get();
                    if (!cfg.isConfigActive()) {
                        log.info("[DOCTOR-ELIGIBILITY] Médico ID={} ignorado: DoctorConfiguration.isActive=false.", docId);
                        return false;
                    }
                    if (cfg.getBlipQueueId() != null && cfg.getBlipQueueId().trim().equalsIgnoreCase("inactive")) {
                        log.info("[DOCTOR-ELIGIBILITY] Médico ID={} ignorado: config.blipQueueId='inactive'.", docId);
                        return false;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        // 5. Validação em modo de produção
        // Em modo de produção, se o médico estiver na lista de teste configurada, também é permitido (homologação paralela)
        if (appointmentMotorProperties.getTestDoctorIds().contains(docId)) {
            return true;
        }

        List<String> activeDoctorIds = appointmentMotorProperties.getActiveDoctorIds();
        if (activeDoctorIds != null && !activeDoctorIds.isEmpty()) {
            if (activeDoctorIds.contains(docId)) {
                return true;
            }
            // Se activeDoctorIds foi definido na .env, médicos fora dessa lista só entram se tiverem DoctorConfiguration explicitamente ativa com fila válida
            if (configOpt.isPresent() && configOpt.get().isConfigActive()) {
                String qId = configOpt.get().getBlipQueueId();
                if (qId != null && !qId.isBlank() && !qId.equalsIgnoreCase("inactive")) {
                    return true;
                }
            }
            if (mappingOpt.isPresent()) {
                var mapping = mappingOpt.get();
                String qId = mapping.getBlipQueueId();
                if (qId != null && !qId.isBlank() && !qId.equalsIgnoreCase("inactive") && mapping.isActive() && !mapping.isIgnoreAutoSchedule()) {
                    return true;
                }
            }
            return false;
        }

        // Se activeDoctorIds da .env estiver vazio, exige configuração explícita ativa com fila válida
        if (configOpt.isPresent() && configOpt.get().isConfigActive()) {
            String qId = configOpt.get().getBlipQueueId();
            if (qId != null && !qId.isBlank() && !qId.equalsIgnoreCase("inactive")) {
                return true;
            }
        }

        if (mappingOpt.isPresent()) {
            var mapping = mappingOpt.get();
            String qId = mapping.getBlipQueueId();
            if (qId != null && !qId.isBlank() && !qId.equalsIgnoreCase("inactive") && mapping.isActive() && !mapping.isIgnoreAutoSchedule()) {
                return true;
            }
        }

        // NUNCA executar fail-open para médicos não configurados
        return false;
    }

    /**
     * Retorna todos os IDs de médicos ativos e elegíveis para receberem disparos.
     * Utilizado na ingestão matinal (7h) e no painel de controle administrativo.
     */
    public List<String> getActiveAllowedDoctorIds() {
        if (appointmentMotorProperties.isTestMode()) {
            return appointmentMotorProperties.getTestDoctorIds().stream()
                    .filter(this::isDoctorAllowed)
                    .toList();
        }

        Set<String> candidates = new LinkedHashSet<>();
        if (appointmentMotorProperties.getActiveDoctorIds() != null) {
            candidates.addAll(appointmentMotorProperties.getActiveDoctorIds());
        }

        if (doctorConfigurationRepository != null) {
            doctorConfigurationRepository.findByIsActiveTrue().forEach(c -> {
                if (c.getFeegowProfissionalId() != null) {
                    candidates.add(String.valueOf(c.getFeegowProfissionalId()));
                }
            });
        }

        if (appointmentDoctorMappingRepository != null) {
            appointmentDoctorMappingRepository.findAll().forEach(m -> {
                if (m.getProfissionalId() != null && !m.getProfissionalId().isBlank()) {
                    String qId = m.getBlipQueueId();
                    if (qId != null && !qId.isBlank() && !qId.equalsIgnoreCase("inactive") && !m.isIgnoreAutoSchedule()) {
                        candidates.add(m.getProfissionalId().trim());
                    }
                }
            });
        }

        return candidates.stream()
                .filter(this::isDoctorAllowed)
                .toList();
    }
}
