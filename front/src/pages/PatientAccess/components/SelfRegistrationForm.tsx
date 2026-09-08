import React, { useState } from 'react';
import { UserPlus, Search, CheckCircle2, AlertCircle } from 'lucide-react';
import type { ClinicTheme } from '../utils/clinicThemes';
import type { AccessCredential } from '../types';
import { RegistrationTab } from './RegistrationTab';
import { LookupTab } from './LookupTab';

interface SelfRegistrationFormProps {
  clinicTheme: ClinicTheme;
  onSuccess: (credentials: AccessCredential[], authInfo?: { token?: string; phoneDigits?: string }) => void;
}

export const SelfRegistrationForm: React.FC<SelfRegistrationFormProps> = ({ clinicTheme, onSuccess }) => {
  const [activeTab, setActiveTab] = useState<'register' | 'lookup'>('register');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  return (
    <div className="w-full max-w-md mx-auto">
      {/* Header com Título e Subtítulo */}
      <div className="text-center mb-6">
        <h2 className="text-xl font-black text-slate-800 tracking-tight">
          {clinicTheme.name}
        </h2>
        <p className="text-xs font-semibold text-slate-500 mt-0.5">
          {clinicTheme.subtitle}
        </p>
      </div>

      {/* Card Principal */}
      <div className="bg-white rounded-3xl p-6 shadow-xl shadow-slate-200/50 border border-slate-100 relative overflow-hidden">
        <style>{`
          .form-input-themed:focus {
            border-color: ${clinicTheme.primaryColor} !important;
            box-shadow: 0 0 0 2px ${clinicTheme.primaryColor}30 !important;
            background-color: #ffffff !important;
          }
        `}</style>

        {/* Barra superior de abas */}
        <div className="flex bg-slate-100/80 p-1 rounded-2xl">
          <button
            type="button"
            onClick={() => {
              setActiveTab('register');
              setErrorMessage(null);
            }}
            className={`flex-1 py-2.5 px-3 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
              activeTab === 'register'
                ? 'bg-white text-slate-800 shadow-sm'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <UserPlus className="w-3.5 h-3.5" style={{ color: activeTab === 'register' ? clinicTheme.primaryColor : undefined }} />
            Novo Cadastro
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveTab('lookup');
              setErrorMessage(null);
            }}
            className={`flex-1 py-2.5 px-3 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
              activeTab === 'lookup'
                ? 'bg-white text-slate-800 shadow-sm'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <Search className="w-3.5 h-3.5" style={{ color: activeTab === 'lookup' ? clinicTheme.primaryColor : undefined }} />
            Consultar por CPF
          </button>
        </div>

        {/* Mensagem de Erro */}
        {errorMessage && (
          <div className="mt-4 p-3.5 rounded-2xl bg-red-50 border border-red-200/60 flex items-start gap-2.5 text-left animate-in fade-in duration-200">
            <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
            <p className="text-xs font-semibold text-red-700 leading-snug">
              {errorMessage}
            </p>
          </div>
        )}

        {/* Conteúdo da Aba Ativa */}
        {activeTab === 'register' ? (
          <RegistrationTab
            clinicTheme={clinicTheme}
            onSuccess={onSuccess}
            setParentErrorMessage={setErrorMessage}
          />
        ) : (
          <LookupTab
            clinicTheme={clinicTheme}
            onSuccess={onSuccess}
            setParentErrorMessage={setErrorMessage}
          />
        )}

        <div className="mt-6 pt-4 border-t border-slate-100 text-center">
          <p className="text-[11px] text-slate-400 font-semibold flex items-center justify-center gap-1.5">
            <CheckCircle2 className="w-3.5 h-3.5" style={{ color: clinicTheme.primaryColor }} />
            Acesso integrado às catracas do Edifício Inovare
          </p>
        </div>
      </div>
    </div>
  );
};
