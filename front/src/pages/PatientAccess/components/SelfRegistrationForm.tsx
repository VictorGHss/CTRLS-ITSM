import React, { useState } from 'react';
import { User, CreditCard, Phone, Calendar, UserPlus, ArrowRight, Search, CheckCircle2, AlertCircle, ShieldCheck } from 'lucide-react';
import type { ClinicTheme } from '../utils/clinicThemes';
import type { AccessCredential } from '../types';
import api from '../../../services/api';

interface SelfRegistrationFormProps {
  clinicTheme: ClinicTheme;
  onSuccess: (credentials: AccessCredential[], authInfo?: { token?: string; phoneDigits?: string }) => void;
}

export const SelfRegistrationForm: React.FC<SelfRegistrationFormProps> = ({ clinicTheme, onSuccess }) => {
  const [activeTab, setActiveTab] = useState<'register' | 'lookup'>('register');

  // Campos de Cadastro
  const [name, setName] = useState('');
  const [cpf, setCpf] = useState('');
  const [phone, setPhone] = useState('');
  const [birthDate, setBirthDate] = useState('');

  // Acompanhante
  const [hasCompanion, setHasCompanion] = useState(false);
  const [companionName, setCompanionName] = useState('');
  const [companionCpf, setCompanionCpf] = useState('');
  const [companionBirthDate, setCompanionBirthDate] = useState('');

  // Campos de Consulta
  const [lookupCpf, setLookupCpf] = useState('');

  // Estados de Controle
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Máscaras de entrada
  const maskCpf = (val: string) => {
    return val
      .replace(/\D/g, '')
      .slice(0, 11)
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
  };

  const maskPhone = (val: string) => {
    const raw = val.replace(/\D/g, '').slice(0, 11);
    if (raw.length <= 10) {
      return raw.replace(/(\d{2})(\d{4})(\d{0,4})/, '($1) $2-$3').replace(/-$/, '');
    }
    return raw.replace(/(\d{2})(\d{5})(\d{0,4})/, '($1) $2-$3').replace(/-$/, '');
  };

  const maskDate = (val: string) => {
    return val
      .replace(/\D/g, '')
      .slice(0, 8)
      .replace(/(\d{2})(\d)/, '$1/$2')
      .replace(/(\d{2})(\d)/, '$1/$2');
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const cleanCpf = cpf.replace(/\D/g, '');
    if (cleanCpf.length !== 11) {
      setErrorMessage('Por favor, informe um CPF válido com 11 dígitos.');
      return;
    }

    if (!name.trim() || name.trim().length < 3) {
      setErrorMessage('Por favor, informe seu nome completo.');
      return;
    }

    if (hasCompanion) {
      if (!companionName.trim()) {
        setErrorMessage('Por favor, informe o nome do acompanhante.');
        return;
      }
      const cleanCompCpf = companionCpf.replace(/\D/g, '');
      if (cleanCompCpf && cleanCompCpf.length !== 11) {
        setErrorMessage('O CPF do acompanhante deve conter 11 dígitos (ou ser deixado em branco).');
        return;
      }
    }

    setLoading(true);

    try {
      const payload = {
        name: name.trim(),
        cpf: cleanCpf,
        phone: phone.replace(/\D/g, ''),
        birthDate: birthDate,
        clinic: clinicTheme.id,
        companion: hasCompanion && companionName.trim() ? {
          name: companionName.trim(),
          cpf: companionCpf.replace(/\D/g, '') || undefined,
          birthDate: companionBirthDate || undefined,
        } : undefined
      };

      const response = await api.post<AccessCredential[]>(
        '/v1/access/self-registration',
        payload,
        {
          headers: {
            'X-Skip-Interceptor': 'true'
          }
        }
      );

      if (response.data && response.data.length > 0) {
        onSuccess(response.data);
      } else {
        setErrorMessage('Não foi possível gerar as credenciais de acesso. Tente novamente.');
      }
    } catch (err: any) {
      console.error('[SelfRegistration] Erro ao submeter cadastro:', err);
      const msg = err.response?.data?.message || 'Erro ao processar o check-in. Verifique os dados e tente novamente.';
      setErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleLookupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const cleanCpf = lookupCpf.replace(/\D/g, '');
    if (cleanCpf.length !== 11) {
      setErrorMessage('Por favor, informe um CPF válido com 11 dígitos.');
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
        setErrorMessage('Nenhum cadastro ativo encontrado para este CPF hoje.');
      }
    } catch (err: any) {
      console.error('[SelfRegistration] Erro ao consultar CPF:', err);
      const msg = err.response?.data?.message || 'Nenhum cadastro ativo encontrado para este CPF hoje.';
      setErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md mx-auto space-y-6">
      {/* Cabeçalho do Card */}
      <div className="bg-white rounded-3xl shadow-xl shadow-slate-200/50 border border-slate-100 p-6 sm:p-8 text-center relative overflow-hidden">
        <div 
          className="absolute top-0 left-0 right-0 h-2 bg-gradient-to-r"
          style={{
            backgroundImage: `linear-gradient(to right, ${clinicTheme.primaryColor}, ${clinicTheme.primaryDarkColor})`
          }}
        />

        <h1 className="text-xl sm:text-2xl font-black text-slate-800 tracking-tight pt-1">
          Pré-Cadastro
        </h1>
        <p className="text-xs sm:text-sm text-slate-500 mt-1 font-medium leading-relaxed">
          Preencha seus dados para liberação da catraca física e emissão do QR Code de acesso.
        </p>

        {/* Seletor de Abas */}
        <div className="flex rounded-2xl bg-slate-100 p-1 mt-5 border border-slate-200/60">
          <button
            type="button"
            onClick={() => {
              setActiveTab('register');
              setErrorMessage(null);
            }}
            className={`flex-1 py-2.5 px-3 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'register'
                ? 'bg-white text-slate-800 shadow-sm'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <UserPlus className="w-3.5 h-3.5" />
            Novo Cadastro
          </button>
          <button
            type="button"
            onClick={() => {
              setActiveTab('lookup');
              setErrorMessage(null);
            }}
            className={`flex-1 py-2.5 px-3 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'lookup'
                ? 'bg-white text-slate-800 shadow-sm'
                : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            <Search className="w-3.5 h-3.5" />
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

        {/* Formulário: Novo Cadastro */}
        {activeTab === 'register' && (
          <form onSubmit={handleRegisterSubmit} className="mt-6 space-y-4 text-left">
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                <User className="w-3.5 h-3.5 text-slate-400" />
                Nome Completo do Paciente *
              </label>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Ex: Maria dos Santos"
                className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 transition-all"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                <CreditCard className="w-3.5 h-3.5 text-slate-400" />
                CPF do Paciente *
              </label>
              <input
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                required
                value={cpf}
                onChange={(e) => setCpf(maskCpf(e.target.value))}
                placeholder="000.000.000-00"
                maxLength={14}
                className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 transition-all"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                  <Calendar className="w-3.5 h-3.5 text-slate-400" />
                  Nascimento
                </label>
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  value={birthDate}
                  onChange={(e) => setBirthDate(maskDate(e.target.value))}
                  placeholder="DD/MM/AAAA"
                  maxLength={10}
                  className="w-full px-3.5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 transition-all"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                  <Phone className="w-3.5 h-3.5 text-slate-400" />
                  WhatsApp
                </label>
                <input
                  type="tel"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  value={phone}
                  onChange={(e) => setPhone(maskPhone(e.target.value))}
                  placeholder="(42) 99999-9999"
                  maxLength={15}
                  className="w-full px-3.5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 transition-all"
                />
              </div>
            </div>

            {/* Checkbox Acompanhante */}
            <div className="pt-2 border-t border-slate-100">
              <label className="flex items-center gap-2.5 cursor-pointer py-1.5 select-none">
                <input
                  type="checkbox"
                  checked={hasCompanion}
                  onChange={(e) => setHasCompanion(e.target.checked)}
                  className="w-4 h-4 rounded text-rose-600 focus:ring-rose-500 border-slate-300"
                />
                <span className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                  <UserPlus className="w-3.5 h-3.5 text-rose-600" />
                  Vou levar um acompanhante
                </span>
              </label>

              {hasCompanion && (
                <div className="mt-3 p-4 bg-rose-50/40 border border-rose-100 rounded-2xl space-y-3 animate-in fade-in duration-200">
                  <p className="text-[11px] font-bold text-rose-900 uppercase tracking-wider">
                    Dados do Acompanhante
                  </p>
                  <div>
                    <input
                      type="text"
                      value={companionName}
                      onChange={(e) => setCompanionName(e.target.value)}
                      placeholder="Nome do Acompanhante *"
                      className="w-full px-3.5 py-2.5 bg-white border border-rose-200 rounded-xl text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      type="text"
                      inputMode="numeric"
                      pattern="[0-9]*"
                      value={companionCpf}
                      onChange={(e) => setCompanionCpf(maskCpf(e.target.value))}
                      placeholder="CPF (opcional)"
                      maxLength={14}
                      className="w-full px-3.5 py-2.5 bg-white border border-rose-200 rounded-xl text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
                    />
                    <input
                      type="text"
                      inputMode="numeric"
                      pattern="[0-9]*"
                      value={companionBirthDate}
                      onChange={(e) => setCompanionBirthDate(maskDate(e.target.value))}
                      placeholder="Nascimento"
                      maxLength={10}
                      className="w-full px-3.5 py-2.5 bg-white border border-rose-200 rounded-xl text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
                    />
                  </div>
                </div>
              )}
            </div>

            {/* Botão de Envio */}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-4 px-6 rounded-2xl text-white font-extrabold text-sm shadow-lg shadow-rose-900/20 hover:opacity-95 active:scale-[0.98] transition-all flex items-center justify-center gap-2 mt-4 disabled:opacity-60 cursor-pointer"
              style={{
                backgroundImage: `linear-gradient(135deg, ${clinicTheme.primaryColor}, ${clinicTheme.primaryDarkColor})`
              }}
            >
              {loading ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  <span>Liberando Acesso...</span>
                </>
              ) : (
                <>
                  <ShieldCheck className="w-4 h-4" />
                  <span>Emitir Meu Acesso (Catracas)</span>
                  <ArrowRight className="w-4 h-4 ml-0.5" />
                </>
              )}
            </button>
          </form>
        )}

        {/* Formulário: Consulta por CPF */}
        {activeTab === 'lookup' && (
          <form onSubmit={handleLookupSubmit} className="mt-6 space-y-4 text-left">
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                <CreditCard className="w-3.5 h-3.5 text-slate-400" />
                Digite seu CPF cadastrado
              </label>
              <input
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                required
                value={lookupCpf}
                onChange={(e) => setLookupCpf(maskCpf(e.target.value))}
                placeholder="000.000.000-00"
                maxLength={14}
                className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500 transition-all"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-4 px-6 rounded-2xl text-white font-extrabold text-sm shadow-lg shadow-rose-900/20 hover:opacity-95 active:scale-[0.98] transition-all flex items-center justify-center gap-2 mt-4 disabled:opacity-60 cursor-pointer"
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
        )}

        <div className="mt-6 pt-4 border-t border-slate-100 text-center">
          <p className="text-[11px] text-slate-400 font-semibold flex items-center justify-center gap-1.5">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
            Acesso integrado às catracas do Edifício Inovare
          </p>
        </div>
      </div>
    </div>
  );
};
