package br.dev.ctrls.inovareti.modules.access.application.usecase;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessValidationResult;
import br.dev.ctrls.inovareti.modules.access.domain.model.CpfValidator;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessWindowCalculator;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.AccessCredentialResponse;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de Uso: Consulta e Resolução Completa de Credenciais de Acesso.
 * Orquestra validação de desafios de segurança, agrupamento de sessões do paciente,
 * detecção de consultas do dia e mapeamento de respostas para exibição no portal web / quiosque.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetCredentialsUseCase {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final FeegowClientPort feegowClientPort;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentExternalPort appointmentExternalPort;
    private final ValidateAccessChallengeUseCase validateAccessChallengeUseCase;
    private final ProcessAccessRequestUseCase processAccessRequestUseCase;

    public List<AccessCredentialResponse> execute(String idAgendamento, String phoneDigits, String token) {
        log.info("[GetCredentials] Consulta de credenciais para agendamento ID: {} (token={}, phoneDigits={})", 
                idAgendamento, token != null && !token.isBlank() ? "presente" : "ausente", phoneDigits);

        // Se for rota pública ou auto-cadastro (IMG- ou INOV-), busca diretamente do banco sem desafio Feegow
        if (idAgendamento != null && (idAgendamento.startsWith("IMG-") || idAgendamento.startsWith("INOV-"))) {
            List<AccessCredential> creds = accessCredentialRepositoryPort.findByAppointmentId(idAgendamento);
            if (creds.isEmpty()) {
                return List.of();
            }
            return creds.stream()
                .map(c -> new AccessCredentialResponse(
                    c.getAppointmentId(),
                    c.getName(),
                    c.getUserType(),
                    c.getLocator(),
                    c.getAccessCredential(),
                    c.getCpf(),
                    idAgendamento.startsWith("INOV-") ? "Inovare – Serviços de Saúde" : "Clínica Da Imagem - Unidade Inovare",
                    resolveDisplayDate(idAgendamento),
                    "06:00",
                    "23:59",
                    c.getId()
                ))
                .toList();
        }

        // Executa a validação do desafio (por token criptográfico ou 4 dígitos do telefone)
        FeegowPatientAccessInfo accessInfo = validateAccessChallengeUseCase.validateAccessChallenge(idAgendamento, phoneDigits, token);

        List<String> appointmentIds = new ArrayList<>();
        appointmentIds.add(idAgendamento);

        try {
            var mainSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(idAgendamento);
            if (mainSessionOpt.isPresent() && mainSessionOpt.get().getCurrentGroupId() != null) {
                var groupSessions = appointmentSessionRepository.findByCurrentGroupId(mainSessionOpt.get().getCurrentGroupId());
                for (var s : groupSessions) {
                    if (s.getFeegowAppointmentId() != null && !s.getFeegowAppointmentId().equalsIgnoreCase(idAgendamento)) {
                        appointmentIds.add(s.getFeegowAppointmentId());
                    }
                }
                log.info("[GetCredentials] Encontrado grupo com {} agendamentos: {}", appointmentIds.size(), appointmentIds);
            }
        } catch (Exception ex) {
            log.warn("[GetCredentials] Erro ao buscar grupo de sessões para o agendamento {}: {}", idAgendamento, ex.getMessage());
        }

        LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
        if (accessInfo.patientId() != null) {
            try {
                var patientSessions = appointmentSessionRepository.findByPatientId(accessInfo.patientId());
                for (var s : patientSessions) {
                    boolean isToday = (s.getAppointmentAt() != null && s.getAppointmentAt().toLocalDate().equals(today))
                                   || (s.getCreatedAt() != null && s.getCreatedAt().toLocalDate().equals(today));
                    if (isToday && s.getFeegowAppointmentId() != null && !appointmentIds.contains(s.getFeegowAppointmentId())) {
                        appointmentIds.add(s.getFeegowAppointmentId());
                        log.info("[GetCredentials] Auto-detectado agendamento de hoje ({}) para o paciente ID: {}. Adicionado ao escopo.", s.getFeegowAppointmentId(), accessInfo.patientId());
                    }
                }
            } catch (Exception ex) {
                log.warn("[GetCredentials] Erro ao auto-detectar sessões de hoje para o paciente: {}", ex.getMessage());
            }

            try {
                List<FeegowAppointment> todayFeegowApps = appointmentExternalPort.searchAppointments(today, 0);
                for (FeegowAppointment fa : todayFeegowApps) {
                    if (accessInfo.patientId().equals(fa.patientId()) && fa.id() != null && !appointmentIds.contains(fa.id())) {
                        appointmentIds.add(fa.id());
                        log.info("[GetCredentials] Auto-detectada consulta adicional de hoje ({}) no Feegow para o paciente ID: {}. Adicionada ao escopo.", fa.id(), accessInfo.patientId());
                    }
                }
            } catch (Exception ex) {
                log.warn("[GetCredentials] Erro ao buscar consultas adicionais do dia no Feegow: {}", ex.getMessage());
            }
        }

        String patientCpf = CpfValidator.cleanCpf(accessInfo.cpf());
        if (patientCpf.length() == 11) {
            try {
                List<AccessCredential> todayCreds = accessCredentialRepositoryPort.findByCpf(patientCpf);
                for (var c : todayCreds) {
                    if (c.getCreatedAt() != null && c.getCreatedAt().toLocalDate().equals(today)) {
                        if (c.getAppointmentId() != null && !appointmentIds.contains(c.getAppointmentId())) {
                            appointmentIds.add(c.getAppointmentId());
                            log.info("[GetCredentials] Auto-detectado agendamento com credencial hoje ({}) por CPF: {}", c.getAppointmentId(), patientCpf);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[GetCredentials] Erro ao auto-detectar credenciais de hoje por CPF: {}", ex.getMessage());
            }
        }

        List<AccessCredential> credentials = new ArrayList<>();

        for (String id : appointmentIds) {
            List<AccessCredential> appCreds = accessCredentialRepositoryPort.findByAppointmentId(id);
            boolean hasOnlyContingency = !appCreds.isEmpty() && appCreds.stream()
                    .allMatch(c -> c.getAccessCredential() != null && c.getAccessCredential().startsWith("CRED-"));

            boolean hasPatientWithInvalidCpf = !appCreds.isEmpty() && appCreds.stream()
                    .filter(c -> c.getUserType() == UserType.PATIENT)
                    .anyMatch(c -> c.getCpf() == null || !CpfValidator.isValidCpf(c.getCpf()));

            if (appCreds.isEmpty() || hasOnlyContingency || hasPatientWithInvalidCpf) {
                log.info("[GetCredentials] Credenciais não encontradas, contingenciais ou com CPF inválido para o agendamento ID: {}. Tentando obter credencial real na GerAcesso...", id);
                try {
                    AccessValidationResult result = processAccessRequestUseCase.execute(id, null, null);
                    if (result.authorized()) {
                        appCreds = accessCredentialRepositoryPort.findByAppointmentId(id);
                    } else if (result.requiresCpfFallback()) {
                        String patientNameFallback = "Paciente";
                        try {
                            if (id != null && id.matches("\\d+")) {
                                var opt = feegowClientPort.fetchPatientAccessInfo(id);
                                if (opt.isPresent() && opt.get().name() != null) {
                                    patientNameFallback = opt.get().name();
                                }
                            }
                        } catch (Exception ignored) {}

                        AccessCredential ghost = AccessCredential.builder()
                                .id(UUID.randomUUID())
                                .appointmentId(id)
                                .name(patientNameFallback)
                                .cpf("")
                                .userType(UserType.PATIENT)
                                .accessCredential("CPF_MISSING")
                                .locator("CPF_MISSING")
                                .createdAt(LocalDateTime.now())
                                .build();
                        appCreds = List.of(ghost);
                    }
                } catch (Exception ex) {
                    log.error("[GetCredentials] Falha ao processar acesso em tempo real para agendamento ID {}: {}", id, ex.getMessage());
                }
            }
            credentials.addAll(appCreds);
        }

        credentials.sort((c1, c2) -> {
            boolean isC1Main = c1.getUserType() == UserType.PATIENT && c1.getAppointmentId().equalsIgnoreCase(idAgendamento);
            boolean isC2Main = c2.getUserType() == UserType.PATIENT && c2.getAppointmentId().equalsIgnoreCase(idAgendamento);
            if (isC1Main && !isC2Main) return -1;
            if (!isC1Main && isC2Main) return 1;

            if (c1.getUserType() == UserType.PATIENT && c2.getUserType() != UserType.PATIENT) return -1;
            if (c1.getUserType() != UserType.PATIENT && c2.getUserType() == UserType.PATIENT) return 1;

            return 0;
        });

        String appointmentDateTime = "";
        String opensAt = "";
        String closesAt = "23:00";
        if (accessInfo.appointmentDate() != null) {
            if (accessInfo.appointmentTime() != null) {
                appointmentDateTime = LocalDateTime.of(accessInfo.appointmentDate(), accessInfo.appointmentTime())
                        .format(DATE_TIME_FORMATTER);
                LocalTime openingTime = accessInfo.appointmentTime().minusMinutes(120);
                opensAt = openingTime.format(TIME_FORMATTER);
            } else {
                appointmentDateTime = accessInfo.appointmentDate().format(DATE_FORMATTER);
                opensAt = "08:00";
            }
        }

        String doctorName = accessInfo.doctorName() != null ? accessInfo.doctorName() : "";
        final String finalAppointmentDateTime = appointmentDateTime;
        final String finalDoctorName = doctorName;
        final String finalOpensAt = opensAt;
        final String finalClosesAt = closesAt;

        Map<String, FeegowPatientAccessInfo> challengeCache = new HashMap<>();
        List<AccessCredentialResponse> response = new ArrayList<>();
        for (AccessCredential c : credentials) {
            String itemAppointmentDateTime = finalAppointmentDateTime;
            String itemDoctorName = finalDoctorName;
            String itemOpensAt = finalOpensAt;
            String itemClosesAt = finalClosesAt;

            if (!c.getAppointmentId().equalsIgnoreCase(idAgendamento) 
                    && !"CPF_MISSING".equals(c.getAccessCredential()) 
                    && c.getAppointmentId() != null 
                    && c.getAppointmentId().matches("\\d+")) {
                try {
                    FeegowPatientAccessInfo specificInfo = null;
                    if (challengeCache.containsKey(c.getAppointmentId())) {
                        specificInfo = challengeCache.get(c.getAppointmentId());
                    } else {
                        var optInfo = feegowClientPort.fetchPatientAccessInfo(c.getAppointmentId());
                        if (optInfo.isPresent()) {
                            specificInfo = optInfo.get();
                            challengeCache.put(c.getAppointmentId(), specificInfo);
                        }
                    }
                    if (specificInfo != null) {
                        if (specificInfo.appointmentDate() != null) {
                            if (specificInfo.appointmentTime() != null) {
                                itemAppointmentDateTime = LocalDateTime.of(specificInfo.appointmentDate(), specificInfo.appointmentTime())
                                        .format(DATE_TIME_FORMATTER);
                                LocalTime openingTime = specificInfo.appointmentTime().minusMinutes(120);
                                itemOpensAt = openingTime.format(TIME_FORMATTER);
                            } else {
                                itemAppointmentDateTime = specificInfo.appointmentDate().format(DATE_FORMATTER);
                                itemOpensAt = "08:00";
                            }
                        }
                        if (specificInfo.doctorName() != null && !specificInfo.doctorName().isBlank()) {
                            itemDoctorName = specificInfo.doctorName();
                        }
                    }
                } catch (Exception ex) {
                    log.warn("[GetCredentials] Não foi possível obter detalhes para o agendamento {}: {}", c.getAppointmentId(), ex.getMessage());
                }
            }

            if (c.getAppointmentId() != null && (c.getAppointmentId().startsWith("INOV-") || c.getAppointmentId().startsWith("IMG-"))) {
                if (c.getDoctorName() != null && !c.getDoctorName().isBlank()) {
                    itemDoctorName = c.getDoctorName();
                } else if (itemDoctorName == null || itemDoctorName.isBlank()) {
                    itemDoctorName = c.getAppointmentId().startsWith("INOV-") ? "Inovare – Serviços de Saúde" : "Clínica Da Imagem - Unidade Inovare";
                }
                itemAppointmentDateTime = resolveDisplayDate(c.getAppointmentId());
            }

            LocalDate todayDate = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
            LocalDate itemDate = null;
            
            if (c.getAppointmentId().equalsIgnoreCase(idAgendamento)) {
                itemDate = accessInfo.appointmentDate();
            } else {
                try {
                    FeegowPatientAccessInfo specificInfo = challengeCache.get(c.getAppointmentId());
                    if (specificInfo != null) {
                        itemDate = specificInfo.appointmentDate();
                    }
                } catch (Exception ignored) {}
            }
            
            if (itemDate == null) {
                itemDate = accessInfo.appointmentDate();
            }
            
            boolean isItemToday = itemDate != null && todayDate.equals(itemDate);
            boolean isItemReleased = isItemToday;
            if (c.getAppointmentId() != null && (c.getAppointmentId().startsWith("INOV-") || c.getAppointmentId().startsWith("IMG-"))) {
                isItemReleased = true;
            } else if (!isItemToday && itemDate != null) {
                isItemReleased = false;
            }

            String credentialCodeToReturn = c.getAccessCredential();
            if (!"CPF_MISSING".equals(credentialCodeToReturn)) {
                credentialCodeToReturn = isItemReleased ? c.getAccessCredential() : "BLOCKED_OUTSIDE_WINDOW";
            }

            response.add(new AccessCredentialResponse(
                    c.getAppointmentId(),
                    c.getName(),
                    c.getUserType(),
                    c.getLocator(),
                    credentialCodeToReturn,
                    c.getCpf(),
                    itemDoctorName,
                    itemAppointmentDateTime,
                    itemOpensAt,
                    itemClosesAt,
                    c.getId()
            ));
        }

        log.info("[GetCredentials] Retornando {} credencial(ais) para o agendamento ID: {}", response.size(), idAgendamento);
        return response;
    }

    private String resolveDisplayDate(String appointmentId) {
        if (appointmentId == null || appointmentId.length() < 13) {
            return "Hoje";
        }
        try {
            String datePart = appointmentId.substring(5, 13);
            LocalDate d = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate today = LocalDate.now(AccessWindowCalculator.CLINIC_ZONE);
            if (d.isEqual(today)) return "Hoje";
            if (d.isEqual(today.plusDays(1))) return "Amanhã";
            return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return "Hoje";
        }
    }
}
