package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipContactUpdateCommand;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Observed
public class BlipContextService {

    private final BlipLIMEClient limeClient;
    private final ObjectMapper objectMapper;
    private final AsyncTaskExecutor applicationTaskExecutor;
    private final BlipIdentityReconciler blipIdentityReconciler;
    private final BlipProperties blipProperties;
    private final BlipDeskGuardService blipDeskGuardService;
    private final BlipContextPayloadFactory blipContextPayloadFactory;

    @Value("${APP_BLIP_APPOINTMENT_ID:}")
    private String blipAppointmentId;

    public BlipContextService(
            BlipLIMEClient limeClient, 
            ObjectMapper objectMapper, 
            AsyncTaskExecutor applicationTaskExecutor,
            BlipIdentityReconciler blipIdentityReconciler,
            BlipProperties blipProperties,
            BlipDeskGuardService blipDeskGuardService,
            BlipContextPayloadFactory blipContextPayloadFactory) {
        this.limeClient = limeClient;
        this.objectMapper = objectMapper;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.blipIdentityReconciler = blipIdentityReconciler;
        this.blipProperties = blipProperties;
        this.blipDeskGuardService = blipDeskGuardService;
        this.blipContextPayloadFactory = blipContextPayloadFactory;
    }

    public String sanitizeRawIdentity(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return null;
        String clean = userIdentity.trim();
        if (clean.contains("/")) {
            clean = clean.substring(0, clean.indexOf('/'));
        }
        if (clean.contains("@desk.msging.net")) {
            int atIdx = clean.indexOf('@');
            String localPart = clean.substring(0, atIdx);
            try {
                clean = java.net.URLDecoder.decode(localPart, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
            if (clean.contains("/")) {
                clean = clean.substring(0, clean.indexOf('/'));
            }
        }
        return clean;
    }

    public String resolveMasterIdentity(String userIdentity) {
        String clean = sanitizeRawIdentity(userIdentity);
        if (clean == null || clean.isBlank()) return null;
        if (clean.contains("@tunnel.msging.net")) {
            String reconciled = blipIdentityReconciler.resolveAndReconcileIdentity(clean, null);
            if (reconciled != null && !reconciled.isBlank()) {
                return reconciled.contains("@") ? reconciled : limeClient.normalizeUserIdentity(reconciled);
            }
            String local = clean.substring(0, clean.indexOf('@'));
            if (local.contains(".")) {
                String digits = local.substring(0, local.indexOf('.')).replaceAll("\\D", "");
                if (!digits.isBlank()) {
                    return limeClient.normalizeUserIdentity(digits);
                }
            }
            return null;
        }
        return limeClient.normalizeUserIdentity(clean);
    }

    public String resolveTunnelIdentity(String userIdentity) {
        String clean = sanitizeRawIdentity(userIdentity);
        if (clean == null || clean.isBlank()) return null;
        if (clean.contains("@tunnel.msging.net")) {
            return clean;
        }
        String normalized = limeClient.normalizeUserIdentity(clean);
        String phoneDigits = normalized.contains("@") ? normalized.substring(0, normalized.indexOf('@')).replaceAll("\\D", "") : normalized.replaceAll("\\D", "");
        if (phoneDigits.isBlank()) return null;

        String subbotLocalPart = null;
        if (blipAppointmentId != null && !blipAppointmentId.isBlank() && !blipAppointmentId.toLowerCase().contains("fluxov1")) {
            subbotLocalPart = blipAppointmentId.contains("@") ? blipAppointmentId.substring(0, blipAppointmentId.indexOf('@')) : blipAppointmentId.trim();
        }
        if (subbotLocalPart == null || subbotLocalPart.isBlank()) {
            return null;
        }
        return phoneDigits + "." + subbotLocalPart + "@tunnel.msging.net";
    }

    public void setUserContextForUser(String userIdentity, String key, String value) {
        setUserContext(userIdentity, key, value);
    }

    public void setUserContextFieldsInParallel(String userIdentity, Map<String, String> fields) {
        if (userIdentity == null || userIdentity.isBlank() || fields == null || fields.isEmpty()) {
            return;
        }
        log.debug("[LIME-PARALLEL] Configurando contexto LIME em paralelo para target: {}. Campos: {}", userIdentity, fields.keySet());
        List<CompletableFuture<Void>> futures = fields.entrySet().stream()
            .map(entry -> CompletableFuture.runAsync(() -> {
                try {
                    setUserContext(userIdentity, entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    log.error("Erro ao configurar contexto para {} key: {}", userIdentity, entry.getKey(), e);
                }
            }, applicationTaskExecutor))
            .toList();
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception e) {
            log.error("Erro ao aguardar configuração de contexto para {}", userIdentity, e);
        }
    }

    public String getUserContext(String userIdentity, String key) {
        if (userIdentity == null || userIdentity.isBlank() || key == null || key.isBlank()) return null;
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        Map<String, Object> command = blipContextPayloadFactory.buildGetContextCommand(normalizedIdentity, key);

        try {
            Map<String, Object> response = limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            Map<String, Object> body = response;
            if (body == null) return null;
            Object resource = body.get("resource");
            if (resource == null) return null;

            String value;
            if (resource instanceof Map<?, ?> map) {
                Object rawValue = map.get("value");
                value = rawValue != null ? String.valueOf(rawValue) : null;
            } else {
                value = String.valueOf(resource);
            }

            if (value == null) return null;
            String normalizedValue = value.trim();
            if (normalizedValue.isBlank() || "null".equalsIgnoreCase(normalizedValue)) return null;
            return normalizedValue;
        } catch (RestClientException ex) {
            log.warn("Falha ao consultar contexto. identity={}, key={}", normalizedIdentity, key, ex);
            return null;
        }
    }

    public void setUserContext(String userIdentity, String key, String value) {
        if (userIdentity == null || userIdentity.isBlank() || value == null || value.isBlank()) return;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        if (masterIdentity != null && !masterIdentity.isBlank()) {
            futures.add(CompletableFuture.runAsync(
                () -> sendSingleUserContext(masterIdentity, key, value), applicationTaskExecutor));
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            futures.add(CompletableFuture.runAsync(
                () -> sendSingleUserContext(tunnelIdentity, key, value), applicationTaskExecutor));
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception ignored) {}
    }

    public static final String STATE_REVIEW_FINISHED = "932c1f15-4e83-4b55-b343-1d3564cf57ff";

    /**
     * Atualiza o estado do usuário no Blip (master-state) em escopo dual (Router + Túnel)
     * para transicionar a conversa para um bloco específico (ex: bloco silencioso terminal).
     */
    public void updateUserMasterState(String userIdentity, String stateId) {
        if (userIdentity == null || userIdentity.isBlank() || stateId == null || stateId.isBlank()) return;

        setUserContext(userIdentity, "master-state", stateId);
        log.info("[LIME] Master-State atualizado para o bloco silencioso 'Fim - Avaliação Enviada' (stateId={}) para user={}", stateId, userIdentity);
    }

    private final Map<String, Long> deduplicationCache = new ConcurrentHashMap<>();

    private boolean isRedundantContextCall(String deduplicationKey) {
        long now = System.currentTimeMillis();
        Long lastTime = deduplicationCache.get(deduplicationKey);
        if (lastTime != null && (now - lastTime) < 3000L) {
            log.debug("[DEDUPLICAÇÃO-BLIP] Ignorando envio redundante no contexto Blip (chave: '{}') acionado há <3s.", deduplicationKey);
            return true;
        }
        deduplicationCache.put(deduplicationKey, now);

        if (deduplicationCache.size() > 2000) {
            deduplicationCache.entrySet().removeIf(entry -> (now - entry.getValue()) > 10000L);
        }
        return false;
    }

    private void sendSingleUserContext(String normalizedIdentity, String key, String value) {
        if (isRedundantContextCall("ctx:" + normalizedIdentity + ":" + key + ":" + value)) {
            return;
        }

        Map<String, Object> command = blipContextPayloadFactory.buildSetContextCommand(normalizedIdentity, key, value);

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Contexto configurado (escopo dual). identity={}, key={}", normalizedIdentity, key);
        } catch (RestClientException ex) {
            log.warn("Falha ao configurar contexto. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    public void deleteUserContext(String userIdentity, String key) {
        if (userIdentity == null || userIdentity.isBlank() || key == null || key.isBlank()) return;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        if (masterIdentity != null && !masterIdentity.isBlank()) {
            futures.add(CompletableFuture.runAsync(
                () -> sendSingleDeleteUserContext(masterIdentity, key), applicationTaskExecutor));
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            futures.add(CompletableFuture.runAsync(
                () -> sendSingleDeleteUserContext(tunnelIdentity, key), applicationTaskExecutor));
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception ignored) {}
    }

    private void sendSingleDeleteUserContext(String normalizedIdentity, String key) {
        if (normalizedIdentity == null || normalizedIdentity.isBlank() || key == null || key.isBlank()) return;

        Map<String, Object> command = blipContextPayloadFactory.buildDeleteContextCommand(normalizedIdentity, key);

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Contexto removido. identity={}, key={}", normalizedIdentity, key);
        } catch (RestClientException ex) {
            log.warn("Falha ao remover contexto. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    /**
     * Remove sincronamente as variáveis de contexto relacionadas ao fluxo de confirmação
     * (isConfirmingAgenda, isGroupFlow, payloadclique, requiresCpfFallback) em ambos os escopos (Master e Túnel).
     */
    public void clearConfirmationContext(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        List<String> keys = List.of("isConfirmingAgenda", "isGroupFlow", "payloadclique", "requiresCpfFallback");

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String key : keys) {
            if (masterIdentity != null && !masterIdentity.isBlank()) {
                futures.add(CompletableFuture.runAsync(
                    () -> sendSingleDeleteUserContext(masterIdentity, key), applicationTaskExecutor));
            }
            if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
                futures.add(CompletableFuture.runAsync(
                    () -> sendSingleDeleteUserContext(tunnelIdentity, key), applicationTaskExecutor));
            }
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            log.info("[BLIP-CONTEXT] Variáveis de confirmação removidas com sucesso (escopo dual) para {}: {}", userIdentity, keys);
        } catch (Exception ex) {
            log.warn("[BLIP-CONTEXT] Erro ao remover variáveis de confirmação para {}: {}", userIdentity, ex.getMessage());
        }
    }

    /**
     * Envia um contexto JSON ao Blip via comando LIME.
     * O resource é passado como Object (Map, record, etc.) e serializado para string JSON,
     * sendo enviado como type: "text/plain".
     */
    public void setJsonContext(String userIdentity, String key, Object resourceObject) {
        if (userIdentity == null || userIdentity.isBlank() || resourceObject == null) return;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (masterIdentity != null && !masterIdentity.isBlank()) {
            sendSingleJsonContext(masterIdentity, key, resourceObject);
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            sendSingleJsonContext(tunnelIdentity, key, resourceObject);
        }
    }

    private void sendSingleJsonContext(String normalizedIdentity, String key, Object resourceObject) {
        try {
            String jsonString = objectMapper.writeValueAsString(resourceObject);
            Map<String, Object> command = blipContextPayloadFactory.buildSetJsonContextCommand(normalizedIdentity, key, jsonString);

            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("[LIME] Contexto JSON configurado (escopo dual). identity={}, key={}", normalizedIdentity, key);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | RestClientException ex) {
            log.warn("[LIME] Falha ao configurar contexto JSON. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    public void setMasterState(String userIdentity, String botIdentity, String operation) {
        if (userIdentity == null || userIdentity.isBlank()) return;

        String targetBot = botIdentity != null && !botIdentity.isBlank() ? botIdentity : blipAppointmentId;
        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (masterIdentity != null && !masterIdentity.isBlank()) {
            sendSingleMasterState(masterIdentity, targetBot, operation);
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            sendSingleMasterState(tunnelIdentity, targetBot, operation);
        }
    }

    private void sendSingleMasterState(String normalizedIdentity, String targetBot, String operation) {
        // SALVAGUARDA DESK: NUNCA enviar Master-State para desk@msging.net em identidades de túnel (@tunnel.msging.net).
        if ("desk@msging.net".equalsIgnoreCase(targetBot) && normalizedIdentity.contains("@tunnel.msging.net")) {
            log.info("[BLIP-CONTEXT-GUARD] Ignorado Master-State para desk@msging.net na identidade de túnel '{}' para evitar ticket duplicado no Desk.", normalizedIdentity);
            return;
        }

        if (isRedundantContextCall("mstate:" + normalizedIdentity + ":" + targetBot + ":" + operation)) {
            return;
        }

        Map<String, Object> command = blipContextPayloadFactory.buildMasterStateCommand(normalizedIdentity, targetBot, operation);

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Master-State atualizado. identity={}, operation={}, targetBot={}", normalizedIdentity, operation, targetBot);
        } catch (RestClientException ex) {
            log.error("Erro ao atualizar Master-State. identity={}, operation={}", normalizedIdentity, operation, ex);
        }
    }

    public void setBuilderMasterState(String userIdentity, String stateId) {
        if (userIdentity == null || userIdentity.isBlank()) return;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (masterIdentity != null && !masterIdentity.isBlank()) {
            sendSingleBuilderMasterState(masterIdentity, stateId);
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            sendSingleBuilderMasterState(tunnelIdentity, stateId);
        }
    }

    private void sendSingleBuilderMasterState(String normalizedIdentity, String stateId) {
        if (normalizedIdentity == null || normalizedIdentity.isBlank()) return;

        // SALVAGUARDA TOTAL FLUXOV1: NUNCA definir builder master-state para identidades de teste do fluxov1
        if (normalizedIdentity.toLowerCase().contains("fluxov1")) {
            log.warn("[BLIP-CONTEXT-GUARD] BLOQUEADO: Tentativa de definir builder master-state para identidade '{}'. Operacao cancelada.", normalizedIdentity);
            return;
        }

        if (isRedundantContextCall("bstate:" + normalizedIdentity + ":" + stateId)) {
            return;
        }

        Map<String, Object> command = blipContextPayloadFactory.buildBuilderMasterStateCommand(normalizedIdentity, stateId);

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("[LIME] Builder Master-State atualizado para o bloco stateId={}, user={}", stateId, normalizedIdentity);
        } catch (RestClientException ex) {
            log.error("Erro ao atualizar Builder Master-State. stateId={}, user={}", stateId, normalizedIdentity, ex);
        }
    }

    public void setUserState(String userIdentity, String stateName) {
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);
        Map<String, Object> command = blipContextPayloadFactory.buildUserStateCommand(normalizedIdentity, stateName);

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("User State atualizado. stateName={}", stateName);
        } catch (RestClientException ex) {
            log.error("Erro ao atualizar User State. stateName={}", stateName, ex);
        }
    }

    public void processAppointmentPush(String userPhone, String action, AppointmentPayload payload) {
        try {
            String userPhoneClean = userPhone != null ? userPhone.trim() : "";
            String masterIdentity = null;
            String tunnelIdentity = null;

            if (userPhoneClean.contains("@tunnel.msging.net")) {
                tunnelIdentity = userPhoneClean;
                // Reconcilia para obter o telefone real do paciente
                String reconciledPhone = blipIdentityReconciler.resolveAndReconcileIdentity(userPhoneClean, null);
                if (reconciledPhone != null && !reconciledPhone.isBlank()) {
                    masterIdentity = reconciledPhone.contains("@") ? reconciledPhone : reconciledPhone + "@wa.gw.msging.net";
                }
            } else {
                masterIdentity = limeClient.normalizeUserIdentity(userPhoneClean);
            }

            String resolvedQueue = resolveQueueName(payload.getQueue());

            Map<String, String> extras = new HashMap<>();
            extras.put("Medico", payload.getDoctorName());
            extras.put("fila", resolvedQueue);
            extras.put("deskFila", resolvedQueue);
            extras.put("nascimento", payload.getPatientBirthdate());
            extras.put("data_nascimento", payload.getPatientBirthdate());

            String cleanPName = blipIdentityReconciler.sanitizePatientName(payload.getPatientName());
            if (cleanPName != null) {
                extras.put("paciente", cleanPName);
                extras.put("Nome", cleanPName);
            }

            String blipBirthDate = blipContextPayloadFactory.convertBirthdateToBlipFormat(payload.getPatientBirthdate());

            // PASSO 1a: Atualiza os dados do Contato no Roteador (usando a identidade real master)
            if (masterIdentity != null && !masterIdentity.isBlank()) {
                BlipContactUpdateCommand masterCommand = blipContextPayloadFactory.buildContactUpdateCommand(
                        masterIdentity, cleanPName, payload.getPatientCPF(), blipBirthDate, extras);

                log.info("[LIME PUSH] Atualizando contato no ROTEADOR para identity={}: Medico={}, fila={}, birthDate={}", 
                        masterIdentity, payload.getDoctorName(), resolvedQueue, blipBirthDate);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> masterMap = objectMapper.convertValue(masterCommand, Map.class);
                limeClient.executeCommand(masterMap, BlipLIMEClient.AuthorizationScope.ROUTER);
            }

            // PASSO 1b: Atualiza os dados do Contato no Subbot/Desk (usando a identidade de túnel)
            if (tunnelIdentity != null && !tunnelIdentity.isBlank()) {
                BlipContactUpdateCommand tunnelCommand = blipContextPayloadFactory.buildContactUpdateCommand(
                        tunnelIdentity, cleanPName, payload.getPatientCPF(), blipBirthDate, extras);

                log.info("[LIME PUSH] Atualizando contato no SUBBOT/DESK para identity={}: Medico={}, fila={}, birthDate={}", 
                        tunnelIdentity, payload.getDoctorName(), resolvedQueue, blipBirthDate);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> tunnelMap = objectMapper.convertValue(tunnelCommand, Map.class);
                limeClient.executeCommand(tunnelMap, BlipLIMEClient.AuthorizationScope.DESK);
            }

            log.info("[MENSAGERIA] Registro processado. Delegando roteamento ao payload nativo do Blip Builder para a identidade: {}", userPhoneClean);
        } catch (RuntimeException ex) {
            throw new RuntimeException("Falha ao executar orquestração de push no Blip", ex);
        }
    }

    public boolean hasActiveTicket(String userIdentity) {
        return blipDeskGuardService.hasActiveTicket(userIdentity);
    }

    public boolean hasActiveTicket(String userIdentity, LocalDateTime lastNotificationSentAt) {
        return blipDeskGuardService.hasActiveTicket(userIdentity, lastNotificationSentAt);
    }

    public boolean isInHumanAttendance(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return false;
        try {
            String masterState = getUserContext(userIdentity, "master-state");
            if (masterState != null && !masterState.isBlank()) {
                String deskBlockId = blipProperties != null && blipProperties.getBlocks() != null 
                        ? blipProperties.getBlocks().getDeskStateId() : null;
                if (masterState.toLowerCase().contains("desk") 
                        || (deskBlockId != null && !deskBlockId.isBlank() && masterState.equalsIgnoreCase(deskBlockId))) {
                    log.info("[ATTENDANCE-GUARD] Contato {} com master-state apontando para Desk ('{}').", userIdentity, masterState);
                    return true;
                }
            }
            return hasActiveTicket(userIdentity);
        } catch (Exception ex) {
            log.warn("[ATTENDANCE-GUARD] Falha ao verificar atendimento humano para {}: {}", userIdentity, ex.getMessage());
            return false;
        }
    }

    public String resolveQueueName(String queueNameOrId) {
        if (queueNameOrId == null) return "Recepção Geral";
        String resolvedQueueName = queueNameOrId;

        if (queueNameOrId.trim().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            String uuid = queueNameOrId.trim();
            log.info("[QUEUE-RESOLVER] Detectado UUID de fila '{}'. Buscando nome descritivo correspondente na API do Blip...", uuid);
            try {
                var queues = limeClient.listBlipQueues();
                String foundName = null;
                if (queues != null) {
                    for (var q : queues) {
                        if (uuid.equalsIgnoreCase(q.id())) {
                            foundName = q.name();
                            break;
                        }
                    }
                }
                if (foundName != null && !foundName.isBlank() && !foundName.trim().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                    log.info("[QUEUE-RESOLVER] Traduzido UUID {} para o nome de pauta '{}'", uuid, foundName);
                    resolvedQueueName = foundName;
                } else {
                    log.warn("[QUEUE-RESOLVER] UUID {} não foi localizado na listagem de filas oficiais do Blip. Aplicando fallback de segurança 'Recepção Geral'.", uuid);
                    resolvedQueueName = "Recepção Geral";
                }
            } catch (Exception ex) {
                log.error("[QUEUE-RESOLVER] Falha na comunicação com a API do Blip ao resolver o UUID {}. Aplicando fallback de segurança 'Recepção Geral'.", uuid, ex);
                resolvedQueueName = "Recepção Geral";
            }
        }

        String safeQueueName = cleanQueueName(resolvedQueueName);
        if (safeQueueName.isBlank() || safeQueueName.trim().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            safeQueueName = "Recepção Geral";
        }
        return safeQueueName;
    }

    public boolean setQueueRedirect(String userIdentity, String queueNameOrId) {
        if (userIdentity == null || userIdentity.isBlank()) return false;
        String safeQueueName = resolveQueueName(queueNameOrId);
        String rawQueueId = (queueNameOrId != null && queueNameOrId.trim().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))
                ? queueNameOrId.trim() : null;

        if (!safeQueueName.isBlank()
                && !"Recepção Geral".equalsIgnoreCase(safeQueueName)
                && !"Recepção Central / Suporte".equalsIgnoreCase(safeQueueName)
                && !"Recepção".equalsIgnoreCase(safeQueueName)
                && !safeQueueName.contains(" - ")) {
            log.warn("[QUEUE WARNING] Nome da fila pode estar incompleto para o Desk. fila='{}'", safeQueueName);
        }

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);
        String queueValueForRedirect = safeQueueName;

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, String> extras = new LinkedHashMap<>();
                extras.put("attendanceQueueToRedirect", queueValueForRedirect);
                extras.put("attendanceQueueNameToRedirect", safeQueueName);
                extras.put("fila", safeQueueName);
                extras.put("deskFila", safeQueueName);
                if (rawQueueId != null && !rawQueueId.isBlank()) {
                    extras.put("blipQueueId", rawQueueId.trim());
                }
                updateContactExtras(userIdentity, extras);

