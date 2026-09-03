package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.FeegowSearchResponseDto;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowAppointmentClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.FeegowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import br.dev.ctrls.inovareti.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de infraestrutura FeegowRestClientAdapter usando o RestClient corporativo via FeegowAppointmentClient.
 * Comentários em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeegowRestClientAdapter implements FeegowClientPort {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm[:ss]");

    private final FeegowAppointmentClient appointmentClient;
    private final PatientExternalPort patientExternalPort;
    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final ProfessionalExternalPort professionalExternalPort;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final FeegowProperties feegowProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Cacheable(value = CacheConfig.CACHE_PATIENT_ACCESS_INFO, key = "#appointmentId", unless = "#result == null or !#result.isPresent()")
    public Optional<FeegowPatientAccessInfo> fetchPatientAccessInfo(String appointmentId) {
        if (appointmentId == null || appointmentId.isBlank()) {
            return Optional.empty();
        }

        try {
            // Constrói a URL para buscar o agendamento específico no Feegow pelo id_agendamento
            URI uri = UriComponentsBuilder.fromUriString(appointmentMotorProperties.getFeegowBaseUrl())
                    .path(appointmentMotorProperties.getFeegowSearchPath())
                    .queryParam("agendamento_id", appointmentId.trim())
                    .build()
                    .toUri();

            log.info("[FEEGOW-ACCESS] Buscando agendamento ID {} na URL: {}", appointmentId, uri);
            ResponseEntity<String> response = appointmentClient.searchAppointments(uri, getAccessToken());

            if (response.getBody() == null || response.getBody().isBlank()) {
                log.warn("[FEEGOW-ACCESS] Resposta vazia da Feegow para agendamento ID: {}", appointmentId);
                return Optional.empty();
            }

            // Executa o mapeamento da resposta para o DTO FeegowSearchResponseDto
            FeegowSearchResponseDto mappedResponse = objectMapper.readValue(response.getBody(), FeegowSearchResponseDto.class);
            List<FeegowSearchResponseDto.FeegowSearchAppointmentDto> appointments = mappedResponse.appointments();

            if (appointments == null || appointments.isEmpty()) {
                log.warn("[FEEGOW-ACCESS] Nenhum agendamento encontrado no Feegow para o ID: {}", appointmentId);
                return Optional.empty();
            }

            // Obtém o primeiro agendamento retornado da busca utilizando Sequenced Collections (Java 21)
            FeegowSearchResponseDto.FeegowSearchAppointmentDto appDto = appointments.getFirst();
            String patientId = appDto.patientId();

            if (patientId == null || patientId.isBlank()) {
                log.warn("[FEEGOW-ACCESS] Agendamento ID {} sem patientId cadastrado.", appointmentId);
                return Optional.empty();
            }

            String resolvedCpf = (appDto.patientCpf() != null && !appDto.patientCpf().isBlank())
                    ? appDto.patientCpf().replaceAll("\\D", "")
                    : "";
            String resolvedName = (appDto.patientName() != null && !appDto.patientName().isBlank())
                    ? appDto.patientName().trim()
                    : "";
            String resolvedPhone = (appDto.patientPhone() != null && !appDto.patientPhone().isBlank())
                    ? appDto.patientPhone().trim()
                    : "";

            // Apenas busca prontuário se CPF ou Nome estiverem ausentes no retorno do agendamento
            if (resolvedCpf.isBlank() || resolvedName.isBlank()) {
                log.info("[FEEGOW-ACCESS] Dados cadastrais ausentes no agendamento. Buscando prontuário do paciente ID: {}", patientId);
                FeegowPatient patient = patientExternalPort.patientInfo(patientId);
                if (patient != null) {
                    if (resolvedCpf.isBlank() && patient.cpf() != null) {
                        resolvedCpf = patient.cpf().replaceAll("\\D", "");
                    }
                    if (resolvedName.isBlank() && patient.name() != null) {
                        resolvedName = patient.name().trim();
                    }
                    if (resolvedPhone.isBlank() && patient.phone() != null) {
                        resolvedPhone = patient.phone().trim();
                    }
                }
            }

            // Realiza parse de data e hora do agendamento
            LocalDate date = null;
            if (appDto.appointmentDate() != null) {
                date = LocalDate.parse(appDto.appointmentDate().trim(), DATE_FORMATTER);
            }
            LocalTime time = null;
            if (appDto.appointmentTime() != null) {
                time = LocalTime.parse(appDto.appointmentTime().trim(), TIME_FORMATTER);
            }

            String doctorId = appDto.doctorId();
            String doctorName = appDto.doctorName() != null ? appDto.doctorName().trim() : "";

            if ((doctorName == null || doctorName.isBlank()) && doctorId != null && !doctorId.isBlank()) {
                // 1. Tenta buscar nas configurações locais de médicos cadastrados
                try {
                    Long docIdLong = Long.parseLong(doctorId.trim());
                    Optional<DoctorConfiguration> docConfigOpt = doctorConfigurationRepository.findById(docIdLong);
                    if (docConfigOpt.isPresent() && docConfigOpt.get().getDoctorName() != null && !docConfigOpt.get().getDoctorName().isBlank()) {
                        doctorName = docConfigOpt.get().getDoctorName().trim();
                    }
                } catch (Exception ex) {
                    log.debug("[FEEGOW-ACCESS] Erro ao buscar médico localmente por ID {}: {}", doctorId, ex.getMessage());
                }

                // 2. Se ainda não resolveu, busca na API de profissionais da Feegow (com cache Redis)
                if ((doctorName == null || doctorName.isBlank()) && professionalExternalPort != null) {
                    try {
                        String resolved = professionalExternalPort.getProfessionalName(doctorId.trim());
                        if (resolved != null && !resolved.isBlank()) {
                            doctorName = resolved.trim();
                        }
                    } catch (Exception ex) {
                        log.warn("[FEEGOW-ACCESS] Falha ao consultar nome do profissional ID {}: {}", doctorId, ex.getMessage());
                    }
                }
            }

            FeegowPatientAccessInfo info = new FeegowPatientAccessInfo(
                appointmentId,
                patientId,
                resolvedName,
                resolvedCpf,
                date,
                time,
                doctorId,
                doctorName,
                resolvedPhone
            );

            return Optional.of(info);
        } catch (Exception ex) {
            log.error("[FEEGOW-ACCESS] Erro ao buscar informações de acesso do agendamento {}: {}", appointmentId, ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    private String getAccessToken() {
        String apiKey = feegowProperties.getApiKey();
        if (apiKey == null) {
            return "";
        }
        String normalized = apiKey.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        return normalized;
    }
}
