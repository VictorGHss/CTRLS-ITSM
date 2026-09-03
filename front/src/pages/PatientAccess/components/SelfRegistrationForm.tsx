import React, { useState, useRef, useMemo, useEffect } from 'react';
import { User, CreditCard, Phone, Calendar, UserPlus, ArrowRight, Search, CheckCircle2, AlertCircle, ShieldCheck, Plus, Trash2, Stethoscope, MapPin, X, Sparkles, Clock } from 'lucide-react';
import { type ClinicTheme, DOCTOR_SUGGESTIONS, type DoctorSuggestion, resolveDoctorLocation } from '../utils/clinicThemes';
import type { AccessCredential } from '../types';
import api from '../../../services/api';
import { getApiErrorMessage } from '../../../lib/apiError';

interface CompanionEntry {
  id: string;
  name: string;
  cpf: string;
  birthDate: string;
}

export interface FeegowAppointmentItem {
  appointmentId: string;
  doctorName: string;
  specialty: string;
  date: string;
  time: string;
  formattedDateTime: string;
  isToday: boolean;
  location?: string;
}

export interface FeegowLookupResponse {
  found: boolean;
  patientName?: string;
  birthDate?: string;
  phone?: string;
  appointments: FeegowAppointmentItem[];
  message?: string;
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

  // Estados de Data da Consulta / Visita
  const today = new Date();
  const tomorrow = new Date();
  tomorrow.setDate(today.getDate() + 1);