                Map<String, String> contextFields = Map.of(
                    "attendanceQueueToRedirect", queueValueForRedirect,
                    "attendanceQueueNameToRedirect", safeQueueName,
                    "fila", safeQueueName,
                    "deskFila", safeQueueName
                );
                if (masterIdentity != null && !masterIdentity.isBlank()) {
                    setUserContextFieldsInParallel(masterIdentity, contextFields);
                }
                if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
                    setUserContextFieldsInParallel(tunnelIdentity, contextFields);
                }
                log.info("[BLIP-CONTEXT] Fila de redirecionamento e extras configurados no contexto (escopo dual). master={}, tunnel={}, fila='{}'",
                        masterIdentity, tunnelIdentity, safeQueueName);
            } catch (Exception ex) {
                log.warn("[BLIP-CONTEXT] Falha assíncrona ao configurar redirecionamento de fila para {}: {}", userIdentity, ex.getMessage());
            }
        }, applicationTaskExecutor);

        return true;
    }

    public boolean clearQueueRedirect(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return false;
        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        CompletableFuture.runAsync(() -> {
            if (masterIdentity != null && !masterIdentity.isBlank()) {
                deleteUserContext(masterIdentity, "attendanceQueueToRedirect");
            }
            if (tunnelIdentity != null && !tunnelIdentity.isBlank()) {
                deleteUserContext(tunnelIdentity, "attendanceQueueToRedirect");
            }
            log.info("[BLIP-CONTEXT] Variável attendanceQueueToRedirect removida do contexto para master={} e tunnel={}", masterIdentity, tunnelIdentity);
        }, applicationTaskExecutor);

        return true;
    }

    public void changeMasterState(String userIdentity, String stateId) {
        if (userIdentity == null || userIdentity.isBlank() || stateId == null || stateId.isBlank()) return;
        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        setMasterState(masterIdentity, "desk@msging.net", stateId);
        setBuilderMasterState(masterIdentity, stateId);
        if (tunnelIdentity != null && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            setBuilderMasterState(tunnelIdentity, stateId);
        }
        log.info("[BLIP-CONTEXT] Master State alterado para stateId={} no paciente master={} e tunnel={}", stateId, masterIdentity, tunnelIdentity);
    }

    public String cleanQueueName(String queueName) {
        return blipContextPayloadFactory.cleanQueueName(queueName);
    }

    public void setVariable(String userIdentity, String key, String value) {
        setUserContext(userIdentity, key, value);
    }

    public void setContactExtra(String userIdentity, String key, String value) {
        if (userIdentity == null || userIdentity.isBlank()) return;
        try {
            updateContactExtras(userIdentity, Map.of(key, value));
        } catch (Exception ex) {
            log.warn("Falha ao definir extras do contato no Blip. identity={}, key={}, value={}", userIdentity, key, value, ex);
        }
    }

    public void updateContactExtras(String userIdentity, String queueName, String blipQueueId) {
        if (userIdentity == null || userIdentity.isBlank()) return;

        Map<String, String> extras = new HashMap<>();
        if (queueName != null && !queueName.isBlank()) {
            String clean = cleanQueueName(queueName);
            extras.put("fila", clean);
            extras.put("deskFila", clean);
        }
        if (blipQueueId != null && !blipQueueId.isBlank()) {
            extras.put("blipQueueId", blipQueueId.trim());
        }

        if (extras.isEmpty()) return;

        updateContactExtras(userIdentity, extras);
    }

    public void updateContactExtras(String userIdentity, Map<String, String> extras) {
        if (userIdentity == null || userIdentity.isBlank() || extras == null || extras.isEmpty()) return;

        try {
            String masterIdentity = resolveMasterIdentity(userIdentity);
            String tunnelIdentity = resolveTunnelIdentity(userIdentity);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            if (masterIdentity != null && !masterIdentity.isBlank()) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        limeClient.mergeContactExtras(masterIdentity, extras, BlipLIMEClient.AuthorizationScope.ROUTER);
                    } catch (Exception ex) {
                        log.warn("[BLIP-CONTEXT] Falha ao atualizar extras no Roteador para {}: {}", masterIdentity, ex.getMessage());
                    }
                }, applicationTaskExecutor));
            }
            if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        limeClient.mergeContactExtras(tunnelIdentity, extras, BlipLIMEClient.AuthorizationScope.DESK);
                    } catch (Exception ex) {
                        log.warn("[BLIP-CONTEXT] Falha ao atualizar extras no Desk/Subbot para {}: {}", tunnelIdentity, ex.getMessage());
                    }
                }, applicationTaskExecutor));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            log.info("[BLIP-CONTEXT] Extras do contato atualizados em escopo duplo para {}: {}", userIdentity, extras.keySet());
        } catch (Exception ex) {
            log.warn("[BLIP-CONTEXT] Falha ao atualizar extras do contato em escopo duplo para {}: {}", userIdentity, ex.getMessage());
        }
    }
}
