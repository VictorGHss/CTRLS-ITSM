package br.dev.ctrls.itsm.modules.access.domain.model;

/**
 * Validador utilitário puro para validação de CPFs da Receita Federal (Módulo 11).
 * Não possui dependências externas ou de framework.
 */
public final class CpfValidator {

    private CpfValidator() {
        // Utilitário estático imutável
    }

    /**
     * Valida se um CPF é matematicamente válido utilizando os dígitos verificadores do Módulo 11.
     *
     * @param rawCpf String contendo CPF formatado ou apenas dígitos.
     * @return true se o CPF for válido perante o algoritmo da Receita Federal.
     */
    public static boolean isValidCpf(String rawCpf) {
        if (rawCpf == null || rawCpf.isBlank()) {
            return false;
        }
        String cpf = rawCpf.replaceAll("\\D", "");
        if (cpf.length() != 11) {
            return false;
        }
        if (cpf.matches("^(\\d)\\1{10}$")) {
            return false;
        }

        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                sum += (cpf.charAt(i) - '0') * (10 - i);
            }
            int remainder = sum % 11;
            int firstCheck = remainder < 2 ? 0 : (11 - remainder);
            if ((cpf.charAt(9) - '0') != firstCheck) {
                return false;
            }

            sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += (cpf.charAt(i) - '0') * (11 - i);
            }
            remainder = sum % 11;
            int secondCheck = remainder < 2 ? 0 : (11 - remainder);
            return (cpf.charAt(10) - '0') == secondCheck;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sanitiza a string de CPF mantendo apenas os dígitos numéricos.
     */
    public static String cleanCpf(String rawCpf) {
        return rawCpf != null ? rawCpf.replaceAll("\\D", "") : "";
    }
}
