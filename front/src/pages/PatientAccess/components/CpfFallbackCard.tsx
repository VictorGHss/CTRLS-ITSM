import React from 'react';
import { ShieldCheck, AlertTriangle, RefreshCw } from 'lucide-react';

interface CpfFallbackCardProps {
  cpfInput: string;
  cpfSubmitLoading: boolean;
  cpfSubmitError: string | null;
  onCpfChange: (val: string) => void;
  onSubmit: (e: React.FormEvent) => void;
}

export const CpfFallbackCard: React.FC<CpfFallbackCardProps> = ({
  cpfInput,
  cpfSubmitLoading,
  cpfSubmitError,
  onCpfChange,
  onSubmit,
}) => {
  return (
    <div className="bg-white border border-slate-200/80 rounded-3xl p-6 space-y-5 shadow-sm text-center">
      <div className="w-14 h-14 bg-brand-primary/10 text-brand-primary rounded-full flex items-center justify-center mx-auto">
        <ShieldCheck className="w-7 h-7" />
      </div>
      <div className="space-y-1.5">
        <h3 className="text-md font-bold text-slate-800">Correção do CPF</h3>
        <p className="text-xs text-slate-500 leading-relaxed max-w-[290px] mx-auto">
          O CPF cadastrado está incorreto. Digite os 11 números do seu CPF para liberar seu acesso na clínica:
        </p>
      </div>

      <form onSubmit={onSubmit} className="space-y-4">
        <input
          type="text"
          inputMode="numeric"
          placeholder="000.000.000-00"
          value={cpfInput}
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
          disabled={cpfSubmitLoading}
          className="w-full text-center py-3.5 px-4 font-bold text-slate-700 border border-slate-200 rounded-2xl focus:border-brand-primary focus:ring-4 focus:ring-brand-primary/10 transition-all font-mono"
        />

        {cpfSubmitError && (
          <div className="text-xs font-semibold text-red-600 bg-red-50 border border-red-100 rounded-xl py-2 px-3 flex items-center gap-1.5 justify-center">
            <AlertTriangle className="w-3.5 h-3.5" />
            {cpfSubmitError}
          </div>
        )}

        <button
          type="submit"
          disabled={cpfSubmitLoading || cpfInput.replace(/\D/g, '').length !== 11}
          className={`w-full py-3.5 px-5 rounded-2xl font-bold transition-all shadow-md flex items-center justify-center gap-2 ${
            cpfInput.replace(/\D/g, '').length === 11 && !cpfSubmitLoading
              ? 'bg-gradient-to-r from-brand-primary to-brand-primary-dark text-white hover:scale-[1.01] active:scale-[0.99] cursor-pointer'
              : 'bg-slate-100 text-slate-400 cursor-not-allowed shadow-none'
          }`}
        >
          {cpfSubmitLoading ? (
            <>
              <RefreshCw className="w-4 h-4 animate-spin" />
              Validando...
            </>
          ) : (
            'Salvar e Liberar Acesso'
          )}
        </button>
      </form>
    </div>
  );
};
