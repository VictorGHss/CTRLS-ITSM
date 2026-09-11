package br.dev.ctrls.itsm.modules.access.application.usecase;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Caso de Uso: Geração de arquivos iCalendar (.ics) para Apple Calendar e Outlook.
 * Utiliza Java 21 Text Blocks para formatação limpa e imutável.
 */
@Component
public class GenerateCalendarIcsUseCase {

    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public String execute(String title, String start, String end, String location, String description) {
        String uid = "itsm-" + System.currentTimeMillis() + "@itsm.ctrls.dev.br";
        String nowUtc = UTC_FORMATTER.format(Instant.now());

        String dtStart = (start != null && !start.isBlank()) ? start : nowUtc;
        String dtEnd = (end != null && !end.isBlank()) ? end : dtStart;

        String escapedTitle = escapeIcs(title);
        String escapedDesc = escapeIcs(description);
        String escapedLoc = escapeIcs(location);

        return """
BEGIN:VCALENDAR\r
VERSION:2.0\r
PRODID:-//CTRLS ITSM//ITSM Acesso//PT\r
CALSCALE:GREGORIAN\r
METHOD:PUBLISH\r
BEGIN:VEVENT\r
UID:%s\r
DTSTAMP:%s\r
DTSTART:%s\r
DTEND:%s\r
SUMMARY:%s\r
DESCRIPTION:%s\r
LOCATION:%s\r
STATUS:CONFIRMED\r
BEGIN:VALARM\r
TRIGGER:-P1D\r
ACTION:DISPLAY\r
DESCRIPTION:Lembrete de Consulta Médica (Amanhã)\r
END:VALARM\r
BEGIN:VALARM\r
TRIGGER:-PT1H\r
ACTION:DISPLAY\r
DESCRIPTION:Lembrete: Sua consulta é em 1 hora\r
END:VALARM\r
END:VEVENT\r
END:VCALENDAR""".formatted(uid, nowUtc, dtStart, dtEnd, escapedTitle, escapedDesc, escapedLoc);
    }

    private String escapeIcs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
