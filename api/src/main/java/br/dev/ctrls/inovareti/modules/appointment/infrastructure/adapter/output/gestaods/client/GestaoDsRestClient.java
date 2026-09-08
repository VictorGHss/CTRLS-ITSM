package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.config.GestaoDsProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsAgendamentoItemDto;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsBuscaAgendamentoRequest;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsCadastroPacienteRequest;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsMudarStatusRequest;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.dto.GestaoDsPacienteDto;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente HTTP para consumo da API do software médico Gestão DS.
 * Suporta modo produção e modo desenvolvimento com resiliência a falhas de rede.
 */
@Slf4j
@Component
public class GestaoDsRestClient {

    private final RestClient restClient;
    private final GestaoDsProperties properties;
    private final ObjectMapper objectMapper;

    public GestaoDsRestClient(
            @Qualifier("gestaoDsHttpRestClient") RestClient restClient,
            GestaoDsProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    private String getPrefix() {
        return properties.isDevMode() ? "/api/dev-" : "/api/";
    }

    private boolean isConfigured() {
        if (!properties.isEnabled()) {
            log.debug("[GESTAODS] Integração desabilitada via configuração.");
            return false;
        }
        if (properties.getToken() == null || properties.getToken().isBlank()) {
            log.warn("[GESTAODS] Token da clínica não configurado em app.gestaods.token.");
            return false;
        }
        return true;
    }

    /**
     * Consulta dados cadastrais do paciente por CPF no Gestão DS.
     *
     * @param cpf CPF do paciente (com ou sem formatação)
     * @return Optional com os dados do paciente ou vazio se não encontrado
     */
    public Optional<GestaoDsPacienteDto> findPatientByCpf(String cpf) {
        if (!isConfigured() || cpf == null || cpf.isBlank()) {
            return Optional.empty();
        }

        String cleanCpf = cpf.replaceAll("\\D", "");
        if (cleanCpf.length() != 11) {
            log.warn("[GESTAODS] CPF inválido para consulta: {}", cpf);
            return Optional.empty();
        }

        String path = getPrefix() + "paciente/{token}/{cpf}/";
        log.info("[GESTAODS] Consultando paciente por CPF na rota {}", path.replace("{token}", "***").replace("{cpf}", cleanCpf));

        try {
            String rawJson = restClient.get()
                    .uri(path, properties.getToken(), cleanCpf)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> status != null && status.is4xxClientError(), (req, resp) -> {
                        log.warn("[GESTAODS] Paciente CPF {} retornou status de cliente: {}", cleanCpf, resp.getStatusCode());
                    })
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank() || rawJson.contains("\"status\":400") || rawJson.contains("nao encontrado")) {
                return Optional.empty();
            }

            GestaoDsPacienteDto dto = objectMapper.readValue(rawJson, GestaoDsPacienteDto.class);
            if (dto.nomeCompleto() == null && dto.cpf() == null) {
                return Optional.empty();
            }

            return Optional.of(dto);
        } catch (Exception ex) {
            log.error("[GESTAODS] Erro ao consultar paciente CPF {}: {}", cleanCpf, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Busca os agendamentos cadastrados para o paciente por CPF no Gestão DS.
     *
     * @param cpf CPF do paciente
     * @return Lista de agendamentos encontrados
     */
    public List<GestaoDsAgendamentoItemDto> findPatientAppointments(String cpf) {
        if (!isConfigured() || cpf == null || cpf.isBlank()) {
            return Collections.emptyList();
        }

        String cleanCpf = cpf.replaceAll("\\D", "");
        String path = getPrefix() + "paciente/agendamentos/";
        log.info("[GESTAODS] Buscando agendamentos do paciente CPF na rota {}", path);

        try {
            GestaoDsBuscaAgendamentoRequest request = new GestaoDsBuscaAgendamentoRequest(cleanCpf, properties.getToken());

            String rawJson = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                return Collections.emptyList();
            }

            JsonNode rootNode = objectMapper.readTree(rawJson);
            List<GestaoDsAgendamentoItemDto> items = new ArrayList<>();

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    items.add(objectMapper.treeToValue(node, GestaoDsAgendamentoItemDto.class));
                }
            } else if (rootNode.has("agendamentos") && rootNode.get("agendamentos").isArray()) {
                for (JsonNode node : rootNode.get("agendamentos")) {
                    items.add(objectMapper.treeToValue(node, GestaoDsAgendamentoItemDto.class));
                }
            } else if (rootNode.has("data") && rootNode.get("data").isArray()) {
                for (JsonNode node : rootNode.get("data")) {
                    items.add(objectMapper.treeToValue(node, GestaoDsAgendamentoItemDto.class));
                }
            } else if (!rootNode.has("status") || rootNode.get("status").asInt(200) == 200) {
                // Tenta deserializar como objeto único
                try {
                    GestaoDsAgendamentoItemDto single = objectMapper.treeToValue(rootNode, GestaoDsAgendamentoItemDto.class);
                    if (single.agendamento() != null || single.dataAgendamento() != null) {
                        items.add(single);
                    }
                } catch (Exception ignored) {}
            }

            log.info("[GESTAODS] Encontrados {} agendamento(s) para o CPF {}", items.size(), cleanCpf);
            return items;
        } catch (Exception ex) {
            log.error("[GESTAODS] Erro ao buscar agendamentos para CPF {}: {}", cleanCpf, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Atualiza o status de confirmação ou cancelamento do agendamento no Gestão DS.
     *
     * @param appointmentId Identificador/Token do agendamento
     * @param confirmed Se a consulta foi confirmada
     * @param canceled Se a consulta foi cancelada
     * @param reason Motivo do cancelamento (opcional)
     * @return true se atualizado com sucesso
     */
    public boolean updateAppointmentStatus(String appointmentId, Boolean confirmed, Boolean canceled, String reason) {
        if (!isConfigured() || appointmentId == null || appointmentId.isBlank()) {
            return false;
        }

        String path = getPrefix() + "paciente/agendamentos/";
        log.info("[GESTAODS] Atualizando status agendamento {}: confirmado={}, cancelado={}", appointmentId, confirmed, canceled);

        try {
            GestaoDsMudarStatusRequest request = new GestaoDsMudarStatusRequest(
                    properties.getToken(),
                    appointmentId,
                    confirmed,
                    canceled,
                    reason
            );

            restClient.put()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[GESTAODS] Status do agendamento {} atualizado com sucesso.", appointmentId);
            return true;
        } catch (Exception ex) {
            log.error("[GESTAODS] Falha ao atualizar status do agendamento {}: {}", appointmentId, ex.getMessage());
            return false;
        }
    }

    /**
     * Cadastra um novo paciente no sistema Gestão DS.
     *
     * @param request Dados cadastrais do novo paciente
     * @return Optional com o paciente criado
     */
    public Optional<GestaoDsPacienteDto> registerPatient(GestaoDsCadastroPacienteRequest request) {
        if (!isConfigured() || request == null) {
            return Optional.empty();
        }

        String path = getPrefix() + "paciente/cadastrar/";
        log.info("[GESTAODS] Cadastrando novo paciente no Gestão DS: CPF {}", request.cpf());

        try {
            String rawJson = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                return Optional.empty();
            }

            GestaoDsPacienteDto dto = objectMapper.readValue(rawJson, GestaoDsPacienteDto.class);
            return Optional.of(dto);
        } catch (Exception ex) {
            log.error("[GESTAODS] Erro ao cadastrar paciente no Gestão DS: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
