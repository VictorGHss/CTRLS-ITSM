import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { Clock } from 'lucide-react';
import api from '../../services/api';
import { getApiErrorMessage } from '../../lib/apiError';
import type { AccessCredential } from './types';
import { resolveClinicTheme } from './utils/clinicThemes';
import { TwoFactorAuthChallenge } from './components/TwoFactorAuthChallenge';
import { FullscreenQrModal } from './components/FullscreenQrModal';
import { CompanionModal } from './components/CompanionModal';
import { CpfFallbackCard } from './components/CpfFallbackCard';
import { CredentialsCarousel } from './components/CredentialsCarousel';
import { PatientAccessFooter } from './components/PatientAccessFooter';
import { SelfRegistrationForm } from './components/SelfRegistrationForm';
import { isValidCpf } from './utils/cpfValidator';
import { useWakeLock } from './hooks/useWakeLock';
import { usePatientAccessAuth } from './hooks/usePatientAccessAuth';

export default function PatientAccess() {
  const { appointmentId } = useParams<{ appointmentId: string }>();

  // Tema e Identidade Visual Dinâmica da Clínica
  const clinicTheme = useMemo(() => {
    return resolveClinicTheme(new URLSearchParams(window.location.search), window.location.pathname);
  }, []);

  // Hook central de Autenticação, Cache Offline e Polling
  const {
    isVerified,
    digits,
    inputRefs,
    challengeLoading,
    challengeError,
    credentials,
    setCredentials,
    verifiedPhoneDigits,
    setVerifiedPhoneDigits,
    verifiedToken,
    setVerifiedToken,
    isPublicRoute,
    defaultPrefix,
    saveCredentialsWithOfflineCache,
    handleResetAccess,
    handleDigitChange,
    handleDigitKeyDown,
    handleUnlock
  } = usePatientAccessAuth({ appointmentId, clinicTheme });

  // Controles de Visualização e Modais
  const [fullscreenCard, setFullscreenCard] = useState<number | null>(null);
  const modalRef = useRef<HTMLDivElement>(null);
  const [activeCardIndex, setActiveCardIndex] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Screen Wake Lock API enquanto QR Code está em tela cheia
  useWakeLock(fullscreenCard !== null);

  // Fallback de CPF
  const [cpfInput, setCpfInput] = useState<string>('');
  const [cpfSubmitLoading, setCpfSubmitLoading] = useState<boolean>(false);
  const [cpfSubmitError, setCpfSubmitError] = useState<string | null>(null);
  const [isEditingCpf, setIsEditingCpf] = useState<boolean>(false);
  const [editingCredential, setEditingCredential] = useState<AccessCredential | null>(null);

  // Cadastro de Acompanhantes e Reativação
  const [isCompanionModalOpen, setIsCompanionModalOpen] = useState<boolean>(false);
  const [companionName, setCompanionName] = useState<string>('');
  const [companionCpf, setCompanionCpf] = useState<string>('');
  const [companionBirthDate, setCompanionBirthDate] = useState<string>('');
  const [companionSubmitLoading, setCompanionSubmitLoading] = useState<boolean>(false);
  const [companionSubmitError, setCompanionSubmitError] = useState<string | null>(null);
  const [isReactivating, setIsReactivating] = useState<boolean>(false);
  const [reactivateMessage, setReactivateMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Monitora saída da tela cheia nativa do browser para sincronizar o estado do React
  useEffect(() => {
    const handleFullscreenChange = () => {
      if (!document.fullscreenElement) {
        setFullscreenCard(null);
      }
    };
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
    };
  }, []);

  // Auto-foco imediato no primeiro dígito ao abrir o desafio de segurança
  useEffect(() => {
    if (!isVerified) {
      const timer = setTimeout(() => {
        inputRefs[0].current?.focus();
      }, 150);
      return () => clearTimeout(timer);
    }
  }, [isVerified, inputRefs]);

  const openFullscreen = (index: number) => {
    if (credentials[index]?.credentialCode === 'BLOCKED_OUTSIDE_WINDOW') return;
    setFullscreenCard(index);
    setTimeout(() => {
      if (modalRef.current && modalRef.current.requestFullscreen) {
        modalRef.current.requestFullscreen().catch(() => {});
      }
    }, 50);
  };

  const closeFullscreen = () => {
    setFullscreenCard(null);
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    }
  };

  const scrollToCard = (index: number) => {
    if (scrollRef.current) {
      const width = scrollRef.current.clientWidth;
      scrollRef.current.scrollTo({
        left: index * (width * 0.85),
        behavior: 'smooth'
      });
      setActiveCardIndex(index);
    }
  };

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    const scrollLeft = target.scrollLeft;
    const width = target.clientWidth;
    const index = Math.round(scrollLeft / (width * 0.85));
    setActiveCardIndex(Math.min(Math.max(index, 0), credentials.length - 1));
  };

  const getActiveAppointmentId = (): string | undefined => {
    if (appointmentId && appointmentId !== 'imagem' && appointmentId !== 'inovare') {
      return appointmentId;
    }
    const fromCred = credentials.find(c => c.appointmentId && c.appointmentId !== 'imagem' && c.appointmentId !== 'inovare')?.appointmentId;
    if (fromCred) return fromCred;
    const patientCred = credentials.find(c => c.userType === 'PATIENT' && c.cpf) || credentials.find(c => c.cpf);
    if (patientCred?.appointmentId && patientCred.appointmentId !== 'imagem' && patientCred.appointmentId !== 'inovare') {
      return patientCred.appointmentId;
    }
    if (patientCred?.cpf) {
      const todayDigits = new Date().toLocaleDateString('sv-SE').replace(/-/g, '');
      return `${defaultPrefix}${todayDigits}-${patientCred.cpf.replace(/\D/g, '')}`;
    }
    return undefined;
  };

  const handleCpfSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const missingCred = credentials.find(
      c => c.locator === 'CPF_MISSING' || c.credentialCode === 'CPF_MISSING'
    );
    const targetId = editingCredential?.appointmentId 
      || missingCred?.appointmentId 
      || getActiveAppointmentId() 
      || appointmentId 
      || credentials[0]?.appointmentId;

    if (!targetId) {
      setCpfSubmitError('Não foi possível identificar seu agendamento. Por favor, recarregue a página.');
      return;
    }
    if (!cpfInput) {
      setCpfSubmitError('Por favor, informe os 11 dígitos do seu CPF.');
      return;
    }

    const cleanCpf = cpfInput.replace(/\D/g, '');
    if (cleanCpf.length !== 11 || !isValidCpf(cleanCpf)) {
      setCpfSubmitError('CPF inválido perante a Receita Federal. Por favor, confira os números digitados.');
      return;
    }

    setCpfSubmitLoading(true);
    setCpfSubmitError(null);

    const token = (
      new URLSearchParams(window.location.search).get('t') ||
      new URLSearchParams(window.location.search).get('token') ||
      verifiedToken ||
      localStorage.getItem(`patient_access_token_${appointmentId}`) ||
      localStorage.getItem(`patient_access_token_${targetId}`) ||
      ''
    ).trim();

    const phoneDigits = (
      new URLSearchParams(window.location.search).get('p') ||
      new URLSearchParams(window.location.search).get('auth') ||
      verifiedPhoneDigits ||
      localStorage.getItem(`patient_access_phone_${appointmentId}`) ||
      localStorage.getItem(`patient_access_phone_${targetId}`) ||
      ''
    ).trim();

    try {
      const validateRes = await api.post<{ authorized: boolean; requiresCpfFallback?: boolean; message?: string }>(
        '/v1/access/validate',
        {
          appointmentId: targetId,
          cpf: cleanCpf,
          credentialId: editingCredential?.id,
          targetName: editingCredential?.name,
          userType: editingCredential?.userType
        },
        {
          headers: {
            'X-Skip-Interceptor': 'true'
          }
        }
      );

      if (validateRes.data?.requiresCpfFallback || !validateRes.data?.authorized) {
        setCpfSubmitError(validateRes.data?.message || 'CPF não encontrado ou inválido. Por favor, confira os números digitados.');
        return;
      }

      const rootId = (appointmentId && appointmentId !== 'inovare' && appointmentId !== 'imagem') ? appointmentId : targetId;
      const query = token
        ? `t=${encodeURIComponent(token)}`
        : phoneDigits
          ? `phoneDigits=${encodeURIComponent(phoneDigits)}`
          : '';

      try {
        const response = await api.get<AccessCredential[]>(
          `/v1/access/credentials/${rootId}${query ? `?${query}` : ''}`,
          {
            headers: {
              'X-Skip-Interceptor': 'true'
            }
          }
        );
        if (response.data && response.data.length > 0) {
          saveCredentialsWithOfflineCache(response.data, { token, phoneDigits });
          if (token) setVerifiedToken(token);
          if (phoneDigits) setVerifiedPhoneDigits(phoneDigits);
          setCredentials(response.data);
          setIsEditingCpf(false);
          setEditingCredential(null);
          setCpfInput('');
          return;
        }
      } catch (fetchErr) {
        console.warn('[PatientAccess] Busca por appointmentId após CPF falhou, tentando lookup por CPF:', fetchErr);
      }

      try {
        const lookupRes = await api.post<AccessCredential[]>('/v1/access/lookup-by-cpf', {
          cpf: cleanCpf,
          clinic: clinicTheme.id
        });
        if (lookupRes.data && lookupRes.data.length > 0) {
          saveCredentialsWithOfflineCache(lookupRes.data);
          setCredentials(lookupRes.data);
          setIsEditingCpf(false);
          setEditingCredential(null);
          setCpfInput('');
          return;
        }
      } catch (lookupErr) {
        console.warn('[PatientAccess] Lookup por CPF após validação falhou:', lookupErr);
      }
      setIsEditingCpf(false);
      setEditingCredential(null);
      setCpfInput('');
    } catch (err: unknown) {
      console.error('[PatientAccess] Falha ao enviar CPF:', err);
      const msg = getApiErrorMessage(err, 'Ocorreu um erro ao salvar o CPF. Tente novamente.');
      setCpfSubmitError(msg);
    } finally {
      setCpfSubmitLoading(false);
    }
  };

  const handleCompanionSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const targetAppointmentId = getActiveAppointmentId();

    if (!targetAppointmentId) {
      setCompanionSubmitError('Não foi possível identificar o cadastro principal. Recarregue a página.');
      return;
    }

    if (!companionName.trim()) {
      setCompanionSubmitError('Por favor, informe o nome completo do acompanhante.');
      return;
    }

    const cleanCpf = companionCpf ? companionCpf.replace(/\D/g, '') : '';
    if (cleanCpf.length !== 11 || !isValidCpf(cleanCpf)) {
      setCompanionSubmitError('CPF do acompanhante inválido perante a Receita Federal. Por favor, confira os números digitados.');
      return;
    }

    const patientCred = credentials.find(c => c.userType === 'PATIENT');
    const patientCleanCpf = patientCred?.cpf ? patientCred.cpf.replace(/\D/g, '') : '';
    if (patientCleanCpf && cleanCpf === patientCleanCpf) {
      setCompanionSubmitError('O CPF informado pertence ao paciente titular. Cada pessoa precisa de seu próprio CPF para liberar a catraca.');
      return;
    }

    if (patientCred?.name && companionName.trim().toUpperCase() === patientCred.name.trim().toUpperCase()) {
      setCompanionSubmitError('O acompanhante não pode ser o próprio paciente titular.');
      return;
    }

    const existingComp = credentials.find(c => c.userType === 'COMPANION' && c.cpf?.replace(/\D/g, '') === cleanCpf);
    if (existingComp) {
      setCompanionSubmitError(`Já existe um acompanhante cadastrado com este CPF (${existingComp.name}).`);
      return;
    }

    setCompanionSubmitLoading(true);
    setCompanionSubmitError(null);

    interface CompanionCreatedResponse {
      locator?: string;
      accessCredential?: string;
      credentialCode?: string;
    }

    try {
      const postResponse = await api.post<AccessCredential[] | CompanionCreatedResponse>(
        `/v1/access/companions/${targetAppointmentId}`,
        {
          name: companionName.trim(),
          cpf: cleanCpf,
          birthDate: companionBirthDate || null
        },
        {
          headers: {
            'X-Skip-Interceptor': 'true'
          }
        }
      );

      let updatedList: AccessCredential[] = [];

      if (Array.isArray(postResponse.data) && postResponse.data.length > 0) {
        updatedList = postResponse.data;
      } else if (postResponse.data && typeof postResponse.data === 'object') {
        const obj = postResponse.data as CompanionCreatedResponse;
        if (obj.accessCredential || obj.credentialCode) {
          const newComp: AccessCredential = {
            appointmentId: targetAppointmentId,
            name: companionName.trim().toUpperCase(),
            userType: 'COMPANION',
            locator: obj.locator || '',
            credentialCode: obj.accessCredential || obj.credentialCode || '',
            cpf: cleanCpf,
            doctorName: credentials[0]?.doctorName || 'Clínica Inovare',
            appointmentDateTime: credentials[0]?.appointmentDateTime || 'Hoje',
            opensAt: '06:00',
            closesAt: '23:00'
          };
          updatedList = [...credentials, newComp];
        }
      }

      if (updatedList.length === 0) {
        try {
          const query = verifiedToken
            ? `t=${encodeURIComponent(verifiedToken)}`
            : verifiedPhoneDigits
              ? `phoneDigits=${encodeURIComponent(verifiedPhoneDigits)}`
              : '';

          const url = query 
            ? `/v1/access/credentials/${targetAppointmentId}?${query}` 
            : `/v1/access/credentials/${targetAppointmentId}`;

          const getResponse = await api.get<AccessCredential[]>(url, {
            headers: { 'X-Skip-Interceptor': 'true' }
          });
          if (getResponse.data && getResponse.data.length > 0) {
            updatedList = getResponse.data;
          }
        } catch (getErr) {
          console.warn('[PatientAccess] Fallback GET após cadastro:', getErr);
        }
      }

      if (updatedList.length > 0) {
        saveCredentialsWithOfflineCache(updatedList, { token: verifiedToken, phoneDigits: verifiedPhoneDigits });
        setIsCompanionModalOpen(false);
        setCompanionName('');
        setCompanionCpf('');
        setCompanionBirthDate('');
        setTimeout(() => {
          scrollToCard(updatedList.length - 1);
        }, 300);
      } else {
        setIsCompanionModalOpen(false);
      }
    } catch (err: unknown) {
      console.error('[PatientAccess] Falha ao cadastrar acompanhante:', err);
      const apiMsg = getApiErrorMessage(err, 'Erro ao cadastrar acompanhante. Tente novamente.');
      setCompanionSubmitError(apiMsg);
    } finally {
      setCompanionSubmitLoading(false);
    }
  };

  const handleReactivateAccess = async () => {
    const targetAppointmentId = getActiveAppointmentId();

    if (!targetAppointmentId) {
      setReactivateMessage({ type: 'error', text: 'Não foi possível identificar o agendamento. Recarregue a página.' });
      setTimeout(() => setReactivateMessage(null), 5000);
      return;
    }

    setIsReactivating(true);
    setReactivateMessage(null);
    try {
      const response = await api.post<AccessCredential[]>(
        `/v1/access/reactivate/${targetAppointmentId}`,
        {},
        {
          headers: {
            'X-Skip-Interceptor': 'true'
          }
        }
      );

      if (response.data && response.data.length > 0) {
        saveCredentialsWithOfflineCache(response.data, { token: verifiedToken, phoneDigits: verifiedPhoneDigits });
        setReactivateMessage({ type: 'success', text: 'Novo QR Code gerado e liberado com sucesso nas catracas!' });
        setTimeout(() => setReactivateMessage(null), 6000);
      } else {
        setReactivateMessage({ type: 'error', text: 'Não foi possível gerar nova credencial. Procure a recepção.' });
        setTimeout(() => setReactivateMessage(null), 5000);
      }
    } catch (err: unknown) {
      console.error('[PatientAccess] Falha ao reativar acesso:', err);
      setReactivateMessage({ type: 'error', text: 'Erro ao reativar acesso na catraca. Tente novamente.' });
      setTimeout(() => setReactivateMessage(null), 5000);
    } finally {
      setIsReactivating(false);
    }
  };

  // === TELA DE AUTO-CHECKIN OU DESAFIO DE IDENTIDADE (2FA) ===
  if (!isVerified) {
    if (isPublicRoute) {
      return (
        <div className="min-h-screen bg-slate-100 flex flex-col justify-between font-sans antialiased">
          <title>Pré-Cadastro — {clinicTheme.shortName}</title>
          <meta name="description" content={`Pré-cadastro e liberação de acesso às catracas físicas da ${clinicTheme.name}`} />
          <div className="w-full max-w-md bg-white shadow-2xl flex flex-col min-h-screen mx-auto relative border-x border-slate-200/60">
            <header className="sticky top-0 bg-white/95 backdrop-blur-md border-b border-slate-100 px-6 py-4 flex items-center justify-center z-10">
              <img 
                src={clinicTheme.logoUrl} 
                alt={clinicTheme.name} 
                className="h-10 w-auto object-contain mx-auto max-h-10"
                onError={(e) => {
                  e.currentTarget.src = clinicTheme.id === 'imagem'
                    ? 'https://placehold.co/180x60/b8004b/ffffff?text=Cl%C3%ADnica+da+Imagem'
                    : 'https://placehold.co/180x60/00875f/ffffff?text=Inovare';
                }}
              />
            </header>
            <main className={`flex-1 px-4 sm:px-6 py-8 space-y-6 overflow-y-auto ${
              clinicTheme.id === 'imagem'
                ? 'bg-gradient-to-b from-white via-rose-50/20 to-slate-50'
                : 'bg-gradient-to-b from-white via-amber-50/20 to-slate-50'
            }`}>
              <SelfRegistrationForm
                clinicTheme={clinicTheme}
                onSuccess={(creds, authInfo) => {
                  saveCredentialsWithOfflineCache(creds, authInfo);
                }}
              />
            </main>
            <PatientAccessFooter clinicTheme={clinicTheme} />
          </div>
        </div>
      );
    }

    return (
      <TwoFactorAuthChallenge
        digits={digits}
        inputRefs={inputRefs}
        challengeLoading={challengeLoading}
        challengeError={challengeError}
        onDigitChange={handleDigitChange}
        onDigitKeyDown={handleDigitKeyDown}
        onUnlock={handleUnlock}
      />
    );
  }

  const patientCredential = credentials.find(c => c.userType === 'PATIENT') || credentials[0];

  // === TELA PRINCIPAL (CARROSSEL DE CREDENCIAIS / CONTINGÊNCIA) ===
  return (
    <div className="min-h-screen bg-slate-100 flex flex-col justify-between font-sans antialiased">
      <title>Pré-Cadastro — {clinicTheme.shortName}</title>
      <meta name="description" content={`Credencial de acesso e QR Code para entrada nas catracas da ${clinicTheme.name}`} />
      <div className="w-full max-w-md bg-white shadow-2xl shadow-brand-primary/5 border-x border-brand-secondary/35 flex flex-col min-h-screen mx-auto relative">
        
        {/* Header Superior */}
        <header className="sticky top-0 bg-white/95 backdrop-blur-md border-b border-brand-secondary/30 px-6 py-4 flex items-center justify-center z-10">
          <img 
            src={clinicTheme.logoUrl} 
            alt={clinicTheme.name} 
            className="h-9 w-auto object-contain mx-auto max-h-9"
            onError={(e) => {
              e.currentTarget.src = clinicTheme.id === 'imagem'
                ? 'https://placehold.co/180x60/b8004b/ffffff?text=Cl%C3%ADnica+da+Imagem'
                : 'https://placehold.co/120x40/feb56c/ffffff?text=Inovare+TI';
            }}
          />
        </header>

        {/* Conteúdo Principal */}
        <main className="flex-1 px-5 py-6 space-y-6 overflow-y-auto pb-12 bg-gradient-to-b from-white via-slate-50/50 to-slate-50">
          
          {/* Saudação Inicial */}
          <div className="space-y-1">
            <h1 className="text-xl font-extrabold text-slate-800 tracking-tight">
              Olá{patientCredential ? `, ${patientCredential.name.split(' ')[0]}` : ''}!
            </h1>
            <p className="text-xs text-slate-400 font-medium">Aqui estão seus cartões para liberação das catracas físicas.</p>
          </div>

          {/* Fluxo Condicional */}
          {isEditingCpf || (credentials.length > 0 && credentials.every(c => c.locator === 'CPF_MISSING' || c.credentialCode === 'CPF_MISSING')) ? (
            <div className="space-y-3">
              <CpfFallbackCard
                patientName={editingCredential?.name}
                cpfInput={cpfInput}
                cpfSubmitLoading={cpfSubmitLoading}
                cpfSubmitError={cpfSubmitError}
                onCpfChange={(masked) => {
                  setCpfSubmitError(null);
                  setCpfInput(masked);
                }}
                onSubmit={handleCpfSubmit}
                onCancel={credentials.some(c => c.locator !== 'CPF_MISSING' && c.credentialCode !== 'CPF_MISSING') ? () => {
                  setIsEditingCpf(false);
                  setEditingCredential(null);
                } : undefined}
              />
            </div>
          ) : credentials.length === 0 ? (
            <div className="bg-brand-secondary/10 border border-brand-primary/20 rounded-3xl p-6 text-center space-y-4 shadow-sm">
              <div className="w-16 h-16 bg-brand-primary/10 rounded-full flex items-center justify-center mx-auto text-brand-primary animate-pulse">
                <Clock className="w-8 h-8" />
              </div>
              <h3 className="text-md font-bold text-slate-800">Acesso em Processamento</h3>
              <p className="text-xs text-slate-600 leading-relaxed">
                Seu acesso prévio está em processamento. 📲 Caso a catraca não libere automaticamente ao chegar, informe seu nome na recepção para liberação imediata!
              </p>
            </div>
          ) : (
            <CredentialsCarousel
              credentials={credentials}
              scrollRef={scrollRef}
              activeCardIndex={activeCardIndex}
              onScroll={handleScroll}
              scrollToCard={scrollToCard}
              onOpenFullscreen={openFullscreen}
              onOpenCompanionModal={() => setIsCompanionModalOpen(true)}
              onReactivateAccess={handleReactivateAccess}
              onResetAccess={handleResetAccess}
              onEditCpf={(targetCred) => {
                if (targetCred) setEditingCredential(targetCred);
                setIsEditingCpf(true);
              }}
              isReactivating={isReactivating}
              reactivateMessage={reactivateMessage}
              clinicTheme={clinicTheme}
            />
          )}

          {/* Instruções para Acesso */}
          <div className="text-center px-4 pt-2">
            <p className="text-xs text-slate-500 leading-relaxed">
              💡 <b>Instruções:</b> Aproxime o QR Code do leitor da catraca. Se houver acompanhantes cadastrados, passe primeiro o seu cartão (Titular), aguarde a passagem e deslize para passar os demais cartões.
            </p>
          </div>

        </main>

        <PatientAccessFooter clinicTheme={clinicTheme} />

      </div>

      {/* Modal Tela Cheia */}
      {fullscreenCard !== null && (
        <FullscreenQrModal
          modalRef={modalRef}
          credentials={credentials}
          currentIndex={fullscreenCard}
          onSwitchCard={(idx) => setFullscreenCard(idx)}
          onClose={closeFullscreen}
          clinicTheme={clinicTheme}
        />
      )}

      {/* Modal Acompanhante */}
      <CompanionModal
        isOpen={isCompanionModalOpen}
        companionName={companionName}
        companionCpf={companionCpf}
        companionBirthDate={companionBirthDate}
        patientCpf={credentials.find(c => c.userType === 'PATIENT')?.cpf}
        patientName={credentials.find(c => c.userType === 'PATIENT')?.name}
        existingCompanionsCpfs={credentials.filter(c => c.userType === 'COMPANION').map(c => c.cpf || '').filter(Boolean)}
        companionSubmitLoading={companionSubmitLoading}
        companionSubmitError={companionSubmitError}
        onNameChange={setCompanionName}
        onCpfChange={setCompanionCpf}
        onBirthDateChange={setCompanionBirthDate}
        onSubmit={handleCompanionSubmit}
        onClose={() => {
          setIsCompanionModalOpen(false);
          setCompanionName('');
          setCompanionCpf('');
          setCompanionBirthDate('');
        }}
        clinicTheme={clinicTheme}
      />
    </div>
  );
}
