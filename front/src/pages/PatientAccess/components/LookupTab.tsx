import React, { useState } from 'react';
import { CreditCard, Search, ArrowRight } from 'lucide-react';
import type { ClinicTheme } from '../utils/clinicThemes';
import type { AccessCredential } from '../types';
import api from '../../../services/api';
import { getApiErrorMessage } from '../../../lib/apiError';
import { isValidCpf } from '../utils/cpfValidator';
import { maskCpf } from '../utils/masks';

interface LookupTabProps {
  clinicTheme: ClinicTheme;
  onSuccess: (credentials: AccessCredential[]) => void;
  setParentErrorMessage: (msg: string | null) => void;
}

export const LookupTab: React.FC<LookupTabProps> = ({
  clinicTheme,
  onSuccess,
  setParentErrorMessage
}) => {
  const [lookupCpf, setLookupCpf] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLookupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setParentErrorMessage(null);

    const cleanCpf = lookupCpf.replace(/\D/g, '');
    if (cleanCpf.length !== 11 || !isValidCpf(cleanCpf)) {
      setParentErrorMessage('CPF inválido perante a Receita Federal. Por favor, confira os números digitados.');
      return;
    }

    setLoading(true);

    try {
      const response = await api.post<AccessCredential[]>(
        '/v1/access/lookup-by-cpf',
        {
          cpf: cleanCpf,
          clinic: clinicTheme.id
        },
        {
          headers: {
            'X-Skip-Interceptor': 'true'
          }
        }
      );

      if (response.data && response.data.length > 0) {
        onSuccess(response.data);
      } else {
        setParentErrorMessage(
          'Nenhum agendamento ativo encontrado para este CPF nos próximos 7 dias. Se sua consulta for hoje ou se você veio para uma visita, emita seu QR Code na aba "Novo Cadastro" acima.'
        );
      }
    } catch (err: unknown) {
      console.error('[LookupTab] Erro ao consultar CPF:', err);
      const msg = getApiErrorMessage(err, 'Não foi possível consultar seu CPF no momento. Tente novamente.');
      setParentErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleLookupSubmit} className="mt-6 space-y-4 text-left">
      <div>
        <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
          <CreditCard className="w-3.5 h-3.5 text-slate-400" />
          Digite seu CPF
        </label>
        <input
          type="text"
          inputMode="numeric"
          required
          value={lookupCpf}
          onChange={e => setLookupCpf(maskCpf(e.target.value))}
          placeholder="000.000.000-00"
          maxLength={14}
          className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all font-mono form-input-themed"
        />
      </div>

      <button
        type="submit"
        disabled={loading}
        className="w-full py-4 px-6 rounded-2xl text-white font-extrabold text-sm shadow-lg hover:opacity-95 active:scale-[0.98] transition-all flex items-center justify-center gap-2 mt-4 disabled:opacity-60 cursor-pointer"
        style={{
          backgroundImage: `linear-gradient(135deg, ${clinicTheme.primaryColor}, ${clinicTheme.primaryDarkColor})`
        }}
      >
        {loading ? (
          <>
            <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            <span>Buscando QR Code...</span>
          </>
        ) : (
          <>
            <Search className="w-4 h-4" />
            <span>Recuperar Meu Acesso</span>
            <ArrowRight className="w-4 h-4 ml-0.5" />
          </>
        )}
      </button>
    </form>
  );
};
