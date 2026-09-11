package br.dev.ctrls.itsm.modules.auth.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthSecurityGuardTest {

    private AuthSecurityGuard guard;

    @BeforeEach
    void setUp() {
        // Inicializa sem Redis para testar o fallback em memória via Caffeine
        this.guard = new AuthSecurityGuard();
    }

    @Test
    void extractClientIpExtractsFromXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("10.0.0.1");

        String ip = guard.extractClientIp(request);
        assertEquals("203.0.113.195", ip);
    }

    @Test
    void extractClientIpFallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");

        String ip = guard.extractClientIp(request);
        assertEquals("192.168.1.50", ip);
    }

    @Test
    void loginLockoutTriggersAfterMaxAttempts() {
        String email = "admin@inovareti.com";
        String ip = "192.168.1.100";

        assertFalse(guard.isLoginBlocked(email, ip));

        for (int i = 1; i <= 4; i++) {
            int attempts = guard.recordLoginFailure(email, ip);
            assertEquals(i, attempts);
            assertFalse(guard.isLoginBlocked(email, ip));
        }

        // 5ª tentativa ativa o bloqueio
        int attempts = guard.recordLoginFailure(email, ip);
        assertEquals(5, attempts);
        assertTrue(guard.isLoginBlocked(email, ip));
        assertTrue(guard.isLoginBlocked(email, "outra-ip"));
        assertTrue(guard.isLoginBlocked("outro-email@teste.com", ip));
    }

    @Test
    void loginSuccessResetsLockoutAndAttempts() {
        String email = "tecnico@inovareti.com";
        String ip = "10.0.0.25";

        for (int i = 1; i <= 5; i++) {
            guard.recordLoginFailure(email, ip);
        }
        assertTrue(guard.isLoginBlocked(email, ip));

        // Sucesso limpa as travas
        guard.recordLoginSuccess(email, ip);
        assertFalse(guard.isLoginBlocked(email, ip));
    }

    @Test
    void totpLockoutTriggersAfterMaxAttempts() {
        UUID userId = UUID.randomUUID();

        assertFalse(guard.isTotpBlocked(userId));

        for (int i = 1; i <= 4; i++) {
            int attempts = guard.recordTotpFailure(userId);
            assertEquals(i, attempts);
            assertFalse(guard.isTotpBlocked(userId));
        }

        int attempts = guard.recordTotpFailure(userId);
        assertEquals(5, attempts);
        assertTrue(guard.isTotpBlocked(userId));

        // Reset
        guard.recordTotpSuccess(userId);
        assertFalse(guard.isTotpBlocked(userId));
    }
}
