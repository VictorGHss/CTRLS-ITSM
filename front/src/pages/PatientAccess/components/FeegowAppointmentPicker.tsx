import React from 'react';
import { Stethoscope, MapPin } from 'lucide-react';
import type { ClinicTheme } from '../utils/clinicThemes';
import { resolveDoctorLocation } from '../utils/clinicThemes';
import type { FeegowAppointmentItem } from '../types';

interface FeegowAppointmentPickerProps {
  clinicTheme: ClinicTheme;
  appointments: FeegowAppointmentItem[];
  selectedAppointmentId: string | null;
  onSelectAppointment: (appt: FeegowAppointmentItem) => void;
  onSwitchToManual: () => void;
}

export const FeegowAppointmentPicker: React.FC<FeegowAppointmentPickerProps> = ({
  clinicTheme,
  appointments,
  selectedAppointmentId,
  onSelectAppointment,
  onSwitchToManual
}) => {
  if (appointments.length === 0) return null;

  return (
    <div
      className="p-4 rounded-2xl border shadow-xs space-y-3 animate-in fade-in slide-in-from-top-2 duration-200"
      style={{
        backgroundColor: `${clinicTheme.secondaryColor}20`,
        borderColor: `${clinicTheme.primaryColor}35`
      }}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span
            className="w-6 h-6 rounded-full text-white flex items-center justify-center text-xs font-bold shadow-xs"
            style={{ backgroundColor: clinicTheme.primaryColor }}
          >
            ✓
          </span>
          <div>
            <span
              className="text-xs font-extrabold block leading-tight"
              style={{ color: clinicTheme.primaryDarkColor }}
            >
              Consulta Localizada no Feegow!
            </span>
            <span className="text-[10.5px] text-slate-600 font-medium">
              Médico, horário e local pré-selecionados
            </span>
          </div>
        </div>
        <button
          type="button"
          onClick={onSwitchToManual}
          className="text-[11px] font-bold text-slate-500 hover:text-slate-800 underline decoration-slate-300 underline-offset-2 transition-colors cursor-pointer"
        >
          Alterar médico
        </button>
      </div>

      <div className="space-y-2 pt-0.5">
        {appointments.map((appt) => {
          const isSelected = selectedAppointmentId === appt.appointmentId;
          const resolvedFloor = resolveDoctorLocation(appt.doctorName, clinicTheme.floorInfo);
          return (
            <div
              key={appt.appointmentId}
              onClick={() => onSelectAppointment(appt)}
              className="p-3 rounded-xl border transition-all cursor-pointer flex flex-col gap-1.5"
              style={
                isSelected
                  ? {
                      backgroundColor: '#ffffff',
                      borderColor: clinicTheme.primaryColor,
                      boxShadow: `0 0 0 1.5px ${clinicTheme.primaryColor}50`
                    }
                  : {
                      backgroundColor: 'rgba(255, 255, 255, 0.7)',
                      borderColor: `${clinicTheme.primaryColor}25`
                    }
              }
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-extrabold text-slate-800 flex items-center gap-1.5">
                  <Stethoscope className="w-3.5 h-3.5 shrink-0" style={{ color: clinicTheme.primaryColor }} />
                  {appt.doctorName || clinicTheme.name}
                </span>
                <span
                  className="text-[10px] font-extrabold px-2 py-0.5 rounded-full"
                  style={
                    appt.isToday
                      ? {
                          backgroundColor: `${clinicTheme.secondaryColor}60`,
                          color: clinicTheme.primaryDarkColor
                        }
                      : {
                          backgroundColor: '#f1f5f9',
                          color: '#475569'
                        }
                  }
                >
                  {appt.formattedDateTime}
                </span>
              </div>

              <div className="flex items-center justify-between text-[11px] text-slate-500 pt-0.5 border-t border-slate-100">
                <span className="font-medium truncate max-w-[55%]">
                  {appt.specialty || 'Consulta'}
                </span>
                <span className="font-bold text-slate-700 flex items-center gap-1 shrink-0">
                  <MapPin className="w-3 h-3" style={{ color: clinicTheme.primaryColor }} />
                  {resolvedFloor}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
