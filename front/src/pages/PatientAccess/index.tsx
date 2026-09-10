import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { Clock } from 'lucide-react';
import api from '../../services/api';
import { getApiErrorMessage } from '../../lib/apiError';
import { resolveClinicTheme } from './utils/clinicThemes';
import { TwoFactorAuthChallenge } from './components/TwoFactorAuthChallenge';
import { FullscreenQrModal } from './components/FullscreenQrModal';
import { CompanionModal } from './components/CompanionModal';
import { CpfFallbackCard } from './components/CpfFallbackCard';
import { CredentialsCarousel } from './components/CredentialsCarousel';
import { PatientAccessFooter } from './components/PatientAccessFooter';
import { SelfRegistrationForm } from './components/SelfRegistrationForm';
import { useWakeLock } from './hooks/useWakeLock';
import { usePatientAccessAuth } from './hooks/usePatientAccessAuth';
import { useCompanionManagement } from './hooks/useCompanionManagement';
import { useCpfFallback } from './hooks/useCpfFallback';
import type { AccessCredential } from './types';

export default function PatientAccess() {
  const { appointmentId } = useParams<{ appointmentId: string }>();

  // Tema da Clínica
  const clinicTheme = useMemo(() => {
    return resolveClinicTheme(new URLSearchParams(window.location.search), window.location.pathname);
  }, []);

  // Hook central de Autenticação e Credenciais
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

  // Controles de Visualização
  const [fullscreenCard, setFullscreenCard] = useState<number | null>(null);
  const modalRef = useRef<HTMLDivElement>(null);
  const [activeCardIndex, setActiveCardIndex] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Screen Wake Lock API
  useWakeLock(fullscreenCard !== null);

  // Hook de Fallback de CPF
  const {
    cpfInput,
    setCpfInput,
    cpfSubmitLoading,
    cpfSubmitError,
    setCpfSubmitError,
    isEditingCpf,
    setIsEditingCpf,
    editingCredential,
    setEditingCredential,
    handleCpfSubmit,
    getActiveAppointmentId
  } = useCpfFallback({
    credentials,
    appointmentId,
    clinicId: clinicTheme.id,
    defaultPrefix,
    verifiedToken,
    verifiedPhoneDigits,
    setVerifiedToken,
    setVerifiedPhoneDigits,
    saveCredentialsWithOfflineCache,
    setCredentials
  });

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

  // Hook de Gestão de Acompanhantes
  const {
    isCompanionModalOpen,
    setIsCompanionModalOpen,
    companionName,
    setCompanionName,
    companionCpf,
    setCompanionCpf,
    companionBirthDate,
    setCompanionBirthDate,
    companionSubmitLoading,
    companionSubmitError,
    handleCompanionSubmit,
    resetCompanionForm
  } = useCompanionManagement({
    credentials,
    targetAppointmentId: getActiveAppointmentId(),
    verifiedToken,
    verifiedPhoneDigits,
    onCredentialsUpdated: creds => saveCredentialsWithOfflineCache(creds, { token: verifiedToken, phoneDigits: verifiedPhoneDigits }),
    onScrollToCard: scrollToCard
  });

  // Reativação de Acesso
  const [isReactivating, setIsReactivating] = useState<boolean>(false);
  const [reactivateMessage, setReactivateMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const handleReactivateAccess = async (targetCred?: AccessCredential) => {
    const activeCardCred = targetCred || credentials[activeCardIndex] || credentials[0];
    const targetAppointmentId =
      activeCardCred?.appointmentId &&
      activeCardCred.appointmentId !== 'imagem' &&
      activeCardCred.appointmentId !== 'inovare'
        ? activeCardCred.appointmentId
        : activeCardCred?.cpf
          ? activeCardCred.cpf.replace(/\D/g, '')
          : getActiveAppointmentId() || appointmentId;

    if (!targetAppointmentId) {
      setReactivateMessage({ type: 'error', text: 'Não foi possível identificar o agendamento. Recarregue a página.' });
      setTimeout(() => setReactivateMessage(null), 5000);
      return;
    }

    setIsReactivating(true);
    setReactivateMessage(null);
    try {
      const response = await api.post<AccessCredential[]>(
        `/v1/access/reactivate/${encodeURIComponent(targetAppointmentId)}`,
        {},
        { headers: { 'X-Skip-Interceptor': 'true' } }
      );

      if (response.data && response.data.length > 0) {
        // Mescla as credenciais reativadas com a lista existente para preservar acompanhantes/titular
        const currentList = credentials.length > 0 ? credentials : [];
        const mergedList = [...currentList];
        for (const newCred of response.data) {
          const idx = mergedList.findIndex(c =>
            (newCred.id && c.id && newCred.id === c.id) ||
            (newCred.cpf && c.cpf && newCred.cpf.replace(/\D/g, '') === c.cpf.replace(/\D/g, '')) ||
            (newCred.userType === c.userType && newCred.name.trim().toLowerCase() === c.name.trim().toLowerCase())
          );
          if (idx >= 0) {
            mergedList[idx] = { ...mergedList[idx], ...newCred };
          } else {
            mergedList.push(newCred);
          }
        }
        const finalList = mergedList.length > 0 ? mergedList : response.data;
        saveCredentialsWithOfflineCache(finalList, { token: verifiedToken, phoneDigits: verifiedPhoneDigits });
        setReactivateMessage({ type: 'success', text: 'Novo QR Code gerado e liberado com sucesso nas catracas!' });
        setTimeout(() => setReactivateMessage(null), 6000);
      } else {
        setReactivateMessage({ type: 'error', text: 'Não foi possível gerar nova credencial. Procure a recepção.' });
        setTimeout(() => setReactivateMessage(null), 5000);
      }
    } catch (err: unknown) {
      console.error('[PatientAccess] Falha ao reativar acesso:', err);
      const msg = getApiErrorMessage(err, 'Erro ao reativar acesso na catraca. Tente novamente.');
      setReactivateMessage({ type: 'error', text: msg });
      setTimeout(() => setReactivateMessage(null), 5000);
    } finally {
      setIsReactivating(false);
    }
  };

  // Monitora saída da tela cheia nativa
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

  // Auto-foco imediato no primeiro dígito
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

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    const scrollLeft = target.scrollLeft;
    const width = target.clientWidth;
    const index = Math.round(scrollLeft / (width * 0.85));
    setActiveCardIndex(Math.min(Math.max(index, 0), credentials.length - 1));
  };

  // === TELA DE AUTO-CHECKIN OU 2FA ===
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
                onError={e => {
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
            onError={e => {
              e.currentTarget.src = clinicTheme.id === 'imagem'
                ? 'https://placehold.co/180x60/b8004b/ffffff?text=Cl%C3%ADnica+da+Imagem'
                : 'https://placehold.co/120x40/feb56c/ffffff?text=Inovare+TI';
            }}
          />
        </header>

        {/* Conteúdo Principal */}
        <main className="flex-1 px-5 py-6 space-y-6 overflow-y-auto pb-12 bg-gradient-to-b from-white via-slate-50/50 to-slate-50">
          <div className="space-y-1">
            <h1 className="text-xl font-extrabold text-slate-800 tracking-tight">
              Olá{patientCredential ? `, ${patientCredential.name.split(' ')[0]}` : ''}!
            </h1>
            <p className="text-xs text-slate-400 font-medium">Aqui estão seus cartões para liberação das catracas físicas.</p>
          </div>

          {isEditingCpf || (credentials.length > 0 && credentials.every(c => c.locator === 'CPF_MISSING' || c.credentialCode === 'CPF_MISSING')) ? (
            <div className="space-y-3">
              <CpfFallbackCard
                patientName={editingCredential?.name}
                cpfInput={cpfInput}
                cpfSubmitLoading={cpfSubmitLoading}
                cpfSubmitError={cpfSubmitError}
                onCpfChange={masked => {
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
              onEditCpf={targetCred => {
                if (targetCred) setEditingCredential(targetCred);
                setIsEditingCpf(true);
              }}
              isReactivating={isReactivating}
              reactivateMessage={reactivateMessage}
              clinicTheme={clinicTheme}
            />
          )}

          <div className="text-center px-4 pt-2">
            <p className="text-xs text-slate-500 leading-relaxed">
              💡 <b>Instruções:</b> Aproxime o QR Code do leitor da catraca. Se houver acompanhantes cadastrados, passe primeiro o seu cartão (Titular), aguarde a passagem e deslize para passar os demais cartões.
            </p>
          </div>
        </main>

        <PatientAccessFooter clinicTheme={clinicTheme} />
      </div>

      {fullscreenCard !== null && (
        <FullscreenQrModal
          modalRef={modalRef}
          credentials={credentials}
          currentIndex={fullscreenCard}
          onSwitchCard={idx => setFullscreenCard(idx)}
          onClose={closeFullscreen}
          clinicTheme={clinicTheme}
        />
      )}

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
        onClose={resetCompanionForm}
        clinicTheme={clinicTheme}
      />
    </div>
  );
}
