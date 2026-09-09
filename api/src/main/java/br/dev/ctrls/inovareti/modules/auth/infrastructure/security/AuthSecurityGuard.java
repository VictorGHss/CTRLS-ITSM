package br.dev.ctrls.inovareti.modules.auth.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Componente de proteção e blindagem contra ataques de força bruta e credential stuffing.
 * Protege os endpoints de login administrativo (/auth/login) e validação de segundo fator (/auth/2fa/verify).
 *
 * Opera prioritariamente via Redis (distribuído) com fallback automático e transparente em memória (Caffeine).
 */
@Slf4j
@Component
public class AuthSecurityGuard {

    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final int MAX_FAILED_TOTP_ATTEMPTS = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(2);

    private static final String REDIS_PREFIX_LOGIN_ATTEMPTS_EMAIL = "auth:attempts:login:email:";
    private static final String REDIS_PREFIX_LOGIN_ATTEMPTS_IP = "auth:attempts:login:ip:";
    private static final String REDIS_PREFIX_LOGIN_LOCK_EMAIL = "auth:locked:login:email:";
    private static final String REDIS_PREFIX_LOGIN_LOCK_IP = "auth:locked:login:ip:";

    private static final String REDIS_PREFIX_TOTP_ATTEMPTS = "auth:attempts:totp:";
    private static final String REDIS_PREFIX_TOTP_LOCK = "auth:locked:totp:";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ApplicationEventPublisher eventPublisher;

    // Fallbacks locais em memória (Caffeine) caso o Redis esteja indisponível
    private final Cache<String, AtomicInteger> localAttemptsCache;
    private final Cache<String, Boolean> localLockoutCache;

    public AuthSecurityGuard() {
        this(null, null);
    }

    public AuthSecurityGuard(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this(redisTemplateProvider, null);
    }

    @Autowired
    public AuthSecurityGuard(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ApplicationEventPublisher eventPublisher) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.eventPublisher = eventPublisher;

        this.localAttemptsCache = Caffeine.newBuilder()
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
     * Extrai o IP real do cliente considerando cabeçalhos de proxies reversos (Nginx/Cloudflare).
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
     * Verifica se o login está bloqueado para o e-mail ou para o IP informado.
     */
    public boolean isLoginBlocked(String email, String clientIp) {
        if (isKeyLocked(REDIS_PREFIX_LOGIN_LOCK_EMAIL, email)) {
            return true;
        }
        if (clientIp != null && !clientIp.isBlank() && !"unknown".equalsIgnoreCase(clientIp)) {
            return isKeyLocked(REDIS_PREFIX_LOGIN_LOCK_IP, clientIp);
        }
        return false;
    }

    /**
     * Registra uma falha de autenticação e, se atingir o limite, ativa o lockout.
     * Retorna o número máximo de tentativas falhas atingido (por e-mail ou por IP).
     */
    public int recordLoginFailure(String email, String clientIp) {
        int emailAttempts = incrementAttempts(REDIS_PREFIX_LOGIN_ATTEMPTS_EMAIL, REDIS_PREFIX_LOGIN_LOCK_EMAIL, email, MAX_FAILED_LOGIN_ATTEMPTS);
        int ipAttempts = 0;
        if (clientIp != null && !clientIp.isBlank() && !"unknown".equalsIgnoreCase(clientIp)) {
            ipAttempts = incrementAttempts(REDIS_PREFIX_LOGIN_ATTEMPTS_IP, REDIS_PREFIX_LOGIN_LOCK_IP, clientIp, MAX_FAILED_LOGIN_ATTEMPTS);
        }
        int maxAttempts = Math.max(emailAttempts, ipAttempts);
        if (maxAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            log.warn("[SEGURANÇA] Bloqueio de login ativado por {} minutos após {} tentativas falhas. Email: {}, IP: {}",
                    LOCKOUT_DURATION.toMinutes(), maxAttempts, email, clientIp);
            if (eventPublisher != null) {
                try {
                    eventPublisher.publishEvent(new br.dev.ctrls.inovareti.modules.auth.application.event.AuthLockoutEvent(
                            email != null ? email : "desconhecido",
                            clientIp != null ? clientIp : "desconhecido",
                            "Falha consecutiva de autenticação (senha incorreta)",
                            maxAttempts,
                            (int) LOCKOUT_DURATION.toMinutes()
                    ));
                } catch (Exception ex) {
                    log.warn("[AuthSecurityGuard] Falha ao publicar evento de lockout: {}", ex.getMessage());
                }
            }
        }
        return maxAttempts;
    }

