package br.dev.ctrls.itsm.infrastructure.shared.web.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;

import jakarta.servlet.http.HttpServletRequest;

public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    public void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/auth/login");
    }

    @Test
    public void testBadCredentialsReturns401Unauthorized() {
        BadCredentialsException ex = new BadCredentialsException("Usuário inexistente ou senha inválida");
        ProblemDetail problem = handler.handleAuthenticationException(ex, request);

        assertNotNull(problem);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), problem.getStatus());
        assertEquals("Falha na autenticação", problem.getTitle());
        assertEquals("Usuário inexistente ou senha inválida", problem.getDetail());
    }
}
