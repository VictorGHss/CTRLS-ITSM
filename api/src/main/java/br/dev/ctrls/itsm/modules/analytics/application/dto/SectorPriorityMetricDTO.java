package br.dev.ctrls.itsm.modules.analytics.application.dto;

/**
 * Métrica agregada por setor e nível consolidado de prioridade.
 */
public record SectorPriorityMetricDTO(
        String sector,
        String priority,
        long value
) {}