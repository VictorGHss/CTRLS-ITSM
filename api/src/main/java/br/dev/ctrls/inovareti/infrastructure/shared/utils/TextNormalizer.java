package br.dev.ctrls.inovareti.infrastructure.shared.utils;

import java.text.Normalizer;

/**
 * Utilitário centralizado para normalização, sanitização e comparação de textos.
 */
public final class TextNormalizer {

    private TextNormalizer() {
        // Utilitário estático
    }

    /**
     * Remove acentuação, caracteres diacríticos, pontuações excessivas e converte para caixa baixa.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }

    /**
     * Verifica se o texto normalizado contém o termo de busca normalizado.
     */
    public static boolean containsNormalized(String source, String target) {
        if (source == null || target == null) {
            return false;
        }
        return normalize(source).contains(normalize(target));
    }

    /**
     * Sanitiza strings removendo quebras de linha e múltiplos espaços em branco consecutivos.
     */
    public static String collapseWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
