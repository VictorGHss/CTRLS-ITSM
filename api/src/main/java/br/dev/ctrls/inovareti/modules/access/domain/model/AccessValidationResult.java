package br.dev.ctrls.inovareti.modules.access.domain.model;

/**
 * Record de transporte de dados de validação de acesso físico às catracas.
 * Comentários em PT-BR conforme as Regras de Ouro.
 */
public record AccessValidationResult(
    boolean authorized,
    String name,
    String accessCredential,
    boolean requiresCpfFallback,
    String message,
    String accessToken,
    String accessUrl
) {
    public AccessValidationResult(boolean authorized, String name, String accessCredential, boolean requiresCpfFallback, String message) {
        this(authorized, name, accessCredential, requiresCpfFallback, message, null, null);
    }
}
