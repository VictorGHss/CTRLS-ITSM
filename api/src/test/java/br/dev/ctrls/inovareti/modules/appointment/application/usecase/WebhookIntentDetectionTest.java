package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase.WebhookIntent;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest.BlipWebhookController;

/**
 * Suíte de testes unitários para validação da detecção de intenções de agendamento
 * e blindagem anti-falsos positivos em mensagens recebidas do Blip.
 */
class WebhookIntentDetectionTest {

    // =========================================================================
    // A. Confirmações Válidas (Deve retornar CONFIRM / true)
    // =========================================================================
    @ParameterizedTest
    @ValueSource(strings = {
        "1", "1️⃣", "sim", "SIM", "confirmar", "CONFIRMAR", "confirmo", "confirmado", "confirma",
        "presença", "presenca", "confirmar presença", "confirmar presenca", "opção 1", "opcao 1",
        "1 - confirmar", "1.", "1 ", "confirmar!", "Sim."
    })
    void testValidConfirmations(String text) {
        assertEquals(WebhookIntent.CONFIRM, HandleBlipWebhookUseCase.detectIntent(text), "Falha ao classificar como CONFIRM: " + text);
        assertTrue(BlipWebhookController.isConfirmationOrAlterationIntentText(text), "Falha no bypass do Controller para: " + text);
    }

    // =========================================================================
    // B. Alterações Válidas (Deve retornar ALTER / true)
    // =========================================================================
    @ParameterizedTest
    @ValueSource(strings = {
        "2", "2️⃣", "alterar", "ALTERAR", "remarcar", "trocar",
        "solicitar alteração", "solicitar alteracao", "preciso alterar", "opção 2", "opcao 2",
        "2 - alterar", "2.", "2 ", "alterar!"
    })
    void testValidAlterations(String text) {
        assertEquals(WebhookIntent.ALTER, HandleBlipWebhookUseCase.detectIntent(text), "Falha ao classificar como ALTER: " + text);
        assertTrue(BlipWebhookController.isConfirmationOrAlterationIntentText(text), "Falha no bypass do Controller para: " + text);
    }

    // =========================================================================
    // C. Bloqueio por Negação (Deve retornar UNKNOWN / false)
    // =========================================================================
    @ParameterizedTest
    @ValueSource(strings = {
        "Não vou confirmar", "nao posso ir", "não quero alterar", "nunca confirmei", "nem pensar", "não", "nao"
    })
    void testNegationBlocking(String text) {
        assertEquals(WebhookIntent.UNKNOWN, HandleBlipWebhookUseCase.detectIntent(text), "Deveria bloquear por negação: " + text);
        assertFalse(BlipWebhookController.isConfirmationOrAlterationIntentText(text), "Controller deveria negar bypass para: " + text);
    }

    // =========================================================================
    // D. Bloqueio por Condicional / Dúvida (Deve retornar UNKNOWN / false)
    // =========================================================================
    @ParameterizedTest
    @ValueSource(strings = {
        "se eu confirmar tem taxa?", "caso precise alterar", "depois eu confirmo", "posso confirmar mais tarde?"
    })
    void testConditionalOrDoubtBlocking(String text) {
        assertEquals(WebhookIntent.UNKNOWN, HandleBlipWebhookUseCase.detectIntent(text), "Deveria bloquear por condicional/dúvida: " + text);
        assertFalse(BlipWebhookController.isConfirmationOrAlterationIntentText(text), "Controller deveria negar bypass para: " + text);
    }

    // =========================================================================
    // E. Bloqueio por Tamanho / Conversação Livre (Deve retornar UNKNOWN / false)
    // =========================================================================
    @ParameterizedTest
    @ValueSource(strings = {
        "Preciso confirmar com meu marido antes",
        "Vou me atrasar 15 minutos",
        "Qual o endereço da clínica?",
        "Essa atendente virtual não funciona direito",
        "Podem me responder primeiro?"
    })
    void testFreeTextAndLengthBlocking(String text) {
        assertEquals(WebhookIntent.UNKNOWN, HandleBlipWebhookUseCase.detectIntent(text), "Deveria bloquear por tamanho/conversação livre: " + text);
        assertFalse(BlipWebhookController.isConfirmationOrAlterationIntentText(text), "Controller deveria negar bypass para: " + text);
    }

    // =========================================================================
    // F. Edge Cases e Entradas Nulas/Vazias (Deve retornar UNKNOWN / false)
    // =========================================================================
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "@#$%!", "123456" })
    void testEdgeCasesAndNullOrEmpty(String text) {
        assertEquals(WebhookIntent.UNKNOWN, HandleBlipWebhookUseCase.detectIntent(text), "Deveria retornar UNKNOWN para entrada inválida: " + text);
        assertFalse(BlipWebhookController.isConfirmationOrAlterationIntentText(text), "Controller deveria retornar false para entrada inválida: " + text);
    }
}
