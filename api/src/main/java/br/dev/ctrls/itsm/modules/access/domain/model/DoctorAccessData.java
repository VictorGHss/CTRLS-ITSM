package br.dev.ctrls.itsm.modules.access.domain.model;

/**
 * Record contendo as credenciais de catraca física associadas ao médico visitado.
 * Comentários em PT-BR conforme as Regras de Ouro.
 */
public record DoctorAccessData(
    String matricula,
    String cpf
) {}
