import React from 'react';
import { 
  ShieldCheck, 
  RefreshCw, 
  ArrowRight, 
  AlertTriangle, 
  Lock 
} from 'lucide-react';

interface TwoFactorAuthChallengeProps {
  digits: string[];
  inputRefs: React.RefObject<HTMLInputElement | null>[];
  challengeLoading: boolean;
  challengeError: string | null;
  onDigitChange: (index: number, val: string) => void;
  onDigitKeyDown: (index: number, e: React.KeyboardEvent<HTMLInputElement>) => void;
  onUnlock: (e: React.FormEvent) => void;
}

export const TwoFactorAuthChallenge: React.FC<TwoFactorAuthChallengeProps> = ({
  digits,
  inputRefs,
  challengeLoading,
  challengeError,
  onDigitChange,
  onDigitKeyDown,
  onUnlock,
}) => {
  const isFormComplete = digits.every(d => d !== '');

  return (
    <div className="min-h-screen bg-gradient-to-br from-brand-secondary/35 via-slate-50 to-white flex items-center justify-center p-4 font-sans antialiased">
      <title>Pré-Cadastro — Portal de Acesso</title>
      <meta name="description" content="Verificação de identidade e liberação de acesso às catracas físicas" />
      <div className="w-full max-w-md bg-white/90 backdrop-blur-md rounded-3xl shadow-xl shadow-brand-primary/5 border border-white/60 p-8 flex flex-col justify-between min-h-[580px] transition-all">
        
        {/* Logo da Clínica */}
        <div className="text-center">
          <img 
            src="/Logo.png" 
            alt="Logo" 
            className="h-14 w-auto mx-auto mb-6 object-contain"
            onError={(e) => {
              e.currentTarget.src = 'https://placehold.co/180x60/feb56c/ffffff?text=Portal';
            }}
          />
          
          {/* Badge de Segurança */}
          <div className="inline-flex items-center gap-1.5 bg-brand-secondary/30 border border-brand-primary/10 rounded-full px-3 py-1 mb-4">
            <ShieldCheck className="w-4 h-4 text-brand-primary-dark" />
            <span className="text-xs text-brand-primary-dark font-semibold">Verificação de Identidade</span>
          </div>

          <h2 className="text-xl font-extrabold text-slate-800 tracking-tight">Desbloquear Acesso</h2>
          
          {/* Mensagem do desafio */}
          <p className="text-sm text-slate-500 mt-2.5 leading-relaxed max-w-[320px] mx-auto">
            Para sua segurança e desbloqueio dos seus QR Codes de entrada, informe os{' '}
            <b>4 últimos dígitos</b> do número de telefone que recebeu a mensagem de confirmação.
          </p>
        </div>

        {/* Formulário de Desafio */}
        <form onSubmit={onUnlock} className="mt-8 flex-1 flex flex-col justify-between">
          <div className="space-y-4">
            
            {/* Inputs dos 4 dígitos separados para otimização mobile */}
            <div className="flex justify-center gap-3.5">
              {digits.map((digit, index) => (
                <input
                  key={index}
                  ref={inputRefs[index]}
                  id={`digit-input-${index}`}
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  maxLength={1}
                  value={digit}
                  onChange={(e) => onDigitChange(index, e.target.value)}
                  onKeyDown={(e) => onDigitKeyDown(index, e)}
                  placeholder="•"
                  disabled={challengeLoading}
                  className={`w-14 h-16 text-center text-2xl font-extrabold text-slate-800 border-2 rounded-2xl focus:ring-4 bg-slate-50/50 transition-all font-mono placeholder:text-slate-300 disabled:opacity-50 disabled:cursor-wait ${
                    challengeError
                      ? 'border-red-400 focus:border-red-500 focus:ring-red-100'
                      : 'border-slate-200 focus:border-brand-primary focus:ring-brand-primary/10'
                  }`}
                />
              ))}
            </div>

            {/* Mensagem de erro do desafio */}
            {challengeError && (
              <div className="flex items-start gap-2 bg-red-50 border border-red-200 rounded-xl px-3 py-2.5 text-red-700">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                <p className="text-xs font-semibold leading-relaxed">{challengeError}</p>
              </div>
            )}

            {/* Ícone de cadeado + texto de orientação */}
            <p className="text-xs text-slate-400 text-center flex items-center justify-center gap-1.5">
              <Lock className="w-3.5 h-3.5 text-brand-primary" />
              Os QR Codes são exibidos somente após a verificação
            </p>
          </div>

          {/* Botão de Desbloqueio */}
          <button
            type="submit"
            id="unlock-access-button"
            disabled={!isFormComplete || challengeLoading}
            className={`w-full py-4 px-6 rounded-2xl font-bold tracking-wide transition-all duration-300 mt-10 shadow-lg flex items-center justify-center gap-2 ${
              isFormComplete && !challengeLoading
                ? 'bg-gradient-to-r from-brand-primary to-brand-primary-dark hover:from-brand-primary hover:to-brand-primary-dark shadow-brand-primary/25 cursor-pointer active:scale-[0.98] text-white' 
                : 'bg-slate-200 text-slate-400 shadow-none cursor-not-allowed'
            }`}
          >
            {challengeLoading ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin" />
                Verificando...
              </>
            ) : (
              <>
                Desbloquear Acesso
                <ArrowRight className="w-4 h-4" />
              </>
            )}
          </button>
        </form>
      </div>
    </div>
  );
};
