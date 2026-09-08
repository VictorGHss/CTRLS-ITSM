import React from 'react';
import { User, AlertTriangle, RefreshCw } from 'lucide-react';
import type { ClinicTheme } from '../utils/clinicThemes';

interface CompanionModalProps {
  isOpen: boolean;
  companionName: string;
  companionCpf: string;
  companionBirthDate?: string;
  patientCpf?: string;
  patientName?: string;
  existingCompanionsCpfs?: string[];
  companionSubmitLoading: boolean;
  companionSubmitError: string | null;
  onNameChange: (val: string) => void;
  onCpfChange: (val: string) => void;
  onBirthDateChange?: (val: string) => void;
  onSubmit: (e: React.FormEvent) => void;
  onClose: () => void;
  clinicTheme?: ClinicTheme;
}

export const CompanionModal: React.FC<CompanionModalProps> = ({
  isOpen,
  companionName,
  companionCpf,
  patientCpf,
  patientName,
  existingCompanionsCpfs,
  companionSubmitLoading,
  companionSubmitError,
  onNameChange,
  onCpfChange,
  onSubmit,
  onClose,
  clinicTheme,
}) => {
  if (!isOpen) return null;

  const primaryColor = clinicTheme?.primaryColor || '#00875F';
  const primaryDarkColor = clinicTheme?.primaryDarkColor || '#00583F';
  const secondaryColor = clinicTheme?.secondaryColor || '#E6F4EA';

  const cleanCompCpf = companionCpf.replace(/\D/g, '');
  const cleanPatientCpf = patientCpf ? patientCpf.replace(/\D/g, '') : '';
  const isSameCpfAsPatient = cleanPatientCpf.length === 11 && cleanCompCpf === cleanPatientCpf;
  const isDuplicateCompanionCpf = (existingCompanionsCpfs || []).some(
    c => c.replace(/\D/g, '') === cleanCompCpf && cleanCompCpf.length === 11
  );
  const isSameNameAsPatient = Boolean(
    patientName &&
    companionName.trim().length >= 3 &&
    patientName.trim().length >= 3 &&
    companionName.trim().toUpperCase() === patientName.trim().toUpperCase()
  );

  const hasConflict = isSameCpfAsPatient || isDuplicateCompanionCpf || isSameNameAsPatient;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
      <div className="w-full max-w-md bg-white rounded-3xl shadow-2xl border border-slate-100 p-6 flex flex-col space-y-4 animate-in fade-in zoom-in-95 duration-200">
        <div className="text-center">
          <div 
            className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 mb-2 border"
            style={{
              backgroundColor: secondaryColor,
              color: primaryDarkColor,
              borderColor: `${primaryColor}25`
            }}
          >
            <User className="w-4 h-4" style={{ color: primaryColor }} />
            <span className="text-xs font-semibold">Novo Acompanhante</span>
          </div>
          <h3 className="text-md font-bold text-slate-800">Cadastrar Acompanhante</h3>
          <p className="text-[11px] text-slate-500 leading-relaxed max-w-[280px] mx-auto mt-1">
            Informe o nome e CPF para cadastrar o acompanhante nas catracas físicas de acesso.
          </p>
        </div>

        <form onSubmit={onSubmit} className="space-y-4 pt-2">
          <div className="space-y-3">
            {/* Nome Completo */}
            <div>
              <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block mb-1">Nome Completo *</label>
              <input
                type="text"
                required
                placeholder="Nome do acompanhante"
                value={companionName}
                onChange={(e) => onNameChange(e.target.value)}
                disabled={companionSubmitLoading}
                className="w-full py-3 px-4 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 transition-all text-xs font-semibold text-slate-700"
              />
              {isSameNameAsPatient && (
                <div className="text-[11px] font-semibold text-rose-600 bg-rose-50 border border-rose-200 rounded-xl py-1.5 px-3 flex items-center gap-1.5 mt-1.5 animate-fadeIn">
                  <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                  <span>O acompanhante não pode ser o próprio paciente titular.</span>
                </div>
              )}
            </div>

            {/* CPF */}
            <div>
              <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block mb-1">
                CPF *
              </label>
              <input
                type="text"
                inputMode="numeric"
                required
                placeholder="000.000.000-00"
                value={companionCpf}
                onChange={(e) => {
                  const digits = e.target.value.replace(/\D/g, '').substring(0, 11);
                  let masked = digits;
                  if (digits.length > 9) {
                    masked = `${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9)}`;
                  } else if (digits.length > 6) {
                    masked = `${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6)}`;
                  } else if (digits.length > 3) {
                    masked = `${digits.substring(0, 3)}.${digits.substring(3)}`;
                  }
                  onCpfChange(masked);
                }}
                disabled={companionSubmitLoading}
                className={`w-full py-3 px-4 border rounded-xl focus:outline-none focus:ring-2 transition-all text-xs font-mono font-semibold text-slate-700 ${
                  isSameCpfAsPatient || isDuplicateCompanionCpf ? 'border-rose-300 bg-rose-50/40' : 'border-slate-200'
                }`}
              />
              {isSameCpfAsPatient && (
                <div className="text-[11px] font-semibold text-rose-600 bg-rose-50 border border-rose-200 rounded-xl py-1.5 px-3 flex items-center gap-1.5 mt-1.5 animate-fadeIn">
                  <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                  <span>Este CPF pertence ao paciente titular. O acompanhante deve possuir seu próprio CPF para liberar a catraca.</span>
                </div>
              )}
              {isDuplicateCompanionCpf && (
                <div className="text-[11px] font-semibold text-rose-600 bg-rose-50 border border-rose-200 rounded-xl py-1.5 px-3 flex items-center gap-1.5 mt-1.5 animate-fadeIn">
                  <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                  <span>Já existe um acompanhante cadastrado com este CPF.</span>
                </div>
              )}
            </div>
          </div>

          {companionSubmitError && (
            <div className="text-[11px] font-semibold text-red-600 bg-red-50 border border-red-100 rounded-xl py-2 px-3 flex items-center gap-1.5 justify-center">
              <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
              <span>{companionSubmitError}</span>
            </div>
          )}

          <div className="flex gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={companionSubmitLoading}
              className="flex-1 py-3 border border-slate-200 text-slate-500 rounded-xl text-xs font-bold transition-all hover:bg-slate-50 active:scale-[0.98] cursor-pointer"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={companionSubmitLoading || !companionName.trim() || cleanCompCpf.length !== 11 || hasConflict}
              className={`flex-1 py-3 rounded-xl text-xs font-bold transition-all shadow-md flex items-center justify-center gap-2 ${
                companionName.trim() && cleanCompCpf.length === 11 && !companionSubmitLoading && !hasConflict
                  ? 'text-white hover:scale-[1.01] active:scale-[0.99] cursor-pointer'
                  : 'bg-slate-100 text-slate-400 cursor-not-allowed shadow-none'
              }`}
              style={
                companionName.trim() && cleanCompCpf.length === 11 && !companionSubmitLoading && !hasConflict
                  ? { backgroundImage: `linear-gradient(to right, ${primaryColor}, ${primaryDarkColor})` }
                  : undefined
              }
            >
              {companionSubmitLoading ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  Cadastrando...
                </>
              ) : (
                'Cadastrar'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