  const toISODate = (d: Date) => d.toLocaleDateString('sv-SE'); // YYYY-MM-DD
  const formatPillDate = (d: Date) => `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;

  const [dateSelection, setDateSelection] = useState<'today' | 'tomorrow' | 'custom'>('today');
  const [customDate, setCustomDate] = useState<string>('');

  // Médico ou Especialidade com Busca Autocomplete (ativado após digitar 3 caracteres)
  const [doctorInput, setDoctorInput] = useState<string>('');
  const [selectedLocation, setSelectedLocation] = useState<string | null>(null);
  const [isDoctorDropdownOpen, setIsDoctorDropdownOpen] = useState<boolean>(false);
  const doctorDropdownRef = useRef<HTMLDivElement>(null);

  // Estados de Busca Automática Inteligente no Feegow
  const [isSearchingFeegow, setIsSearchingFeegow] = useState(false);
  const [feegowLookupDone, setFeegowLookupDone] = useState(false);
  const [feegowAppointments, setFeegowAppointments] = useState<FeegowAppointmentItem[]>([]);
  const [selectedFeegowApptId, setSelectedFeegowApptId] = useState<string | null>(null);
  const [manualDoctorMode, setManualDoctorMode] = useState(false);

  // Seleciona um agendamento localizado no Feegow
  const selectFeegowAppointment = (appt: FeegowAppointmentItem) => {
    setSelectedFeegowApptId(appt.appointmentId);
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
  };

  // Efeito de busca automática no Feegow com debounce ao digitar os 11 dígitos do CPF
  useEffect(() => {
    const cleanCpf = cpf.replace(/\D/g, '');
    if (cleanCpf.length !== 11) {
      setFeegowAppointments([]);
      setSelectedFeegowApptId(null);
      setFeegowLookupDone(false);
      setManualDoctorMode(false);
      return;
    }

    let isMounted = true;
    const timer = setTimeout(async () => {
      setIsSearchingFeegow(true);
      try {
        const resp = await api.post<FeegowLookupResponse>(
          '/v1/access/feegow-lookup',
          {
            cpf: cleanCpf,
            clinic: clinicTheme.id
          },
          {
            headers: { 'X-Skip-Interceptor': 'true' }
          }
        );

        if (!isMounted) return;

        if (resp.data && resp.data.found) {
          setFeegowLookupDone(true);
          if (resp.data.patientName && (!name.trim() || !manualDoctorMode)) {
            setName(resp.data.patientName);
          }
          if (resp.data.phone && (!phone.trim() || !manualDoctorMode)) {
            setPhone(maskPhone(resp.data.phone));
          }
          if (resp.data.birthDate && (!birthDate.trim() || !manualDoctorMode)) {
            const rawBirth = resp.data.birthDate.trim();
            if (rawBirth.includes('-')) {
              const parts = rawBirth.split('-');
              if (parts.length === 3) {
                setBirthDate(`${parts[2]}/${parts[1]}/${parts[0]}`);
              } else {
                setBirthDate(maskDate(rawBirth));
              }
            } else {
              setBirthDate(maskDate(rawBirth));
            }
          }

          const appts = resp.data.appointments || [];
          setFeegowAppointments(appts);

          if (appts.length > 0 && !manualDoctorMode) {
            const chosen = appts.find(a => a.isToday) || appts[0];
            selectFeegowAppointment(chosen);
          }
        }
      } catch (err) {
        console.warn('[SelfRegistrationForm] Falha defensiva ao consultar Feegow:', err);
      } finally {
        if (isMounted) setIsSearchingFeegow(false);
      }
    }, 450);

    return () => {
      isMounted = false;
      clearTimeout(timer);
    };
  }, [cpf, clinicTheme.id]);

  // Fecha o dropdown ao clicar fora do componente
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (doctorDropdownRef.current && !doctorDropdownRef.current.contains(event.target as Node)) {
        setIsDoctorDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // Filtra as opções de médicos e especialidades quando há 3 ou mais caracteres
  const filteredDoctors = useMemo(() => {
    const q = doctorInput.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    if (q.length < 3) return [];

    return DOCTOR_SUGGESTIONS.filter(item => {
      const normName = item.name.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
      const normSpec = (item.specialty || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
      const normLoc = item.location.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
      return normName.includes(q) || normSpec.includes(q) || normLoc.includes(q);
    });
  }, [doctorInput]);

  const handleSelectDoctor = (doc: DoctorSuggestion) => {
    setDoctorInput(doc.name);
    setSelectedLocation(doc.location);
    setIsDoctorDropdownOpen(false);
  };

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
        setErrorMessage(
          'Nenhum agendamento ativo encontrado para este CPF nos próximos 7 dias. Se sua consulta for hoje ou se você veio para uma visita, emita seu QR Code na aba "Novo Cadastro" acima.'
        );
      }
    } catch (err: any) {
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
            {feegowAppointments.length > 0 && !manualDoctorMode && (
              <div 
                className="p-4 rounded-2xl border shadow-xs space-y-3 animate-in fade-in slide-in-from-top-2 duration-200"
                style={{
                  backgroundColor: `${clinicTheme.secondaryColor}20`,
                  borderColor: `${clinicTheme.primaryColor}35`
                }}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span 
                      className="w-6 h-6 rounded-full text-white flex items-center justify-center text-xs font-bold shadow-xs"
                      style={{ backgroundColor: clinicTheme.primaryColor }}
                    >
                      ✓
                    </span>
                    <div>
                      <span 
                        className="text-xs font-extrabold block leading-tight"
                        style={{ color: clinicTheme.primaryDarkColor }}
                      >
                        Consulta Localizada no Feegow!
                      </span>
                      <span className="text-[10.5px] text-slate-600 font-medium">
                        Médico, horário e local pré-selecionados
                      </span>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setManualDoctorMode(true);
                      setSelectedFeegowApptId(null);
                    }}
                    className="text-[11px] font-bold text-slate-500 hover:text-slate-800 underline decoration-slate-300 underline-offset-2 transition-colors cursor-pointer"
                  >
                    Alterar médico
                  </button>
                </div>

                <div className="space-y-2 pt-0.5">
                  {feegowAppointments.map((appt) => {
                    const isSelected = selectedFeegowApptId === appt.appointmentId;
                    const resolvedFloor = resolveDoctorLocation(appt.doctorName, clinicTheme.floorInfo);
                    return (
                      <div
                        key={appt.appointmentId}
                        onClick={() => selectFeegowAppointment(appt)}
                        className="p-3 rounded-xl border transition-all cursor-pointer flex flex-col gap-1.5"
                        style={isSelected ? {
                          backgroundColor: '#ffffff',
                          borderColor: clinicTheme.primaryColor,
                          boxShadow: `0 0 0 1.5px ${clinicTheme.primaryColor}50`
                        } : {
                          backgroundColor: 'rgba(255, 255, 255, 0.7)',
                          borderColor: `${clinicTheme.primaryColor}25`
                        }}
                      >
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-extrabold text-slate-800 flex items-center gap-1.5">
                            <Stethoscope className="w-3.5 h-3.5 shrink-0" style={{ color: clinicTheme.primaryColor }} />
                            {appt.doctorName || clinicTheme.name}
                          </span>
                          <span 
                            className="text-[10px] font-extrabold px-2 py-0.5 rounded-full"
                            style={appt.isToday ? {
                              backgroundColor: `${clinicTheme.secondaryColor}60`,
                              color: clinicTheme.primaryDarkColor
                            } : {
                              backgroundColor: '#f1f5f9',
                              color: '#475569'
                            }}
                          >
                            {appt.formattedDateTime}
                          </span>
                        </div>

                        <div className="flex items-center justify-between text-[11px] text-slate-500 pt-0.5 border-t border-slate-100">
                          <span className="font-medium truncate max-w-[55%]">
                            {appt.specialty || 'Consulta'}
                          </span>
                          <span className="font-bold text-slate-700 flex items-center gap-1 shrink-0">
                            <MapPin className="w-3 h-3" style={{ color: clinicTheme.primaryColor }} />
                            {resolvedFloor}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Modo Manual de Consulta / Data (exibido se não houver consulta no Feegow ou se paciente clicar em Alterar) */}
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
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
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

                {/* Campo de Busca de Médico / Especialidade (Dropdown após 3 caracteres) */}
                {clinicTheme.id === 'inovare' && (
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
                )}
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

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5 flex items-center gap-1">
                  <Calendar className="w-3.5 h-3.5 text-slate-400" />
                  Nascimento <span className="text-[10px] text-slate-400 font-normal">(opcional)</span>
                </label>
                <input
                  type="text"
                  inputMode="numeric"
                  value={birthDate}
                  onChange={(e) => setBirthDate(maskDate(e.target.value))}
                  placeholder="DD/MM/AAAA"
                  maxLength={10}
                  className="w-full px-3.5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all font-mono form-input-themed"
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
                  value={phone}
                  onChange={(e) => setPhone(maskPhone(e.target.value))}
                  placeholder="(42) 99999-9999"
                  maxLength={15}
                  className="w-full px-3.5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-sm font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all font-mono form-input-themed"
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
                          className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all form-input-themed"
                        />
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        <input
                          type="text"
                          inputMode="numeric"
                          value={comp.cpf}
                          onChange={(e) => updateCompanion(comp.id, 'cpf', maskCpf(e.target.value))}
                          placeholder="CPF *"
                          maxLength={14}
                          className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all font-mono form-input-themed"
                        />
                        <input
                          type="text"
                          inputMode="numeric"
                          value={comp.birthDate}
                          onChange={(e) => updateCompanion(comp.id, 'birthDate', maskDate(e.target.value))}
                          placeholder="Nasc. (opcional)"
                          maxLength={10}
                          className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-semibold text-slate-800 placeholder:text-slate-400 focus:bg-white focus:outline-none transition-all form-input-themed"
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
