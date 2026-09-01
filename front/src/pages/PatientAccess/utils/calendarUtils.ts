/**
 * Utilitários para geração de links e arquivos de calendário (.ics / Google Calendar).
 */

export interface CalendarEventDetails {
  title: string;
  description: string;
  location: string;
  dateTimeStr?: string; // Formato esperado: "dd/MM/yyyy HH:mm" ou ISO
  durationMinutes?: number;
}

/**
 * Converte string no formato "dd/MM/yyyy HH:mm" ou "dd/MM/yyyy" para objeto Date.
 */
export function parseAppointmentDate(dateTimeStr?: string): { start: Date; end: Date } | null {
  if (!dateTimeStr) return null;

  try {
    const trimmed = dateTimeStr.trim();
    const parts = trimmed.split(' ');
    const datePart = parts[0];
    const timePart = parts[1] || '08:00';

    const [day, month, year] = datePart.split('/').map(Number);
    const [hour, minute] = timePart.split(':').map(Number);

    if (!day || !month || !year || isNaN(day) || isNaN(month) || isNaN(year)) {
      const parsed = new Date(dateTimeStr);
      if (!isNaN(parsed.getTime())) {
        const end = new Date(parsed.getTime() + 30 * 60 * 1000);
        return { start: parsed, end };
      }
      return null;
    }

    const start = new Date(year, month - 1, day, hour || 8, minute || 0, 0);
    const end = new Date(start.getTime() + 30 * 60 * 1000);
    return { start, end };
  } catch {
    return null;
  }
}

/**
 * Formata Date para formato UTC requerido pelo iCal e Google Calendar (YYYYMMDDTHHmmssZ).
 */
function formatDateToUtcString(date: Date): string {
  const pad = (n: number) => (n < 10 ? '0' + n : String(n));
  return (
    date.getUTCFullYear() +
    pad(date.getUTCMonth() + 1) +
    pad(date.getUTCDate()) +
    'T' +
    pad(date.getUTCHours()) +
    pad(date.getUTCMinutes()) +
    pad(date.getUTCSeconds()) +
    'Z'
  );
}

/**
 * Gera URL direta para adicionar evento no Google Agenda.
 */
export function generateGoogleCalendarUrl(event: CalendarEventDetails): string {
  const dates = parseAppointmentDate(event.dateTimeStr);
  let dateQuery = '';
  if (dates) {
    const startIso = formatDateToUtcString(dates.start);
    const endIso = formatDateToUtcString(dates.end);
    dateQuery = `&dates=${startIso}/${endIso}`;
  }

  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: event.title,
    details: event.description,
    location: event.location,
  });

  return `https://calendar.google.com/calendar/render?${params.toString()}${dateQuery}`;
}

/**
 * Gera e dispara o download de um arquivo .ics (iCalendar / Apple / Outlook).
 */
export function downloadIcsFile(event: CalendarEventDetails): void {
  const dates = parseAppointmentDate(event.dateTimeStr);
  const now = new Date();
  const dtStamp = formatDateToUtcString(now);
  const dtStart = dates ? formatDateToUtcString(dates.start) : dtStamp;
  const dtEnd = dates ? formatDateToUtcString(dates.end) : dtStamp;
  const uid = `inovare-app-${Date.now()}@itsm-inovare.ctrls.dev.br`;

  const icsContent = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Inovare Servicos de Saude//ITSM Acesso//PT',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'BEGIN:VEVENT',
    `UID:${uid}`,
    `DTSTAMP:${dtStamp}`,
    `DTSTART:${dtStart}`,
    `DTEND:${dtEnd}`,
    `SUMMARY:${escapeIcsText(event.title)}`,
    `DESCRIPTION:${escapeIcsText(event.description)}`,
    `LOCATION:${escapeIcsText(event.location)}`,
    'STATUS:CONFIRMED',
    // Alarme de lembrete 24 horas antes
    'BEGIN:VALARM',
    'TRIGGER:-P1D',
    'ACTION:DISPLAY',
    'DESCRIPTION:Lembrete de Consulta Médica (Amanhã)',
    'END:VALARM',
    // Alarme de lembrete 1 hora antes
    'BEGIN:VALARM',
    'TRIGGER:-PT1H',
    'ACTION:DISPLAY',
    'DESCRIPTION:Lembrete: Sua consulta é em 1 hora',
    'END:VALARM',
    'END:VEVENT',
    'END:VCALENDAR',
  ].join('\r\n');

  const blob = new Blob([icsContent], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', 'consulta-inovare.ics');
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function escapeIcsText(text: string): string {
  return text
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\n/g, '\\n');
}
