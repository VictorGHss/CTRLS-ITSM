package br.dev.ctrls.itsm.modules.appointment.application.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço que gerencia o controle de idempotência para recepção de webhooks do Blip,
 * com suporte primário a Redis distribuído e fallback em memória concorrente resiliente.
 */
@Slf4j
@Service
@Observed
@RequiredArgsConstructor
public class BlipWebhookIdempotencyService {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    // Cache local em memória concorrente com expiração para resiliência caso o Redis esteja indisponível
    private final Map<String, Long> processedEventsCache = new ConcurrentHashMap<>();

    // Cache de rate limit de 30 segundos para orientação de estado
    private final Map<String, Long> lastOrientationSentCache = new ConcurrentHashMap<>();

    /**
     * Normaliza identificadores de mensagem do Blip removendo prefixos de encaminhamento (ex: fwd:, fwd:fwd:).
     */
    public static String normalizeMessageId(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return null;
        }
        String normalized = messageId.trim();
        while (normalized.toLowerCase().startsWith("fwd:")) {
            normalized = normalized.substring(4).trim();
        }
        return normalized;
    }

    /**
     * Verifica e registra o processamento do evento para garantir idempotência de forma resiliente.
     *
     * @param messageId ID único do evento/mensagem
     * @return true se for o primeiro processamento (deve processar), false se for duplicado (deve ignorar)
     */
    public boolean isFirstTimeProcessing(String messageId) {
        String normalizedId = normalizeMessageId(messageId);
        if (!StringUtils.hasText(normalizedId)) {
            return true; // Fail-open para mensagens sem identificação
        }

        String cacheKey = "webhook:idempotency:blip:" + normalizedId;
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();

        if (redis != null) {
            try {
                Boolean success = redis.opsForValue().setIfAbsent(cacheKey, "1", Duration.ofHours(24));
                if (success != null) {
                    return success;
                }
            } catch (Exception ex) {
                log.warn("Redis indisponível para registro de idempotência. Ativando fallback em memória local: {}", ex.getMessage());
            }
        }

        // Fallback em memória
        long now = System.currentTimeMillis();
        long expirationTime = now + 3600_000L;

        // Evita vazamento de memória do cache local se o mapa crescer além do limite de 10.000 entradas
        if (processedEventsCache.size() > 10000) {
            CompletableFuture.runAsync(() -> {
                long currentTime = System.currentTimeMillis();
                processedEventsCache.entrySet().removeIf(entry -> entry.getValue() < currentTime);
            });
        }

        AtomicBoolean isFirst = new AtomicBoolean(false);
        processedEventsCache.compute(normalizedId, (key, currentVal) -> {
            if (currentVal == null || currentVal <= now) {
                isFirst.set(true);
                return expirationTime;
            }
            return currentVal;
        });

        return isFirst.get();
    }

    /**
     * Rate-limiter para mensagens de orientação em caso de texto livre durante confirmação de agendamento (30s).
     */
    public boolean shouldSendOrientation(String from) {
        long now = System.currentTimeMillis();
        AtomicBoolean shouldSend = new AtomicBoolean(false);
        lastOrientationSentCache.compute(from, (key, lastSent) -> {
            if (lastSent == null || (now - lastSent) >= 30000L) {
                shouldSend.set(true);
                return now;
            }
            return lastSent;
        });
        return shouldSend.get();
    }
}
