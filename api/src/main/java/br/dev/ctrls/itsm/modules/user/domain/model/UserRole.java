package br.dev.ctrls.itsm.modules.user.domain.model;

/**
 * Papéis disponíveis no sistema para os usuários.
 * ADMIN: acesso total ao sistema
 * TECHNICIAN: técnico de TI
 * USER: usuário padrão
 */
public enum UserRole {
    ADMIN,
    TECHNICIAN,
    USER
}
