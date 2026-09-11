import React from 'react';
import { Stethoscope, User, MapPin, X } from 'lucide-react';
import type { ClinicTheme, DoctorSuggestion } from '../utils/clinicThemes';
import { resolveDoctorLocation } from '../utils/clinicThemes';

interface DoctorAutocompleteInputProps {
  clinicTheme: ClinicTheme;
  doctorInput: string;
  setDoctorInput: (val: string) => void;
  selectedLocation: string | null;
  setSelectedLocation: (val: string | null) => void;
  isDoctorDropdownOpen: boolean;
  setIsDoctorDropdownOpen: (val: boolean) => void;
  doctorDropdownRef: React.RefObject<HTMLDivElement | null>;
  filteredDoctors: DoctorSuggestion[];
  handleSelectDoctor: (doc: DoctorSuggestion) => void;
}

export const DoctorAutocompleteInput: React.FC<DoctorAutocompleteInputProps> = ({
  clinicTheme,
  doctorInput,
  setDoctorInput,
  selectedLocation,
  setSelectedLocation,
  isDoctorDropdownOpen,
  setIsDoctorDropdownOpen,
  doctorDropdownRef,
  filteredDoctors,
  handleSelectDoctor
}) => {
  if (clinicTheme.id !== 'portal') return null;

  return (
    <div className="relative" ref={doctorDropdownRef}>
      <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center justify-between">
        <span className="flex items-center gap-1.5">
          <Stethoscope className="w-3.5 h-3.5 text-slate-400" />
          Médico / Especialidade <span className="text-[10px] text-slate-400 font-normal">(opcional)</span>
        </span>
        {doctorInput && (
          <button
            type="button"
            onClick={() => {
              setDoctorInput('');
              setSelectedLocation(null);
              setIsDoctorDropdownOpen(false);
            }}
            className="text-[10px] font-bold text-slate-400 hover:text-red-500 transition-colors flex items-center gap-0.5 cursor-pointer"
          >
            <X className="w-3 h-3" /> Limpar
          </button>
        )}
      </label>

      <div className="relative">
        <input
          type="text"
          value={doctorInput}
          onChange={(e) => {
            const val = e.target.value;
            setDoctorInput(val);
            setSelectedLocation(null);
            if (val.trim().length >= 3) {
              setIsDoctorDropdownOpen(true);
            } else {
              setIsDoctorDropdownOpen(false);
            }
          }}
          onFocus={() => {
            if (doctorInput.trim().length >= 3) {
              setIsDoctorDropdownOpen(true);
            }
          }}
          placeholder="Digite o nome do médico ou setor (ex: Brenda, Ginecologia...)"
          className="w-full pl-3.5 pr-9 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-xs font-bold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all form-input-themed"
        />
        {doctorInput && (
          <button
            type="button"
            onClick={() => {
              setDoctorInput('');
              setSelectedLocation(null);
              setIsDoctorDropdownOpen(false);
            }}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-0.5 rounded-full hover:bg-slate-200/50 transition-all cursor-pointer"
            title="Limpar médico"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {/* Dica discreta ao digitar menos de 3 caracteres */}
      {doctorInput.trim().length > 0 && doctorInput.trim().length < 3 && (
        <p className="text-[10px] font-medium text-slate-400 mt-1 pl-1">
          Digite mais {3 - doctorInput.trim().length} letra(s) para pesquisar médicos...
        </p>
      )}

      {/* Dropdown Flutuante após 3 caracteres */}
      {isDoctorDropdownOpen && doctorInput.trim().length >= 3 && (
        <div className="absolute left-0 right-0 z-50 mt-1.5 bg-white border border-slate-200 rounded-2xl shadow-xl shadow-slate-300/50 max-h-56 overflow-y-auto divide-y divide-slate-100 animate-in fade-in zoom-in-95 duration-150">
          {filteredDoctors.length > 0 ? (
            filteredDoctors.map((doc, idx) => (
              <button
                key={`${doc.name}-${idx}`}
                type="button"
                onClick={() => handleSelectDoctor(doc)}
                className="w-full px-3.5 py-2.5 text-left hover:bg-slate-50 flex items-start justify-between gap-2 transition-colors cursor-pointer"
              >
                <div>
                  <div className="text-xs font-bold text-slate-800 flex items-center gap-1.5">
                    <User className="w-3 h-3 text-slate-400 shrink-0" />
                    {doc.name}
                  </div>
                  <div className="text-[10px] font-medium text-slate-500 mt-0.5 flex items-center gap-1">
                    <MapPin className="w-2.5 h-2.5 text-slate-400 shrink-0" />
                    {doc.location}
                  </div>
                </div>
                {doc.specialty && (
                  <span
                    className="shrink-0 text-[9px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-lg"
                    style={{
                      backgroundColor: `${clinicTheme.secondaryColor}40`,
                      color: clinicTheme.primaryDarkColor
                    }}
                  >
                    {doc.specialty}
                  </span>
                )}
              </button>
            ))
          ) : (
            <div className="p-3 text-center">
              <p className="text-xs font-semibold text-slate-600">Nenhum médico encontrado</p>
              <p className="text-[10px] text-slate-400 mt-0.5">
                Você pode manter "{doctorInput}" ou apagar para recepção geral.
              </p>
            </div>
          )}
        </div>
      )}

      {/* Badge com a localização confirmada */}
      {(selectedLocation || (doctorInput && resolveDoctorLocation(doctorInput) !== '1º Andar - Lado Direito')) && (
        <div
          className="mt-2 p-2 rounded-xl border flex items-center gap-2 text-[11px] font-semibold animate-in fade-in duration-150"
          style={{
            backgroundColor: `${clinicTheme.secondaryColor}30`,
            borderColor: `${clinicTheme.primaryColor}30`,
            color: clinicTheme.primaryDarkColor
          }}
        >
          <MapPin className="w-3.5 h-3.5 shrink-0" style={{ color: clinicTheme.primaryColor }} />
          <span className="truncate">
            {selectedLocation || resolveDoctorLocation(doctorInput)}
          </span>
        </div>
      )}
    </div>
  );
};
