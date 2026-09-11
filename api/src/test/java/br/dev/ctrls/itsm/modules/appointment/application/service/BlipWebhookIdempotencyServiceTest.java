package br.dev.ctrls.itsm.modules.appointment.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class BlipWebhookIdempotencyServiceTest {

    private BlipWebhookIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        idempotencyService = new BlipWebhookIdempotencyService(redisProvider);
    }

    @Test
    @DisplayName("Deve normalizar IDs removendo prefixos fwd: simples e compostos")
    void testNormalizeMessageId() {
        assertEquals("wamid.HBg123456", BlipWebhookIdempotencyService.normalizeMessageId("wamid.HBg123456"));
        assertEquals("wamid.HBg123456", BlipWebhookIdempotencyService.normalizeMessageId("fwd:wamid.HBg123456"));
        assertEquals("c552bae3-3d40-4022-a31b-3517ae93facf", BlipWebhookIdempotencyService.normalizeMessageId("fwd:fwd:c552bae3-3d40-4022-a31b-3517ae93facf"));
        assertEquals("c552bae3-3d40-4022-a31b-3517ae93facf", BlipWebhookIdempotencyService.normalizeMessageId("FWD: fwd:  c552bae3-3d40-4022-a31b-3517ae93facf "));
    }

    @Test
    @DisplayName("Deve bloquear evento duplicado quando receber mensagem original e cópia encaminhada com fwd:")
    void testDeduplicateForwardedMessages() {
        String originalId = "wamid.HBgMNTU0MjkxMTQ2MTk2FQIAEhggQUM3QUMxOUZGMjgwNzUxNkM0NUMwMTE5MjRFQ0U0RTQA";
        String forwardedId = "fwd:wamid.HBgMNTU0MjkxMTQ2MTk2FQIAEhggQUM3QUMxOUZGMjgwNzUxNkM0NUMwMTE5MjRFQ0U0RTQA";

        // Primeiro processamento (original) deve passar
        assertTrue(idempotencyService.isFirstTimeProcessing(originalId));

        // Segundo processamento (fwd:) deve ser bloqueado por idempotência
        assertFalse(idempotencyService.isFirstTimeProcessing(forwardedId));
    }

    @Test
    @DisplayName("Deve bloquear evento duplicado quando receber fwd: e fwd:fwd:")
    void testDeduplicateMultiForwardedMessages() {
        String fwd1 = "fwd:c552bae3-3d40-4022-a31b-3517ae93facf";
        String fwd2 = "fwd:fwd:c552bae3-3d40-4022-a31b-3517ae93facf";

        // Primeiro processamento passa
        assertTrue(idempotencyService.isFirstTimeProcessing(fwd1));

        // Segundo processamento é bloqueado
        assertFalse(idempotencyService.isFirstTimeProcessing(fwd2));
    }
}
