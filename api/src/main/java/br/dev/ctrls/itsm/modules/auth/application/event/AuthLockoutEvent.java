package br.dev.ctrls.itsm.modules.auth.application.event;

/**
 * Evento disparado quando uma conta ou IP atinge o limite máximo de tentativas incorretas
 * e é temporariamente bloqueado para prevenir ataques de força bruta.
 */
public record AuthLockoutEvent(
        String email,
        String clientIp,
        String reason,
        int attempts,
        int lockoutMinutes
) {}
