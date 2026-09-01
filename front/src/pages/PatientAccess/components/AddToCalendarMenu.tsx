import React, { useState, useRef, useEffect } from 'react';
import { CalendarPlus, Calendar, Smartphone, ExternalLink, Download, Check, Apple } from 'lucide-react';
import { generateGoogleCalendarUrl, downloadIcsFile, openAppleCalendar } from '../utils/calendarUtils';
import type { CalendarEventDetails } from '../utils/calendarUtils';
import type { ClinicTheme } from '../utils/clinicThemes';

interface AddToCalendarMenuProps {
  doctorName?: string;
  patientName?: string;
  dateTimeStr?: string;
  clinicTheme: ClinicTheme;
}

export const AddToCalendarMenu: React.FC<AddToCalendarMenuProps> = ({
  doctorName,
  patientName,
  dateTimeStr,
  clinicTheme,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [downloaded, setDownloaded] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  if (!dateTimeStr) return null;

  const eventDetails: CalendarEventDetails = {
    title: `Consulta Médica - ${doctorName || clinicTheme.shortName}`,
    description: `Consulta médica de ${patientName || 'Paciente'} com ${doctorName || 'Médico(a)'}.\nLocal: ${clinicTheme.name}\nEndereço: ${clinicTheme.address}\n\nPor favor, chegue com 10 minutos de antecedência e traga RG/CPF.`,
    location: `${clinicTheme.name}, ${clinicTheme.address}`,
    dateTimeStr: dateTimeStr,
  };

  const handleGoogleCalendar = () => {
    const url = generateGoogleCalendarUrl(eventDetails);
    window.open(url, '_blank', 'noopener,noreferrer');
    setIsOpen(false);
  };

  const handleAppleCalendar = () => {
    openAppleCalendar(eventDetails);
    setDownloaded(true);
    setTimeout(() => {
      setDownloaded(false);
      setIsOpen(false);
    }, 1200);
  };

  const handleIcsDownload = () => {
    downloadIcsFile(eventDetails);
    setDownloaded(true);
    setTimeout(() => {
      setDownloaded(false);
      setIsOpen(false);
    }, 1200);
  };

  return (
    <div className="relative w-full mt-2" ref={menuRef}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="w-full py-2.5 px-3 bg-slate-100/90 hover:bg-slate-200/90 active:scale-[0.98] text-slate-700 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-2 border border-slate-200/80 cursor-pointer shadow-2xs"
      >
        <CalendarPlus className="w-3.5 h-3.5 text-slate-600" />
        <span>Adicionar à Agenda</span>
      </button>

      {isOpen && (
        <div className="absolute left-0 right-0 bottom-full mb-2 bg-white rounded-2xl shadow-xl border border-slate-200 p-2 z-50 animate-in fade-in slide-in-from-bottom-2 duration-150">
          <div className="px-3 py-2 border-b border-slate-100 mb-1">
            <span className="text-[11px] font-extrabold text-slate-800 block">Escolha seu calendário</span>
            <span className="text-[9.5px] text-slate-400 font-medium block">Adiciona lembretes automáticos para a consulta</span>
          </div>

          <div className="space-y-1">
            {/* Google Agenda */}
            <button
              type="button"
              onClick={handleGoogleCalendar}
              className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-left text-xs font-bold text-slate-700 hover:bg-slate-50 active:bg-slate-100 transition-colors group cursor-pointer"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-blue-50 flex items-center justify-center text-blue-600">
                  <Calendar className="w-4 h-4" />
                </div>
                <div>
                  <span className="text-xs font-bold text-slate-800 block">Google Agenda</span>
                  <span className="text-[9px] text-slate-400 font-medium block">Abre no app ou navegador</span>
                </div>
              </div>
              <ExternalLink className="w-3.5 h-3.5 text-slate-300 group-hover:text-blue-500 transition-colors" />
            </button>

            {/* Apple Calendário (iPhone / Mac) */}
            <button
              type="button"
              onClick={handleAppleCalendar}
              className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-left text-xs font-bold text-slate-700 hover:bg-slate-50 active:bg-slate-100 transition-colors group cursor-pointer"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-rose-50 flex items-center justify-center text-rose-600">
                  <Apple className="w-4 h-4" />
                </div>
                <div>
                  <span className="text-xs font-bold text-slate-800 block">Apple Calendário (iPhone)</span>
                  <span className="text-[9px] text-slate-400 font-medium block">Abre direto no app do iPhone</span>
                </div>
              </div>
              {downloaded ? (
                <Check className="w-4 h-4 text-emerald-500" />
              ) : (
                <ExternalLink className="w-3.5 h-3.5 text-slate-300 group-hover:text-rose-500 transition-colors" />
              )}
            </button>

            {/* Outros / Outlook / Download .ics */}
            <button
              type="button"
              onClick={handleIcsDownload}
              className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-left text-xs font-bold text-slate-700 hover:bg-slate-50 active:bg-slate-100 transition-colors group cursor-pointer"
            >
              <div className="flex items-center gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-slate-100 flex items-center justify-center text-slate-600">
                  <Smartphone className="w-4 h-4" />
                </div>
                <div>
                  <span className="text-xs font-bold text-slate-800 block">Outlook / Outros (.ics)</span>
                  <span className="text-[9px] text-slate-400 font-medium block">Baixar arquivo de agenda</span>
                </div>
              </div>
              <Download className="w-3.5 h-3.5 text-slate-300 group-hover:text-slate-600 transition-colors" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
