package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente de segurança (Attendance Guard) responsável por detectar se um contato
 * possui tickets de atendimento humano abertos ou em espera no Blip Desk (live chat).
 * Utilizado para pausar/abortar lembretes e nudges automáticos durante o atendimento com recepcionistas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class BlipDeskGuardService {

    private final BlipLIMEClient limeClient;

    /**
     * Verifica se o contato possui um ticket de atendimento humano ativo ou aberto no Desk (live chat) do Blip.
     */
    public boolean hasActiveTicket(String userIdentity) {
        return hasActiveTicket(userIdentity, null);
    }

    /**
     * Verifica se o contato possui um ticket de atendimento humano ativo/aberto recente no Desk (live chat) do Blip.
     * Considera identidades diretas e variações formatadas.
     */
    public boolean hasActiveTicket(String userIdentity, LocalDateTime lastNotificationSentAt) {
        if (userIdentity == null || userIdentity.isBlank()) return false;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (checkActiveTicketForIdentity(masterIdentity)) {
            return true;
        }
        if (tunnelIdentity != null && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            if (checkActiveTicketForIdentity(tunnelIdentity)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkActiveTicketForIdentity(String identity) {
        if (identity == null || identity.isBlank()) return false;
        String normalizedIdentity = limeClient.normalizeUserIdentity(identity);

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@desk.msging.net",
            "method", "get",
            "uri", "/tickets?$filter=customerIdentity eq '" + normalizedIdentity + "' and (status eq 'Open' or status eq 'Waiting')"
        );

        try {
            Map<String, Object> response = limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            if (response == null) return false;
            Object resourceNode = response.get("resource");
            if (resourceNode instanceof Map<?, ?> resourceMap) {
                Object itemsNode = resourceMap.get("items");
                if (itemsNode == null) itemsNode = resourceMap.get("tickets");
                if (itemsNode == null) itemsNode = resourceMap.get("data");

                if (itemsNode instanceof Collection<?> itemsList) {
                    boolean hasActive = false;

                    for (Object itemObj : itemsList) {
                        if (itemObj instanceof Map<?, ?> itemMap) {
                            Object customerVal = itemMap.get("customerIdentity");
                            if (customerVal == null) customerVal = itemMap.get("customerInput");
                            if (customerVal == null) customerVal = itemMap.get("identity");

                            if (customerVal != null && !customerVal.toString().isBlank()) {
                                String ticketCustomer = limeClient.normalizeUserIdentity(customerVal.toString().trim());
                                String reqUserDigits = normalizedIdentity.contains("@") 
                                    ? normalizedIdentity.substring(0, normalizedIdentity.indexOf('@')).replaceAll("\\D", "") 
                                    : normalizedIdentity.replaceAll("\\D", "");
                                String ticketUserDigits = ticketCustomer.contains("@") 
                                    ? ticketCustomer.substring(0, ticketCustomer.indexOf('@')).replaceAll("\\D", "") 
                                    : ticketCustomer.replaceAll("\\D", "");

                                if (!reqUserDigits.isEmpty() && !ticketUserDigits.isEmpty() && !reqUserDigits.equals(ticketUserDigits)) {
                                    // O ticket pertence a outro paciente retornado pela API do Desk. Ignora.
                                    continue;
                                }
                            }

                            Object statusVal = itemMap.get("status");
                            if (statusVal != null) {
                                String status = statusVal.toString().trim();
                                if ("Open".equalsIgnoreCase(status) || "Waiting".equalsIgnoreCase(status)) {
                                    hasActive = true;
                                    log.info("[ATTENDANCE-GUARD] Contato {} possui ticket ativo no Desk (status='{}'). Pausando nudges automáticos.", normalizedIdentity, status);
                                    break;
                                }
                            }
                        }
                    }
                    if (hasActive) {
                        log.info("[ATTENDANCE-GUARD] Contato {} possui tickets de live chat ativos e recentes no Desk.", normalizedIdentity);
                    }
                    return hasActive;
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("[ATTENDANCE-GUARD] Falha ao verificar ticket ativo no Desk para {}: {}", normalizedIdentity, ex.getMessage());
            return false; // Fail-open para não travar os nudges normais em caso de falha de rede/autorização
        }
    }

    private String resolveMasterIdentity(String identity) {
        if (identity == null || identity.isBlank()) return "";
        String clean = identity.trim();
        if (!clean.contains("@")) {
            return clean.replaceAll("\\D", "") + "@wa.gw.msging.net";
        }
        return clean;
    }

    private String resolveTunnelIdentity(String identity) {
        if (identity == null || identity.isBlank()) return "";
        String clean = identity.trim();
        if (clean.contains("@tunnel.msging.net") || clean.contains("@0mn.io")) {
            return clean;
        }
        return resolveMasterIdentity(identity);
    }
}
