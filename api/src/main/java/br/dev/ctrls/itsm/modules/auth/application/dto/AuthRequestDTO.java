package br.dev.ctrls.itsm.modules.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de requisição para o endpoint de login.
 */
public record AuthRequestDTO(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
