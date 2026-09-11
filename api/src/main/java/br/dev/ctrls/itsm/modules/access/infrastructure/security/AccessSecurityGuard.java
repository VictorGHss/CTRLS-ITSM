package br.dev.ctrls.itsm.modules.access.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Componente de proteção e blindagem de segurança para os endpoints de acesso público do paciente.
 * Fornece:
 * 1. Rate limiting por IP em consultas de CPF para prevenir scraping e enumeração em massa.
 * 2. Proteção contra ataques de força bruta no desafio dos 4 dígitos do telefone por agendamento.
 *
 * Opera prioritariamente via Redis (distribuído) com fallback automático e transparente em memória (Caffeine).
 */
@Slf4j
@Component
public class AccessSecurityGuard {

    public static final int MAX_LOOKUP_REQUESTS_PER_MINUTE = 10;
    public static final int MAX_FAILED_CHALLENGE_ATTEMPTS = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    public static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private static final String REDIS_KEY_PREFIX_RATELIMIT = "access:ratelimit:cpf:";
    private static final String REDIS_KEY_PREFIX_ATTEMPTS = "access:challenge:attempts:";
    private static final String REDIS_KEY_PREFIX_LOCKED = "access:challenge:locked:";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    // Fallbacks locais em memória (Caffeine) caso o Redis esteja indisponível
    private final Cache<String, AtomicInteger> localIpRateLimitCache;
    private final Cache<String, AtomicInteger> localFailedAttemptsCache;
    private final Cache<String, Boolean> localLockoutCache;

    public AccessSecurityGuard() {
        this(null);
    }

    public AccessSecurityGuard(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;

        this.localIpRateLimitCache = Caffeine.newBuilder()
                .expireAfterWrite(RATE_LIMIT_WINDOW)
                .maximumSize(50_000)
                .build();

        this.localFailedAttemptsCache = Caffeine.newBuilder()
                .expireAfterWrite(LOCKOUT_DURATION)
                .maximumSize(50_000)
                .build();

        this.localLockoutCache = Caffeine.newBuilder()
                .expireAfterWrite(LOCKOUT_DURATION)
                .maximumSize(50_000)
                .build();
    }

    private StringRedisTemplate getRedis() {
        return redisTemplateProvider != null ? redisTemplateProvider.getIfAvailable() : null;
    }

    /**
     * Extrai o IP real do cliente considerando possíveis proxies reversos (Nginx/Cloudflare) via X-Forwarded-For.
     */
    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    /**
     * Verifica e consome uma tentativa de requisição para busca de CPF.
     * Limite: 10 requisições por minuto por IP.
     *
     * @param clientIp IP do cliente solicitante
     * @return true se a requisição é permitida, false se excedeu a cota (429)
     */
    public boolean tryAcquireCpfLookup(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return true;
        }

        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                String key = REDIS_KEY_PREFIX_RATELIMIT + clientIp;
                Long count = redis.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redis.expire(key, RATE_LIMIT_WINDOW);
                }
                boolean allowed = count != null && count <= MAX_LOOKUP_REQUESTS_PER_MINUTE;
                if (!allowed) {
                    log.warn("[AccessSecurityGuard] Rate limit de CPF excedido para IP {}: {} reqs/min", clientIp, count);
                }
                return allowed;
            } catch (Exception ex) {
                log.warn("[AccessSecurityGuard] Falha ao comunicar com Redis para rate-limit. Usando fallback local. Causa: {}", ex.getMessage());
            }
        }

        // Fallback local via Caffeine
        AtomicInteger counter = localIpRateLimitCache.get(clientIp, k -> new AtomicInteger(0));
        int currentCount = counter.incrementAndGet();
        boolean allowed = currentCount <= MAX_LOOKUP_REQUESTS_PER_MINUTE;
        if (!allowed) {
            log.warn("[AccessSecurityGuard] Rate limit de CPF excedido (local) para IP {}: {} reqs/min", clientIp, currentCount);
        }
        return allowed;
    }

    /**
     * Verifica se determinado agendamento está atualmente bloqueado devido a tentativas consecutivas falhas.
     */
    public boolean isAppointmentLocked(String appointmentId) {
        if (appointmentId == null || appointmentId.isBlank()) {
            return false;
        }

        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                String lockKey = REDIS_KEY_PREFIX_LOCKED + appointmentId;
                Boolean isLocked = redis.hasKey(lockKey);
                return Boolean.TRUE.equals(isLocked);
            } catch (Exception ex) {
                log.warn("[AccessSecurityGuard] Falha ao consultar bloqueio no Redis. Usando fallback local. Causa: {}", ex.getMessage());
            }
        }

        return Boolean.TRUE.equals(localLockoutCache.getIfPresent(appointmentId));
    }

    /**
     * Registra uma tentativa incorreta no desafio dos 4 dígitos para o agendamento.
     * Caso atinja o limite máximo de tentativas (5), ativa o bloqueio temporário por 15 minutos.
     *
     * @param appointmentId ID do agendamento
     * @return número total de tentativas incorretas acumuladas
     */
    public int recordFailedAttempt(String appointmentId) {
        if (appointmentId == null || appointmentId.isBlank()) {
            return 1;
        }

        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                String attemptsKey = REDIS_KEY_PREFIX_ATTEMPTS + appointmentId;
                Long attempts = redis.opsForValue().increment(attemptsKey);
                if (attempts != null && attempts == 1L) {
                    redis.expire(attemptsKey, LOCKOUT_DURATION);
                }
                int count = attempts != null ? attempts.intValue() : 1;
                if (count >= MAX_FAILED_CHALLENGE_ATTEMPTS) {
                    String lockKey = REDIS_KEY_PREFIX_LOCKED + appointmentId;
                    redis.opsForValue().set(lockKey, "1", LOCKOUT_DURATION);
                    log.warn("[AccessSecurityGuard] Agendamento {} bloqueado por {} minutos após {} tentativas falhas.", 
                            appointmentId, LOCKOUT_DURATION.toMinutes(), count);
                }
                return count;
            } catch (Exception ex) {
                log.warn("[AccessSecurityGuard] Falha ao registrar tentativa no Redis. Usando fallback local. Causa: {}", ex.getMessage());
            }
        }

        AtomicInteger counter = localFailedAttemptsCache.get(appointmentId, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        if (count >= MAX_FAILED_CHALLENGE_ATTEMPTS) {
            localLockoutCache.put(appointmentId, Boolean.TRUE);
            log.warn("[AccessSecurityGuard] Agendamento {} bloqueado localmente por {} minutos após {} tentativas falhas.", 
                    appointmentId, LOCKOUT_DURATION.toMinutes(), count);
        }
        return count;
    }

    /**
     * Reseta as tentativas falhas e qualquer bloqueio ativo ao obter sucesso na validação.
     */
    public void resetFailedAttempts(String appointmentId) {
        if (appointmentId == null || appointmentId.isBlank()) {
            return;
        }

        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                redis.delete(REDIS_KEY_PREFIX_ATTEMPTS + appointmentId);
                redis.delete(REDIS_KEY_PREFIX_LOCKED + appointmentId);
            } catch (Exception ex) {
                log.warn("[AccessSecurityGuard] Falha ao resetar tentativas no Redis: {}", ex.getMessage());
            }
        }

        localFailedAttemptsCache.invalidate(appointmentId);
        localLockoutCache.invalidate(appointmentId);
    }
}
