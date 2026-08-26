package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipContactUpdateCommand;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Fábrica responsável por montar e normalizar os comandos e payloads LIME
 * enviados para a API do Blip (Take Blip).
 */
@Slf4j
@Component
public class BlipContextPayloadFactory {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Map<String, Object> buildGetContextCommand(String normalizedIdentity, String key) {
        return Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "get",
            "uri", "/contexts/" + normalizedIdentity + "/" + key
        );
    }

    public Map<String, Object> buildSetContextCommand(String normalizedIdentity, String key, String value) {
        return Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@msging.net",
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/" + key,
            "type", "text/plain",
            "metadata", Map.of("expiration", "86400"),
            "resource", value
        );
    }

    public Map<String, Object> buildDeleteContextCommand(String normalizedIdentity, String key) {
        return Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "delete",
            "uri", "/contexts/" + normalizedIdentity + "/" + key
        );
    }

    public Map<String, Object> buildSetJsonContextCommand(String normalizedIdentity, String key, String jsonString) {
        LinkedHashMap<String, Object> command = new LinkedHashMap<>();
        command.put("id", UUID.randomUUID().toString());
        command.put("to", "postmaster@msging.net");
        command.put("method", "set");
        command.put("uri", "/contexts/" + normalizedIdentity + "/" + key);
        command.put("type", "text/plain");
        command.put("metadata", Map.of("expiration", "86400"));
        command.put("resource", jsonString);
        return command;
    }

    public Map<String, Object> buildMasterStateCommand(String normalizedIdentity, String targetBot, String operation) {
        String stateId = operation != null && !operation.isBlank() ? operation : "stateid";
        String flowId = targetBot.contains("@") ? targetBot.substring(0, targetBot.indexOf('@')) : targetBot;
        String combined = stateId + "@" + flowId;
        String encodedState = URLEncoder.encode(combined, StandardCharsets.UTF_8);

        return Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/" + encodedState,
            "type", "text/plain",
            "metadata", Map.of("expiration", "86400"),
            "resource", targetBot
        );
    }

    public Map<String, Object> buildBuilderMasterStateCommand(String normalizedIdentity, String stateId) {
        return Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/master-state",
            "type", "text/plain",
            "metadata", Map.of("expiration", "86400"),
            "resource", stateId
        );
    }

    public Map<String, Object> buildUserStateCommand(String normalizedIdentity, String stateName) {
        return Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/state",
            "type", "text/plain",
            "resource", stateName
        );
    }

    public BlipContactUpdateCommand buildContactUpdateCommand(
            String identity,
            String name,
            String taxDocument,
            String birthDate,
            Map<String, String> extras) {
        BlipContactUpdateCommand command = new BlipContactUpdateCommand();
        BlipContactUpdateCommand.ContactResource resource = new BlipContactUpdateCommand.ContactResource();
        resource.setIdentity(identity);
        if (name != null) {
            resource.setName(name);
        }
        resource.setTaxDocument(taxDocument);
        resource.setBirthDate(birthDate);
        resource.setExtras(extras);
        command.setResource(resource);
        return command;
    }

    public String convertBirthdateToBlipFormat(String birthdate) {
        if (birthdate == null || birthdate.isBlank()) {
            return null;
        }
        String clean = birthdate.trim();
        try {
            if (clean.matches("\\d{2}/\\d{2}/\\d{4}")) {
                LocalDate date = LocalDate.parse(clean, DD_MM_YYYY);
                return date.format(YYYY_MM_DD) + "T00:00:00Z";
            }
            if (clean.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return clean + "T00:00:00Z";
            }
        } catch (Exception ex) {
            log.warn("Falha ao converter data de nascimento para formato do Blip: {}", birthdate);
        }
        return null;
    }

    public String cleanQueueName(String queueName) {
        if (queueName == null) return "";
        String cleaned = queueName.replace("\u200E", "");
        cleaned = cleaned.replaceAll("(?i)null", "");
        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned.trim();
    }
}
