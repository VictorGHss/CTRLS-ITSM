package br.dev.ctrls.itsm.modules.finance.domain.model;

/**
 * Record para representação de referência de conta financeira.
 */
public record FinancialAccountRef(String accountId, String name, String type, boolean active) {
}

