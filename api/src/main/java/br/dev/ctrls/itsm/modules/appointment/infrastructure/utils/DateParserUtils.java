package br.dev.ctrls.itsm.modules.appointment.infrastructure.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilitário robusto para parsing e normalização de datas digitadas de forma flexível pelo paciente.
 * Converte qualquer variação (dd/mm/aaaa, dd-mm-aaaa, dd.mm.aa, dd mm aaaa, yyyy-mm-dd)
 * para o padrão ISO-8601 exigido pelo Feegow ERP (YYYY-MM-DD).
 */
@Slf4j
public class DateParserUtils {

    private static final Pattern DATE_PATTERN_DAY_FIRST = Pattern.compile("^(\\d{1,2})[\\/\\-\\.\\s]+(\\d{1,2})[\\/\\-\\.\\s]+(\\d{2,4})$");
    private static final Pattern DATE_PATTERN_YEAR_FIRST = Pattern.compile("^(\\d{4})[\\/\\-\\.\\s]+(\\d{1,2})[\\/\\-\\.\\s]+(\\d{1,2})$");
    private static final Pattern DATE_PATTERN_DIGITS_ONLY = Pattern.compile("^(\\d{2})(\\d{2})(\\d{2,4})$");

    private DateParserUtils() {
        // Construtor privado para classe utilitária
    }

    /**
     * Converte uma string de data arbitrária para o formato ISO (YYYY-MM-DD).
     *
     * @param input text de data fornecido pelo usuário (ex: "15/08/1990", "15081990", "15 08 90", "15.08.1990", "1990-08-15")
     * @return String formatada em ISO (YYYY-MM-DD)
     * @throws IllegalArgumentException se a data for nula, vazia ou inválida
     */
    public static String parseToIsoDate(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Data de nascimento não fornecida ou em branco.");
        }

        String cleaned = input.trim();

        // 1. Tenta padronizar no formato Dia-Mês-Ano com separadores
        Matcher dayMatcher = DATE_PATTERN_DAY_FIRST.matcher(cleaned);
        if (dayMatcher.matches()) {
            try {
                int day = Integer.parseInt(dayMatcher.group(1));
                int month = Integer.parseInt(dayMatcher.group(2));
                int rawYear = Integer.parseInt(dayMatcher.group(3));

                int year = resolveYear(rawYear);
                LocalDate date = LocalDate.of(year, month, day);
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ex) {
                log.warn("[DATE-PARSER] Erro ao parsear data dia-primeiro '{}': {}", cleaned, ex.getMessage());
            }
        }

        // 2. Tenta padronizar no formato apenas números (ex: 25081990 ou 250890)
        Matcher digitsMatcher = DATE_PATTERN_DIGITS_ONLY.matcher(cleaned.replaceAll("\\D", ""));
        if (digitsMatcher.matches()) {
            try {
                int day = Integer.parseInt(digitsMatcher.group(1));
                int month = Integer.parseInt(digitsMatcher.group(2));
                int rawYear = Integer.parseInt(digitsMatcher.group(3));

                int year = resolveYear(rawYear);
                LocalDate date = LocalDate.of(year, month, day);
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ex) {
                log.warn("[DATE-PARSER] Erro ao parsear data contínua '{}': {}", cleaned, ex.getMessage());
            }
        }

        // 3. Tenta padronizar no formato Ano-Mês-Dia
        Matcher yearMatcher = DATE_PATTERN_YEAR_FIRST.matcher(cleaned);
        if (yearMatcher.matches()) {
            try {
                int year = Integer.parseInt(yearMatcher.group(1));
                int month = Integer.parseInt(yearMatcher.group(2));
                int day = Integer.parseInt(yearMatcher.group(3));

                LocalDate date = LocalDate.of(year, month, day);
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ex) {
                log.warn("[DATE-PARSER] Erro ao parsear data ano-primeiro '{}': {}", cleaned, ex.getMessage());
            }
        }

        // 3. Tenta fallback direto via LocalDate ISO_LOCAL_DATE
        try {
            LocalDate date = LocalDate.parse(cleaned, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            log.error("[DATE-PARSER] Formato de data totalmente irreconhecível: '{}'", cleaned);
            throw new IllegalArgumentException("Formato de data inválido: '" + input + "'. Use dd/mm/aaaa ou dd-mm-aaaa.");
        }
    }

    private static int resolveYear(int rawYear) {
        if (rawYear >= 100) {
            return rawYear;
        }
        int currentTwoDigitYear = LocalDate.now().getYear() % 100;
        if (rawYear > currentTwoDigitYear) {
            return 1900 + rawYear;
        } else {
            return 2000 + rawYear;
        }
    }
}