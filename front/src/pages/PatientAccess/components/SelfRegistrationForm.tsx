import React, { useState, useCallback, useMemo } from 'react';
import { User, CreditCard, Calendar, UserPlus, ArrowRight, Search, CheckCircle2, AlertCircle, ShieldCheck, Sparkles, Clock } from 'lucide-react';
import type { ClinicTheme, DoctorSuggestion } from '../utils/clinicThemes';
import { resolveDoctorLocation } from '../utils/clinicThemes';
import type { AccessCredential, CompanionEntry, FeegowAppointmentItem } from '../types';
import api from '../../../services/api';
import { getApiErrorMessage } from '../../../lib/apiError';
import { isValidCpf } from '../utils/cpfValidator';
import { maskCpf } from '../utils/masks';
import { useDoctorAutocomplete } from '../hooks/useDoctorAutocomplete';
import { useFeegowLookup } from '../hooks/useFeegowLookup';
import { FeegowAppointmentPicker } from './FeegowAppointmentPicker';
import { DoctorAutocompleteInput } from './DoctorAutocompleteInput';
import { CompanionFormList } from './CompanionFormList';

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

  // Estados de Data da Consulta / Visita
  const today = useMemo(() => new Date(), []);
  const tomorrow = useMemo(() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d;
  }, []);

  const toISODate = (d: Date) => d.toLocaleDateString('sv-SE'); // YYYY-MM-DD
  const formatPillDate = (d: Date) => `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;

  const [dateSelection, setDateSelection] = useState<'today' | 'tomorrow' | 'custom'>('today');
  const [customDate, setCustomDate] = useState<string>('');

  // Modo Manual vs Feegow
  const [manualDoctorMode, setManualDoctorMode] = useState(false);

  // Hook de Autocomplete de Médico
  const {
    doctorInput,
    setDoctorInput,
    selectedLocation,
    setSelectedLocation,
    isDoctorDropdownOpen,
    setIsDoctorDropdownOpen,
    doctorDropdownRef,
    filteredDoctors,
    handleSelectDoctor
  } = useDoctorAutocomplete();

  // Seleção de Agendamento vindo do Feegow
  const handleFeegowAppointmentSelected = useCallback((appt: FeegowAppointmentItem) => {
    setManualDoctorMode(false);
    if (appt.doctorName) {
      setDoctorInput(appt.doctorName);
      setSelectedLocation(resolveDoctorLocation(appt.doctorName, clinicTheme.floorInfo));
    }
    if (appt.isToday) {
      setDateSelection('today');
    } else {
      const tomorrowStr = toISODate(tomorrow);
      if (appt.date === tomorrowStr) {
        setDateSelection('tomorrow');
      } else {
        setDateSelection('custom');
        setCustomDate(appt.date);
      }
    }
  }, [clinicTheme.floorInfo, setDoctorInput, setSelectedLocation, tomorrow]);

  // Hook de Consulta Inteligente no Feegow
  const {
    isSearchingFeegow,
    feegowLookupDone,
    feegowAppointments,
    selectedFeegowApptId,
    setSelectedFeegowApptId,
    selectFeegowAppointment
  } = useFeegowLookup({
    cpf,
    clinicId: clinicTheme.id,
    name,
    phone,
    birthDate,
    manualDoctorMode,
    setName,
    setPhone,
    setBirthDate,
    onAppointmentSelected: handleFeegowAppointmentSelected
  });

  // Acompanhantes
  const [hasCompanion, setHasCompanion] = useState(false);
  const [companions, setCompanions] = useState<CompanionEntry[]>([
    { id: 'comp-1', name: '', cpf: '', birthDate: '' }
  ]);

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

  // Campos de Consulta por CPF
  const [lookupCpf, setLookupCpf] = useState('');

  // Estados de Controle de Submissão
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const cleanCpf = cpf.replace(/\D/g, '');
    if (cleanCpf.length !== 11 || !isValidCpf(cleanCpf)) {
      setErrorMessage('CPF do paciente titular inválido perante a Receita Federal. Por favor, confira os números digitados.');
      return;
    }

    if (!name.trim() || name.trim().length < 3) {
      setErrorMessage('Por favor, informe seu nome completo.');
      return;
    }

    // Validação dos acompanhantes (se selecionado)
    const companionsPayload: Array<{ name: string; cpf: string; birthDate?: string }> = [];
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
        if (cleanCompCpf.length !== 11 || !isValidCpf(cleanCompCpf)) {
          setErrorMessage(`O CPF do Acompanhante #${i + 1} (${c.name}) é inválido perante a Receita Federal. Por favor, confira os números digitados.`);
          return;
        }

        // Previne que o acompanhante tenha o mesmo CPF do paciente titular
        if (cleanCompCpf === cleanCpf) {
          setErrorMessage(`O CPF do Acompanhante #${i + 1} não pode ser o mesmo do paciente titular. Cada pessoa precisa do seu próprio CPF para liberar a catraca.`);
          return;
        }

        // Previne que o acompanhante tenha o mesmo nome do paciente titular
        if (c.name.trim().toUpperCase() === name.trim().toUpperCase() && name.trim().length >= 3) {
          setErrorMessage(`O acompanhante #${i + 1} não pode ter o mesmo nome do paciente titular.`);
          return;
        }

        // Previne acompanhantes duplicados entre si
        if (companionsPayload.some(cp => cp.cpf === cleanCompCpf)) {
          setErrorMessage(`Foram informados acompanhantes duplicados com o mesmo CPF. Cada pessoa deve ter um CPF único.`);
          return;
        }

        companionsPayload.push({
          name: c.name.trim(),
          cpf: cleanCompCpf,
          birthDate: c.birthDate || undefined
        });
      }
    }

    let finalVisitDate = toISODate(today);
    if (dateSelection === 'tomorrow') {
      finalVisitDate = toISODate(tomorrow);
    } else if (dateSelection === 'custom') {
      if (!customDate) {
        setErrorMessage('Por favor, selecione a data da consulta.');
        return;
      }
      finalVisitDate = customDate;
    }

    const finalDoctorName = doctorInput.trim() || undefined;

    setLoading(true);

    try {
      const payload = {
        name: name.trim(),
        cpf: cleanCpf,
        phone: phone.replace(/\D/g, ''),
        birthDate: birthDate,
        clinic: clinicTheme.id,
        visitDate: finalVisitDate,
        doctorName: finalDoctorName,
        appointmentId: (!manualDoctorMode && selectedFeegowApptId) ? selectedFeegowApptId : undefined,
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
    } catch (err: unknown) {
      console.error('[SelfRegistration] Erro ao submeter cadastro:', err);
      const msg = getApiErrorMessage(err, 'Erro ao processar o check-in. Verifique os dados e tente novamente.');
      setErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleLookupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const cleanCpf = lookupCpf.replace(/\D/g, '');
    if (cleanCpf.length !== 11 || !isValidCpf(cleanCpf)) {
      setErrorMessage('CPF inválido perante a Receita Federal. Por favor, confira os números digitados.');
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
        setErrorMessage(
          'Nenhum agendamento ativo encontrado para este CPF nos próximos 7 dias. Se sua consulta for hoje ou se você veio para uma visita, emita seu QR Code na aba "Novo Cadastro" acima.'
        );
      }
    } catch (err: unknown) {
      console.error('[SelfRegistration] Erro ao consultar CPF:', err);
      const msg = getApiErrorMessage(err, 'Não foi possível consultar seu CPF no momento. Tente novamente.');
      setErrorMessage(msg);
    } finally {
      setLoading(false);
    }
  };

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
            {/* 1. CPF do Paciente no topo com busca instantânea no Feegow */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                  <CreditCard className="w-3.5 h-3.5 text-slate-400" />
                  CPF do Paciente *
                </label>
                {isSearchingFeegow ? (
                  <span 
                    className="text-[11px] font-bold flex items-center gap-1 animate-pulse"
                    style={{ color: clinicTheme.primaryDarkColor }}
                  >
                    <Sparkles className="w-3 h-3 animate-spin" style={{ color: clinicTheme.primaryColor }} />
                    Buscando no Feegow...
                  </span>
                ) : feegowLookupDone && feegowAppointments.length > 0 && !manualDoctorMode ? (
                  <span 
                    className="text-[11px] font-bold flex items-center gap-1"
                    style={{ color: clinicTheme.primaryDarkColor }}
                  >
                    <CheckCircle2 className="w-3 h-3" style={{ color: clinicTheme.primaryColor }} />
                    Consulta localizada
                  </span>
                ) : feegowLookupDone ? (
                  <span className="text-[11px] font-bold text-slate-500 flex items-center gap-1">
                    <CheckCircle2 className="w-3 h-3 text-slate-400" />
                    Dados sincronizados
                  </span>
                ) : null}
              </div>
              <input
                type="text"
                inputMode="numeric"
                required
                value={cpf}
                onChange={(e) => setCpf(maskCpf(e.target.value))}
                placeholder="000.000.000-00"
                maxLength={14}
                className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all font-mono form-input-themed"
              />
            </div>

            {/* Card de Consulta Localizada no Feegow */}
            {!manualDoctorMode && (
              <FeegowAppointmentPicker
                clinicTheme={clinicTheme}
                appointments={feegowAppointments}
                selectedAppointmentId={selectedFeegowApptId}
                onSelectAppointment={selectFeegowAppointment}
                onSwitchToManual={() => {
                  setManualDoctorMode(true);
                  setSelectedFeegowApptId(null);
                }}
              />
            )}

            {/* Modo Manual de Consulta / Data */}
            {(feegowAppointments.length === 0 || manualDoctorMode) && (
              <div className="space-y-4">
                {manualDoctorMode && feegowAppointments.length > 0 && (
                  <div className="flex items-center justify-between p-2 rounded-xl bg-slate-50 border border-slate-200 text-xs">
                    <span className="text-slate-600 font-medium">Preenchendo manualmente</span>
                    <button
                      type="button"
                      onClick={() => {
                        const chosen = feegowAppointments.find(a => a.isToday) || feegowAppointments[0];
                        selectFeegowAppointment(chosen);
                      }}
                      className="text-[11px] font-bold transition-colors flex items-center gap-1 cursor-pointer"
                      style={{ color: clinicTheme.primaryDarkColor }}
                    >
                      <Clock className="w-3 h-3" style={{ color: clinicTheme.primaryColor }} />
                      Usar consulta do Feegow
                    </button>
                  </div>
                )}

                {/* Seleção de Data da Consulta / Atendimento */}
                <div>
                  <label className="text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                    <Calendar className="w-3.5 h-3.5 text-slate-400" />
                    Data da Consulta / Atendimento *
                  </label>
                  <div className="grid grid-cols-3 gap-2">
                    <button
                      type="button"
                      onClick={() => setDateSelection('today')}
                      className="py-2 px-1 rounded-xl text-xs font-bold transition-all border text-center cursor-pointer"
                      style={dateSelection === 'today' ? {
                        backgroundColor: clinicTheme.primaryColor,
                        borderColor: clinicTheme.primaryDarkColor,
                        color: '#ffffff',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
                      } : {
                        backgroundColor: '#f8fafc',
                        borderColor: '#e2e8f0',
                        color: '#334155'
                      }}
                    >
                      Hoje ({formatPillDate(today)})
                    </button>
                    <button
                      type="button"
                      onClick={() => setDateSelection('tomorrow')}
                      className="py-2 px-1 rounded-xl text-xs font-bold transition-all border text-center cursor-pointer"
                      style={dateSelection === 'tomorrow' ? {
                        backgroundColor: clinicTheme.primaryColor,
                        borderColor: clinicTheme.primaryDarkColor,
                        color: '#ffffff',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
                      } : {
                        backgroundColor: '#f8fafc',
                        borderColor: '#e2e8f0',
                        color: '#334155'
                      }}
                    >
                      Amanhã ({formatPillDate(tomorrow)})
                    </button>
                    <button
                      type="button"
                      onClick={() => setDateSelection('custom')}
                      className="py-2 px-1 rounded-xl text-xs font-bold transition-all border text-center cursor-pointer"
                      style={dateSelection === 'custom' ? {
                        backgroundColor: clinicTheme.primaryColor,
                        borderColor: clinicTheme.primaryDarkColor,
                        color: '#ffffff',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
                      } : {
                        backgroundColor: '#f8fafc',
                        borderColor: '#e2e8f0',
                        color: '#334155'
                      }}
                    >
                      Outra data
                    </button>
                  </div>

                  {dateSelection === 'custom' && (
                    <div className="mt-2.5">
                      <input
                        type="date"
                        required
                        min={toISODate(today)}
                        value={customDate}
                        onChange={(e) => setCustomDate(e.target.value)}
                        className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-2xl text-xs font-bold text-slate-800 focus:bg-white focus:outline-none transition-all form-input-themed"
                      />
                    </div>
                  )}
                </div>

                {/* Autocomplete de Médico */}
                <DoctorAutocompleteInput
                  clinicTheme={clinicTheme}
                  doctorInput={doctorInput}
                  setDoctorInput={setDoctorInput}
                  selectedLocation={selectedLocation}
                  setSelectedLocation={setSelectedLocation}
                  isDoctorDropdownOpen={isDoctorDropdownOpen}
                  setIsDoctorDropdownOpen={setIsDoctorDropdownOpen}
                  doctorDropdownRef={doctorDropdownRef}
                  filteredDoctors={filteredDoctors}
                  handleSelectDoctor={(doc: DoctorSuggestion) => handleSelectDoctor(doc)}
                />
              </div>
            )}

            {/* 2. Nome Completo do Paciente */}
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
                className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all uppercase form-input-themed"
              />
            </div>

            {/* Lista de Acompanhantes */}
            <CompanionFormList
              clinicTheme={clinicTheme}
              hasCompanion={hasCompanion}
              setHasCompanion={setHasCompanion}
              companions={companions}
              addCompanionField={addCompanionField}
              removeCompanionField={removeCompanionField}
              updateCompanion={updateCompanion}
              patientName={name}
              patientCpf={cpf}
            />

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
                required
                value={lookupCpf}
                onChange={(e) => setLookupCpf(maskCpf(e.target.value))}
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
