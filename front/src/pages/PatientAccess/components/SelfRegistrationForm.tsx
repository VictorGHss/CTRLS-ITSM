import React, { useState } from 'react';
import { User, CreditCard, Phone, Calendar, UserPlus, ArrowRight, Search, CheckCircle2, AlertCircle, ShieldCheck, Plus, Trash2 } from 'lucide-react';
import type { ClinicTheme } from '../utils/clinicThemes';
import type { AccessCredential } from '../types';
import api from '../../../services/api';

interface CompanionEntry {
  id: string;
  name: string;
  cpf: string;
  birthDate: string;
}

interface SelfRegistrationFormProps {
  clinicTheme: ClinicTheme;
  onSuccess: (credentials: AccessCredential[], authInfo?: { token?: string; phoneDigits?: string }) => void;
}

export const SelfRegistrationForm: React.FC<SelfRegistrationFormProps> = ({ clinicTheme, onSuccess }) => {
  const [activeTab, setActiveTab] = useState<'register' | 'lookup'>('register');

  // Campos de Cadastro do Paciente Titular
  const [name, setName] = useState('');
  const [cpf, setCpf] = useState('');
  const [phone, setPhone] = useState('');
  const [birthDate, setBirthDate] = useState('');

  // Acompanhantes (Múltiplos / Ilimitados)
  const [hasCompanion, setHasCompanion] = useState(false);
  const [companions, setCompanions] = useState<CompanionEntry[]>([
    { id: 'comp-1', name: '', cpf: '', birthDate: '' }
  ]);

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

  const addCompanionField = () => {
    setCompanions(prev => [
      ...prev,
      { id: `comp-${Date.now()}`, name: '', cpf: '', birthDate: '' }
    ]);
  };

  const removeCompanionField = (id: string) => {
    setCompanions(prev => {
      const filtered = prev.filter(c => c.id !== id);
      if (filtered.length === 0) {
        return [{ id: `comp-${Date.now()}`, name: '', cpf: '', birthDate: '' }];
      }
      return filtered;
    });
  };

  const updateCompanion = (id: string, field: keyof CompanionEntry, value: string) => {
    setCompanions(prev => prev.map(c => c.id === id ? { ...c, [field]: value } : c));
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

    // Validação dos acompanhantes (se selecionado)
    let companionsPayload: Array<{ name: string; cpf: string; birthDate?: string }> = [];
    if (hasCompanion) {
      if (companions.length === 0) {
        setErrorMessage('Por favor, informe os dados do acompanhante ou desmarque a opção.');
        return;
      }
      for (let i = 0; i < companions.length; i++) {
        const c = companions[i];
        if (!c.name.trim()) {
          setErrorMessage(`Por favor, informe o nome completo do Acompanhante #${i + 1}.`);
          return;
        }
        const cleanCompCpf = c.cpf.replace(/\D/g, '');
        if (cleanCompCpf.length !== 11) {
          setErrorMessage(`Por favor, informe o CPF com 11 dígitos do Acompanhante #${i + 1} (${c.name}).`);
          return;
        }
        companionsPayload.push({
          name: c.name.trim(),
          cpf: cleanCompCpf,
          birthDate: c.birthDate || undefined
        });
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
        companion: companionsPayload.length > 0 ? companionsPayload[0] : undefined,
        companions: companionsPayload.length > 0 ? companionsPayload : undefined
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
        setErrorMessage('Nenhum cadastro ativo encontrado para este CPF hoje. Realize um novo cadastro acima.');
      }
    } catch (err: any) {
      console.error('[SelfRegistration] Erro ao consultar CPF:', err);
      const msg = err.response?.data?.message || 'Erro ao consultar CPF. Tente novamente.';
      setErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md mx-auto">
      {/* Header com Logo e Subtítulo */}
      <div className="text-center mb-6">
        <div className="inline-flex items-center justify-center p-3 bg-white rounded-3xl shadow-sm border border-slate-100 mb-3">
          <img
            src={clinicTheme.logoUrl}
            alt={clinicTheme.name}
            className="h-10 object-contain"
            onError={(e) => {
              (e.target as HTMLElement).style.display = 'none';
            }}
          />
        </div>
        <h2 className="text-xl font-black text-slate-800 tracking-tight">
          {clinicTheme.name}
        </h2>
        <p className="text-xs font-semibold text-slate-500 mt-0.5">
          {clinicTheme.subtitle}
        </p>
      </div>

      {/* Card Principal */}
      <div className="bg-white rounded-3xl p-6 shadow-xl shadow-slate-200/50 border border-slate-100 relative overflow-hidden">
        {/* Barra superior de abas */}
        <div className="flex bg-slate-100/80 p-1 rounded-2xl">
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
            <UserPlus className="w-3.5 h-3.5" style={{ color: activeTab === 'register' ? clinicTheme.primaryColor : undefined }} />
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
                className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 transition-all"
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
                className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 transition-all font-mono"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                  <Calendar className="w-3.5 h-3.5 text-slate-400" />
                  Nascimento <span className="text-[10px] text-slate-400 font-normal">(opcional)</span>
                </label>
                <input
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  value={birthDate}
                  onChange={(e) => setBirthDate(maskDate(e.target.value))}
                  placeholder="DD/MM/AAAA"
                  maxLength={10}
                  className="w-full px-3.5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 transition-all"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                  <Phone className="w-3.5 h-3.5 text-slate-400" />
                  WhatsApp <span className="text-[10px] text-slate-400 font-normal">(opcional)</span>
                </label>
                <input
                  type="tel"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  value={phone}
                  onChange={(e) => setPhone(maskPhone(e.target.value))}
                  placeholder="(42) 99999-9999"
                  maxLength={15}
                  className="w-full px-3.5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 transition-all font-mono"
                />
              </div>
            </div>

            {/* Checkbox e Lista de Acompanhantes (Múltiplos / Ilimitados) */}
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
                    <p className="text-[11px] font-bold uppercase tracking-wider" style={{ color: clinicTheme.primaryDarkColor }}>
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

                  {companions.map((comp, idx) => (
                    <div key={comp.id} className="p-3 bg-white rounded-xl border border-slate-200/80 shadow-xs space-y-2 relative">
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
                          className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-1 transition-all"
                        />
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        <input
                          type="text"
                          inputMode="numeric"
                          pattern="[0-9]*"
                          value={comp.cpf}
                          onChange={(e) => updateCompanion(comp.id, 'cpf', maskCpf(e.target.value))}
                          placeholder="CPF *"
                          maxLength={14}
                          className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-1 transition-all font-mono"
                        />
                        <input
                          type="text"
                          inputMode="numeric"
                          pattern="[0-9]*"
                          value={comp.birthDate}
                          onChange={(e) => updateCompanion(comp.id, 'birthDate', maskDate(e.target.value))}
                          placeholder="Nasc. (opcional)"
                          maxLength={10}
                          className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-1 transition-all"
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Botão de Envio */}
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
                className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none focus:ring-2 transition-all font-mono"
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
