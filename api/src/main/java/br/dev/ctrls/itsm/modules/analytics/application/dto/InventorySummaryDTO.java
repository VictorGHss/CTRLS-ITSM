package br.dev.ctrls.itsm.modules.analytics.application.dto;

/**
 * DTO com as métricas resumidas do estoque.
 */
public record InventorySummaryDTO(
    long totalItems,
    long lowStockItems,
    long outOfStockItems,
    long receivedItemsCount
) {}
