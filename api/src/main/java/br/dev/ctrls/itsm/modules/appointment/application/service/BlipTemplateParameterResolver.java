package br.dev.ctrls.itsm.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.dev.ctrls.itsm.modules.appointment.application.dto.AppointmentTemplateData;
import br.dev.ctrls.itsm.modules.appointment.domain.model.AppointmentTemplateMapping;
import br.dev.ctrls.itsm.modules.appointment.domain.port.output.AppointmentTemplateMappingRepositoryPort;
import br.dev.ctrls.itsm.modules.appointment.infrastructure.utils.StringSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável por mapear, interpolar e sanitizar parâmetros dinâmicos
 * de mensagens e templates do WhatsApp (WABA / Take Blip).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class BlipTemplateParameterResolver {

    private final AppointmentTemplateMappingRepositoryPort templateMappingRepository;

    /**
     * Identifica se o template é estático com 0 parâmetros cadastrados na Meta.
     */
    public boolean isStaticZeroParamTemplate(String templateName) {
        if (templateName == null || templateName.isBlank()) return false;
        String norm = templateName.trim().toLowerCase().replace(" ", "_");
        return "aviso_agendamento_grupo".equals(norm);
    }

    /**
     * Constrói os parâmetros dinâmicos baseados no mapeamento do banco de dados
     * ou nos fallbacks seguros da clínica.
     */
    public List<Map<String, String>> buildDynamicParameters(String templateName, AppointmentTemplateData appointmentData) {
        if (isStaticZeroParamTemplate(templateName)) {
            log.info("[TEMPLATE MAPPING] Template estático '{}' configurado para 0 parâmetros na Meta.", templateName);
            return List.of();
        }

        List<AppointmentTemplateMapping> mappings = templateMappingRepository
            .findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(templateName);

        if (mappings.isEmpty()) {
            log.info("[TEMPLATE MAPPING] Nenhum mapeamento no banco para '{}'. Aplicando fallback automático (paciente, médico, horário).", templateName);
            String pName = appointmentData != null ? appointmentData.patientName() : "Paciente";
            String dName = appointmentData != null ? appointmentData.doctorName() : "Clínica Inovare";
            String aTime = appointmentData != null ? appointmentData.appointmentTime() : "horário agendado";

            pName = StringSanitizer.sanitize(pName != null && !pName.isBlank() ? pName : "Paciente");
            dName = StringSanitizer.sanitize(dName != null && !dName.isBlank() ? dName : "Clínica Inovare");
            aTime = StringSanitizer.sanitize(aTime != null && !aTime.isBlank() ? aTime : "horário agendado");

            List<Map<String, String>> fallbackParams = new ArrayList<>();
            fallbackParams.add(Map.of("type", "text", "text", pName));
            fallbackParams.add(Map.of("type", "text", "text", dName));
            fallbackParams.add(Map.of("type", "text", "text", aTime));
            return fallbackParams;
        }

        List<Map<String, String>> parameters = new ArrayList<>();
        for (AppointmentTemplateMapping mapping : mappings) {
            String fieldName = mapping.getFeegowFieldName();
            String value = resolveDynamicFieldValue(appointmentData, fieldName);

                String safeValue = "Recepção";
                if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim()) && !"Informação não disponível".equalsIgnoreCase(value.trim())) {
                    safeValue = value.trim();
                } else {
                    if (fieldName != null) {
                        String lowerField = fieldName.toLowerCase();
                        if (lowerField.contains("profissional") || lowerField.contains("doctor") || lowerField.contains("medico")) {
                            safeValue = "Clínica Inovare";
                        } else if (lowerField.contains("patient") || lowerField.contains("paciente")) {
                            safeValue = "Paciente";
                        }
                    }
                }

                safeValue = StringSanitizer.sanitize(safeValue);
                parameters.add(Map.of("type", "text", "text", safeValue));
        }

        log.debug("[PARAMS] Template [{}]: {} parâmetro(s) mapeados", templateName, parameters.size());
        return parameters;
    }

    private String resolveDynamicFieldValue(AppointmentTemplateData data, String fieldName) {
        if (data == null || fieldName == null || fieldName.isBlank()) return null;

        String key = fieldName.trim().toLowerCase();
        return switch (key) {
            // Paciente
            case "patientname", "patient_name", "nome_paciente", "paciente"   -> data.patientName();
            case "patientphone", "patient_phone", "telefone_paciente"          -> data.patientPhone();
            case "patientid", "patient_id"                                     -> data.patientId();

            // Médico
            case "doctorname", "doctor_name",
                 "profissionalnome", "profissional_nome",
                 "nome_medico", "medico", "professional_name"                  -> data.doctorName();
            case "doctorid", "doctor_id", "profissional_id"                    -> data.doctorId();
            case "specialty", "especialidade"                                  -> data.specialty();

            // Agenda
            case "appointmentdate", "appointment_date", "data_consulta",
                 "data"                                                         -> data.appointmentDate();
            case "appointmentdateshort", "appointment_date_short", "data_curta" -> data.appointmentDateShort();
            case "appointmenttime", "appointment_time", "hora", "hora_consulta" -> data.appointmentTime();
            case "appointmentdatetime", "appointment_date_time", "data_hora"    -> data.appointmentDateTime();
            case "appointmentid", "appointment_id"                             -> data.appointmentId();

            // Unidade
            case "unitname", "unit_name", "unidade", "local"                   -> data.unitName();

            default -> {
                log.warn("[FIELD MAPPING] Campo '{}' não mapeado em AppointmentTemplateData. Revise a tabela appointment_template_mapping.", fieldName);
                yield null;
            }
        };
    }
}
