package br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils;

/**
 * Utilitário centralizado para higienização e conversão de IDs de agendamento do Feegow.
 */
public final class AppointmentIdNormalizer {

    private AppointmentIdNormalizer() {
        // Utilitário estático
    }

    /**
     * Normaliza um ID de agendamento em String, removendo pontuações decimais (.0) e espaços.
     * Exemplo: "3360464.0" -> "3360464", "  3360464  " -> "3360464", "AG_3360464" -> "3360464".
     */
    public static String normalize(String rawId) {
        if (rawId == null || rawId.isBlank() || "null".equalsIgnoreCase(rawId.trim())) {
            return "";
        }
        String idStr = rawId.trim();
        if (idStr.endsWith(".0")) {
            idStr = idStr.substring(0, idStr.length() - 2);
        }
        if (idStr.contains(".")) {
            idStr = idStr.split("\\.")[0];
        }
        // Se contiver caracteres não-dígitos misturados, extrai apenas a sequência de dígitos
        String digitsOnly = idStr.replaceAll("\\D+", "");
        return digitsOnly.isEmpty() ? idStr : digitsOnly;
    }

    /**
     * Converte o ID para Long de forma segura, retornando null se inválido.
     */
    public static Long toLongOrNull(String rawId) {
        String norm = normalize(rawId);
        if (norm.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(norm);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Converte o ID para Integer de forma segura, retornando null se inválido.
     */
    public static Integer toIntegerOrNull(String rawId) {
        String norm = normalize(rawId);
        if (norm.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(norm);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