    /**
     * Limpa o contador de falhas e qualquer bloqueio ativo após autenticação com sucesso.
     */
    public void recordLoginSuccess(String email, String clientIp) {
        clearKey(REDIS_PREFIX_LOGIN_ATTEMPTS_EMAIL, REDIS_PREFIX_LOGIN_LOCK_EMAIL, email);
        if (clientIp != null && !clientIp.isBlank() && !"unknown".equalsIgnoreCase(clientIp)) {
            clearKey(REDIS_PREFIX_LOGIN_ATTEMPTS_IP, REDIS_PREFIX_LOGIN_LOCK_IP, clientIp);
        }
    }

    /**
     * Verifica se o 2FA está bloqueado para o usuário especificado.
     */
    public boolean isTotpBlocked(UUID userId) {
        if (userId == null) {
            return false;
        }
        return isKeyLocked(REDIS_PREFIX_TOTP_LOCK, userId.toString());
    }

    /**
     * Registra uma tentativa incorreta de TOTP e aplica lockout caso atinja o limite.
     */
    public int recordTotpFailure(UUID userId) {
        if (userId == null) {
            return 0;
        }
        int attempts = incrementAttempts(REDIS_PREFIX_TOTP_ATTEMPTS, REDIS_PREFIX_TOTP_LOCK, userId.toString(), MAX_FAILED_TOTP_ATTEMPTS);
        if (attempts >= MAX_FAILED_TOTP_ATTEMPTS) {
            log.warn("[SEGURANÇA] Validação de 2FA bloqueada por {} minutos para o usuário {} após {} falhas.",
                    LOCKOUT_DURATION.toMinutes(), userId, attempts);
            if (eventPublisher != null) {
                try {
                    eventPublisher.publishEvent(new br.dev.ctrls.inovareti.modules.auth.application.event.AuthLockoutEvent(
                            userId.toString(),
                            "Sessão 2FA Autenticada",
                            "Falha consecutiva no código TOTP/2FA do Cofre",
                            attempts,
                            (int) LOCKOUT_DURATION.toMinutes()
                    ));
                } catch (Exception ex) {
                    log.warn("[AuthSecurityGuard] Falha ao publicar evento de lockout TOTP: {}", ex.getMessage());
                }
            }
        }
        return attempts;
    }

    /**
     * Limpa as tentativas de 2FA após validação bem-sucedida.
     */
    public void recordTotpSuccess(UUID userId) {
        if (userId != null) {
            clearKey(REDIS_PREFIX_TOTP_ATTEMPTS, REDIS_PREFIX_TOTP_LOCK, userId.toString());
        }
    }

    // Métodos auxiliares de contagem e persistência com fallback Redis <-> Caffeine

    private boolean isKeyLocked(String prefix, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        String cleanId = identifier.trim().toLowerCase();
        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                return Boolean.TRUE.equals(redis.hasKey(prefix + cleanId));
            } catch (Exception ex) {
                log.warn("[AuthSecurityGuard] Falha ao consultar bloqueio no Redis. Usando fallback local. Causa: {}", ex.getMessage());
            }
        }
        return Boolean.TRUE.equals(localLockoutCache.getIfPresent(prefix + cleanId));
    }

    private int incrementAttempts(String attemptsPrefix, String lockPrefix, String identifier, int maxAllowed) {
        if (identifier == null || identifier.isBlank()) {
            return 0;
        }
        String cleanId = identifier.trim().toLowerCase();
        int attempts = 0;
        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                String attemptsKey = attemptsPrefix + cleanId;
                Long val = redis.opsForValue().increment(attemptsKey);
                if (val != null && val == 1L) {
                    redis.expire(attemptsKey, LOCKOUT_DURATION);
                }
                attempts = val != null ? val.intValue() : 1;
                if (attempts >= maxAllowed) {
                    redis.opsForValue().set(lockPrefix + cleanId, "true", LOCKOUT_DURATION);
                }
                return attempts;
            } catch (Exception ex) {
                log.warn("[AuthSecurityGuard] Falha ao registrar tentativa no Redis. Usando fallback local. Causa: {}", ex.getMessage());
            }
        }

        AtomicInteger local = localAttemptsCache.get(attemptsPrefix + cleanId, k -> new AtomicInteger(0));
        attempts = local.incrementAndGet();
        if (attempts >= maxAllowed) {
            localLockoutCache.put(lockPrefix + cleanId, Boolean.TRUE);
        }
        return attempts;
    }

    private void clearKey(String attemptsPrefix, String lockPrefix, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        String cleanId = identifier.trim().toLowerCase();
        StringRedisTemplate redis = getRedis();
        if (redis != null) {
            try {
                redis.delete(attemptsPrefix + cleanId);
                redis.delete(lockPrefix + cleanId);
            } catch (Exception ex) {
                log.warn("[AuthSecurityGuard] Falha ao resetar tentativas no Redis: {}", ex.getMessage());
            }
        }
        localAttemptsCache.invalidate(attemptsPrefix + cleanId);
        localLockoutCache.invalidate(lockPrefix + cleanId);
    }
}
