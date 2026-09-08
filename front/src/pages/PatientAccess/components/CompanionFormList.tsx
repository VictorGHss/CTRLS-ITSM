import React from 'react';
import { UserPlus, Plus, Trash2 } from 'lucide-react';
import type { ClinicTheme } from '../utils/clinicThemes';
import type { CompanionEntry } from '../types';
import { maskCpf } from '../utils/masks';

interface CompanionFormListProps {
  clinicTheme: ClinicTheme;
  hasCompanion: boolean;
  setHasCompanion: (v: boolean) => void;
  companions: CompanionEntry[];
  addCompanionField: () => void;
  removeCompanionField: (id: string) => void;
  updateCompanion: (id: string, field: 'name' | 'cpf' | 'birthDate', val: string) => void;
  patientName: string;
  patientCpf: string;
}

export const CompanionFormList: React.FC<CompanionFormListProps> = ({
  clinicTheme,
  hasCompanion,
  setHasCompanion,
  companions,
  addCompanionField,
  removeCompanionField,
  updateCompanion,
  patientName,
  patientCpf
}) => {
  const cleanPatientCpf = patientCpf.replace(/\D/g, '');

  return (
    <div className="pt-2 border-t border-slate-100">
      <label className="flex items-center gap-2.5 cursor-pointer py-1.5 select-none">
        <input
          type="checkbox"
          checked={hasCompanion}
          onChange={(e) => setHasCompanion(e.target.checked)}
          className="w-4 h-4 rounded border-slate-300"
          style={{ accentColor: clinicTheme.primaryColor }}
        />
        <span className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
          <UserPlus className="w-3.5 h-3.5" style={{ color: clinicTheme.primaryColor }} />
          Vou levar acompanhante(s)
        </span>
      </label>

      {hasCompanion && (
        <div
          className="mt-3 p-4 rounded-2xl space-y-3.5 animate-in fade-in duration-200 border"
          style={{
            backgroundColor: clinicTheme.secondaryColor,
            borderColor: `${clinicTheme.primaryColor}25`
          }}
        >
          <div className="flex items-center justify-between">
            <p
              className="text-[11px] font-bold uppercase tracking-wider"
              style={{ color: clinicTheme.primaryDarkColor }}
            >
              Acompanhantes ({companions.length})
            </p>
            <button
              type="button"
              onClick={addCompanionField}
              className="inline-flex items-center gap-1 text-[11px] font-bold px-2.5 py-1 rounded-xl bg-white border shadow-sm transition-all hover:scale-105 active:scale-95 cursor-pointer"
              style={{ color: clinicTheme.primaryDarkColor, borderColor: `${clinicTheme.primaryColor}30` }}
            >
              <Plus className="w-3 h-3" />
              + Adicionar outro
            </button>
          </div>

          {companions.map((comp, idx) => {
            const cleanCompCpf = comp.cpf.replace(/\D/g, '');
            const isSameAsPatient =
              cleanCompCpf === cleanPatientCpf && cleanPatientCpf.length === 11;
            const isDuplicate = companions.some(
              (other, oIdx) =>
                oIdx !== idx &&
                other.cpf.replace(/\D/g, '') === cleanCompCpf &&
                cleanCompCpf.length === 11
            );
            const isSameName =
              comp.name.trim().length >= 3 &&
              patientName.trim().length >= 3 &&
              comp.name.trim().toUpperCase() === patientName.trim().toUpperCase();

            return (
              <div
                key={comp.id}
                className="p-3 bg-white rounded-xl border border-slate-200/80 shadow-xs space-y-2 relative"
              >
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-extrabold uppercase text-slate-500 tracking-wider">
                    Acompanhante #{idx + 1}
                  </span>
                  {companions.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeCompanionField(comp.id)}
                      className="text-slate-400 hover:text-red-500 transition-colors p-1 cursor-pointer"
                      title="Remover acompanhante"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
                <div>
                  <input
                    type="text"
                    value={comp.name}
                    onChange={(e) => updateCompanion(comp.id, 'name', e.target.value)}
                    placeholder={`Nome do Acompanhante #${idx + 1} *`}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all form-input-themed"
                  />
                  {isSameName && (
                    <div className="flex items-center gap-1 mt-1 text-[11px] font-semibold text-rose-600 bg-rose-50 border border-rose-200 rounded px-2 py-0.5 animate-fadeIn">
                      <span>⚠️ O acompanhante não pode ser o próprio paciente titular.</span>
                    </div>
                  )}
                </div>
                <div>
                  <input
                    type="text"
                    inputMode="numeric"
                    value={comp.cpf}
                    onChange={(e) => updateCompanion(comp.id, 'cpf', maskCpf(e.target.value))}
                    placeholder="CPF do Acompanhante *"
                    maxLength={14}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all font-mono form-input-themed"
                  />
                  {isSameAsPatient && (
                    <div className="flex items-center gap-1 mt-1 text-[11px] font-semibold text-rose-600 bg-rose-50 border border-rose-200 rounded px-2 py-0.5 animate-fadeIn">
                      <span>⚠️ Não pode ser o mesmo CPF do paciente titular.</span>
                    </div>
                  )}
                  {isDuplicate && (
                    <div className="flex items-center gap-1 mt-1 text-[11px] font-semibold text-rose-600 bg-rose-50 border border-rose-200 rounded px-2 py-0.5 animate-fadeIn">
                      <span>⚠️ CPF duplicado com outro acompanhante.</span>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
