package br.dev.ctrls.itsm.modules.analytics.application.dto;

/**
 * Projeção de leitura para métricas agregadas retornadas por consultas nativas.
 */
public interface AnalyticsMetricView {
    String getName();

    Long getValue();
}