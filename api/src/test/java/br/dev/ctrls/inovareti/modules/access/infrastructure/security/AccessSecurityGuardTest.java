package br.dev.ctrls.inovareti.modules.access.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AccessSecurityGuardTest {

    private AccessSecurityGuard guard;

    @BeforeEach
    void setUp() {
        // Inicializa com fallback local (sem Redis)
        guard = new AccessSecurityGuard();
    }

    @Test
    @DisplayName("Deve extrair o IP correto respeitando cabeçalhos X-Forwarded-For e X-Real-IP")
    void shouldExtractClientIpCorrectly() {
        HttpServletRequest reqWithForwarded = Mockito.mock(HttpServletRequest.class);
        when(reqWithForwarded.getHeader("X-Forwarded-For")).thenReturn("200.189.10.5, 10.0.0.1");
        assertEquals("200.189.10.5", guard.extractClientIp(reqWithForwarded));

        HttpServletRequest reqWithRealIp = Mockito.mock(HttpServletRequest.class);
        when(reqWithRealIp.getHeader("X-Real-IP")).thenReturn("177.100.20.30");
        assertEquals("177.100.20.30", guard.extractClientIp(reqWithRealIp));

        HttpServletRequest reqDirect = Mockito.mock(HttpServletRequest.class);
        when(reqDirect.getRemoteAddr()).thenReturn("192.168.1.50");
        assertEquals("192.168.1.50", guard.extractClientIp(reqDirect));
    }

    @Test
    @DisplayName("Deve permitir até 10 consultas de CPF por minuto e bloquear a 11ª para o mesmo IP")
    void shouldRateLimitCpfLookupsPerIp() {
        String testIp = "189.20.30.40";

        // Primeiras 10 tentativas devem ser permitidas
        for (int i = 1; i <= 10; i++) {
            assertTrue(guard.tryAcquireCpfLookup(testIp), "Tentativa " + i + " deveria ser permitida");
        }

        // A 11ª tentativa no mesmo minuto deve ser rejeitada
        assertFalse(guard.tryAcquireCpfLookup(testIp), "Tentativa 11 deveria ser bloqueada pelo rate limiter");

        // Outro IP diferente não deve ser impactado
        String anotherIp = "189.20.30.41";
        assertTrue(guard.tryAcquireCpfLookup(anotherIp), "Novo IP deveria ter cota própria");
    }

    @Test
    @DisplayName("Deve bloquear o agendamento após 5 tentativas falhas consecutivas de telefone")
    void shouldLockoutAppointmentAfterFiveFailedAttempts() {
        String appointmentId = "FEG-998877";

        assertFalse(guard.isAppointmentLocked(appointmentId), "Inicialmente não deve estar bloqueado");

        // 4 tentativas falhas não devem bloquear ainda
        for (int i = 1; i <= 4; i++) {
            int attempts = guard.recordFailedAttempt(appointmentId);
            assertEquals(i, attempts);
            assertFalse(guard.isAppointmentLocked(appointmentId), "Com " + i + " tentativas ainda não deve bloquear");
        }

        // A 5ª tentativa falha deve ativar o bloqueio
        int fifthAttempt = guard.recordFailedAttempt(appointmentId);
        assertEquals(5, fifthAttempt);
        assertTrue(guard.isAppointmentLocked(appointmentId), "Na 5ª tentativa deve estar bloqueado");

        // Tentativas subsequentes continuam indicando bloqueio
        assertTrue(guard.isAppointmentLocked(appointmentId));

        // Ao resetar com sucesso, o bloqueio deve ser limpo
        guard.resetFailedAttempts(appointmentId);
        assertFalse(guard.isAppointmentLocked(appointmentId), "Após reset, não deve estar bloqueado");
    }
}
