import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { Clock } from 'lucide-react';
import api from '../../services/api';
import type { AccessCredential } from './types';
import { resolveClinicTheme } from './utils/clinicThemes';
import { TwoFactorAuthChallenge } from './components/TwoFactorAuthChallenge';
import { FullscreenQrModal } from './components/FullscreenQrModal';
import { CompanionModal } from './components/CompanionModal';
import { CpfFallbackCard } from './components/CpfFallbackCard';
import { CredentialsCarousel } from './components/CredentialsCarousel';
import { PatientAccessFooter } from './components/PatientAccessFooter';
import { SelfRegistrationForm } from './components/SelfRegistrationForm';

export default function PatientAccess() {
  const { appointmentId } = useParams<{ appointmentId: string }>();

  // --- Tema e Identidade Visual Dinâmica da Clínica ---
  const clinicTheme = useMemo(() => {
    return resolveClinicTheme(new URLSearchParams(window.location.search), window.location.pathname);
  }, [appointmentId]);

  // --- Estados de controle do desafio de identidade (2FA por telefone) ---
  const [isVerified, setIsVerified] = useState<boolean>(false);
  const [digits, setDigits] = useState<string[]>(['', '', '', '']);
  const inputRef0 = useRef<HTMLInputElement>(null);
  const inputRef1 = useRef<HTMLInputElement>(null);
  const inputRef2 = useRef<HTMLInputElement>(null);
  const inputRef3 = useRef<HTMLInputElement>(null);
  const inputRefs = useMemo(() => [inputRef0, inputRef1, inputRef2, inputRef3], []);
  const [challengeLoading, setChallengeLoading] = useState<boolean>(false);
  const [challengeError, setChallengeError] = useState<string | null>(null);

  // --- Estados de controle das credenciais retornadas após o desafio ---
  const [credentials, setCredentials] = useState<AccessCredential[]>([]);
  const [fullscreenCard, setFullscreenCard] = useState<number | null>(null);
  const modalRef = useRef<HTMLDivElement>(null);
  const [activeCardIndex, setActiveCardIndex] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  // --- Fallback de CPF ---
  const [verifiedPhoneDigits, setVerifiedPhoneDigits] = useState<string>('');
  const [verifiedToken, setVerifiedToken] = useState<string>('');
  const [cpfInput, setCpfInput] = useState<string>('');
  const [cpfSubmitLoading, setCpfSubmitLoading] = useState<boolean>(false);
  const [cpfSubmitError, setCpfSubmitError] = useState<string | null>(null);
  const [isEditingCpf, setIsEditingCpf] = useState<boolean>(false);

  // --- Cadastro de Acompanhantes e Reativação ---
  const [isCompanionModalOpen, setIsCompanionModalOpen] = useState<boolean>(false);
  const [companionName, setCompanionName] = useState<string>('');
  const [companionCpf, setCompanionCpf] = useState<string>('');
  const [companionBirthDate, setCompanionBirthDate] = useState<string>('');
  const [companionSubmitLoading, setCompanionSubmitLoading] = useState<boolean>(false);
  const [companionSubmitError, setCompanionSubmitError] = useState<string | null>(null);
  const [isReactivating, setIsReactivating] = useState<boolean>(false);

  // Screen Wake Lock API: impede que o ecrã do telemóvel apague enquanto o QR Code está em tela cheia
  useEffect(() => {
    let activeLock: WakeLockSentinel | null = null;

    const acquireLock = async () => {
      if (fullscreenCard !== null && 'wakeLock' in navigator && navigator.wakeLock) {
        try {
          activeLock = await navigator.wakeLock.request('screen');
        } catch (err: unknown) {
          console.warn('[WakeLock] Erro ao solicitar trava de tela:', err);
        }
      }
    };

    void acquireLock();

    return () => {
      if (activeLock) {
        activeLock.release()
          .catch((err: unknown) => console.warn('[WakeLock] Erro ao liberar trava de tela:', err));
      }
    };
  }, [fullscreenCard]);

  const isPublicRoute = clinicTheme.id === 'imagem' || clinicTheme.id === 'inovare' || appointmentId === 'imagem' || appointmentId === 'inovare';
  const defaultPrefix = clinicTheme.id === 'inovare' ? 'INOV-' : 'IMG-';

  const saveCredentialsWithOfflineCache = (
    data: AccessCredential[],
    authInfo?: { token?: string; phoneDigits?: string }
  ) => {
    // Garante que todas as credenciais possuam appointmentId definido
    const normalizedData = (data || []).map(c => ({
      ...c,
      appointmentId: (c.appointmentId && c.appointmentId !== 'imagem' && c.appointmentId !== 'inovare')
        ? c.appointmentId
        : (appointmentId && appointmentId !== 'imagem' && appointmentId !== 'inovare')
          ? appointmentId
          : (c.cpf ? `${defaultPrefix}${c.cpf.replace(/\D/g, '')}` : undefined)
    }));

    setCredentials(normalizedData);
    setIsVerified(true);
    if (normalizedData.length > 0) {
      try {
        if (appointmentId && appointmentId !== 'imagem' && appointmentId !== 'inovare') {
          localStorage.setItem(`patient_access_credentials_${appointmentId}`, JSON.stringify(normalizedData));
          if (authInfo?.token) {
            localStorage.setItem(`patient_access_token_${appointmentId}`, authInfo.token);
          }
          if (authInfo?.phoneDigits) {
            localStorage.setItem(`patient_access_phone_${appointmentId}`, authInfo.phoneDigits);
          }
        }
        // Se for auto-cadastro público (Imagem ou Inovare), salva também no cache permanente do tema com a data de emissão e data alvo
        if (isPublicRoute) {
          const todayStr = new Date().toLocaleDateString('sv-SE'); // 'YYYY-MM-DD'
          let targetDate = todayStr;
          const firstAppId = normalizedData[0]?.appointmentId;
          const firstAppDateTime = normalizedData[0]?.appointmentDateTime;

          if (firstAppId && firstAppId.length >= 13 && (firstAppId.startsWith('INOV-') || firstAppId.startsWith('IMG-'))) {
            const y = firstAppId.substring(5, 9);
            const m = firstAppId.substring(9, 11);
            const d = firstAppId.substring(11, 13);
            targetDate = `${y}-${m}-${d}`;
          } else if (firstAppDateTime) {
            const dmyMatch = firstAppDateTime.match(/(\d{2})\/(\d{2})\/(\d{4})/);
            if (dmyMatch) {
              targetDate = `${dmyMatch[3]}-${dmyMatch[2]}-${dmyMatch[1]}`;
            } else {
              const ymdMatch = firstAppDateTime.match(/(\d{4})-(\d{2})-(\d{2})/);
              if (ymdMatch) {
                targetDate = `${ymdMatch[1]}-${ymdMatch[2]}-${ymdMatch[3]}`;
              }
            }
          }
          localStorage.setItem(`patient_access_${clinicTheme.id}_last_credentials`, JSON.stringify({
            savedDate: todayStr,
            targetDate: targetDate,
            credentials: normalizedData
          }));
        }
      } catch {
        // Ignora falhas de gravação do localStorage
      }
    }
  };

  const handleResetAccess = () => {
    try {
      localStorage.removeItem(`patient_access_${clinicTheme.id}_last_credentials`);
      localStorage.removeItem('patient_access_imagem_last_credentials');
      localStorage.removeItem('patient_access_inovare_last_credentials');
      if (appointmentId && appointmentId !== 'imagem' && appointmentId !== 'inovare') {
        localStorage.removeItem(`patient_access_credentials_${appointmentId}`);
        localStorage.removeItem(`patient_access_token_${appointmentId}`);
        localStorage.removeItem(`patient_access_phone_${appointmentId}`);
      }
    } catch {
      // Ignora erro
    }
    setCredentials([]);
    setIsVerified(false);
  };

  const refreshCredentials = async (silent = false) => {
    if (!appointmentId || appointmentId === 'imagem' || appointmentId === 'inovare') return;
    const params = new URLSearchParams(window.location.search);
    const token = (params.get('t') || params.get('token') || verifiedToken || localStorage.getItem(`patient_access_token_${appointmentId}`) || '').trim();
    const phoneDigits = (params.get('p') || params.get('auth') || verifiedPhoneDigits || localStorage.getItem(`patient_access_phone_${appointmentId}`) || '').trim();

    if (!token && !phoneDigits) return;

    if (!silent) setChallengeLoading(true);
    try {
      const query = token 
        ? `t=${encodeURIComponent(token)}` 
        : `phoneDigits=${encodeURIComponent(phoneDigits)}`;
      
      const response = await api.get<AccessCredential[]>(
        `/v1/access/credentials/${appointmentId}?${query}`,
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
      }
    } catch (err: unknown) {
      console.warn('[PatientAccess] Erro ao revalidar credenciais com o servidor:', err);
    } finally {
      if (!silent) setChallengeLoading(false);
    }
  };

  // Desbloqueio automático via Magic Link (?t=...) ou parâmetro de telefone (?p=...)
  useEffect(() => {
    if (!appointmentId || appointmentId === 'imagem' || appointmentId === 'inovare') return;
    const params = new URLSearchParams(window.location.search);
    const tokenParam = (params.get('t') || params.get('token') || '').trim();
    const phoneDigitsParam = (params.get('p') || params.get('auth') || '').trim();

    if (tokenParam || phoneDigitsParam) {
      void refreshCredentials(false);
    }
  }, [appointmentId]);

  // Recupera credenciais em cache local (PWA Offline-First) com Revalidação em Background
  useEffect(() => {
    try {
      const todayStr = new Date().toLocaleDateString('sv-SE'); // 'YYYY-MM-DD'

      if (isPublicRoute) {
        const cacheKey = `patient_access_${clinicTheme.id}_last_credentials`;
        const cachedRaw = localStorage.getItem(cacheKey) || (clinicTheme.id === 'imagem' ? localStorage.getItem('patient_access_imagem_last_credentials') : null);
        if (cachedRaw) {
          try {
            const parsed = JSON.parse(cachedRaw);
            let creds: AccessCredential[] = [];
            let savedDate: string | null = null;

            if (parsed && typeof parsed === 'object' && !Array.isArray(parsed) && Array.isArray(parsed.credentials)) {
              creds = parsed.credentials;
              savedDate = parsed.savedDate;
            } else if (Array.isArray(parsed)) {
              creds = parsed;
            }

            // Expira o cache APENAS se a data do agendamento/consulta já tiver passado (anterior a hoje)
            let effectiveDate = parsed.targetDate || savedDate;
            const hasFutureOrTodayAppt = creds.some(c => {
              if (!c.appointmentDateTime) return false;
              const dmy = c.appointmentDateTime.match(/(\d{2})\/(\d{2})\/(\d{4})/);
              if (dmy) {
                const dateStr = `${dmy[3]}-${dmy[2]}-${dmy[1]}`;
                return dateStr >= todayStr;
              }
              const ymd = c.appointmentDateTime.match(/(\d{4})-(\d{2})-(\d{2})/);
              if (ymd) {
                const dateStr = `${ymd[1]}-${ymd[2]}-${ymd[3]}`;
                return dateStr >= todayStr;
              }
              return false;
            });

            if (hasFutureOrTodayAppt) {
              effectiveDate = todayStr;
            }

            if (effectiveDate && effectiveDate < todayStr) {
              console.log(`[PatientAccess] Cache de auto-cadastro (${clinicTheme.id}) expirado pois a consulta era em (${effectiveDate}). Expirando para nova emissão.`);
              localStorage.removeItem(cacheKey);
              setCredentials([]);
              setIsVerified(false);
              return;
            }

            if (creds.length > 0) {
              console.log(`[PatientAccess] Credenciais (${clinicTheme.id}) de hoje restauradas do cache`);
              setCredentials(creds);
              setIsVerified(true);
            }
          } catch {
            localStorage.removeItem(cacheKey);
          }
        }
        return;
      }

      if (appointmentId && appointmentId !== 'imagem' && appointmentId !== 'inovare') {
        const key = `patient_access_credentials_${appointmentId}`;
        const cached = localStorage.getItem(key);
        if (cached) {
          const parsed = JSON.parse(cached) as AccessCredential[];
          if (Array.isArray(parsed) && parsed.length > 0) {
            const hasBlocked = parsed.some(c => c.credentialCode === 'BLOCKED_OUTSIDE_WINDOW');
            const savedToken = localStorage.getItem(`patient_access_token_${appointmentId}`);
            const savedPhone = localStorage.getItem(`patient_access_phone_${appointmentId}`);
            
            if (savedToken) setVerifiedToken(savedToken);
            if (savedPhone) setVerifiedPhoneDigits(savedPhone);

            // Se tiver credenciais bloqueadas e não tiver credencial salva para revalidar, exige autenticação nova
            if (hasBlocked && !savedToken && !savedPhone && !window.location.search.includes('t=') && !window.location.search.includes('p=')) {
              console.log('[PatientAccess] Cache continha bloqueio antigo sem credencial salva. Solicitando desafio novamente.');
              setIsVerified(false);
            } else {
              console.log('[PatientAccess] Credenciais restauradas do cache offline');
              setCredentials(parsed);
              setIsVerified(true);
              // Revalida em background imediatamente para atualizar horários/bloqueios
              if (navigator.onLine) {
                void refreshCredentials(true);
              }
            }
          }
        }
      }
    } catch {
      // Ignora falhas de leitura do localStorage
    }
  }, [appointmentId, clinicTheme.id, isPublicRoute]);

  // Auto-refresh inteligente: revalida quando o app volta para o primeiro plano ou quando há cartões bloqueados
  useEffect(() => {
    const hasBlocked = credentials.some(c => c.credentialCode === 'BLOCKED_OUTSIDE_WINDOW');
    
    // Polling a cada 20 segundos enquanto houver cartão bloqueado (para liberar automaticamente assim que entrar na janela de 2h)
    let interval: ReturnType<typeof setInterval> | null = null;
    if (hasBlocked && isVerified) {
      interval = setInterval(() => {
        if (navigator.onLine) {
          void refreshCredentials(true);
        }
      }, 20000);
    }

    // Revalidar imediatamente quando o paciente desbloqueia o celular ou volta para a aba do navegador
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && navigator.onLine && isVerified) {
        void refreshCredentials(true);
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      if (interval) clearInterval(interval);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [credentials, isVerified, verifiedToken, verifiedPhoneDigits]);

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

  const handleDigitChange = (index: number, val: string) => {
    setChallengeError(null);
    const numericVal = val.replace(/\D/g, '');
    if (!numericVal) {
      const newDigits = [...digits];
      newDigits[index] = '';
      setDigits(newDigits);
      return;
    }
    const newDigits = [...digits];
    newDigits[index] = numericVal.substring(numericVal.length - 1);
    setDigits(newDigits);

    if (index < 3) {
      inputRefs[index + 1].current?.focus();
    }
  };

  const handleDigitKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      const newDigits = [...digits];
      newDigits[index - 1] = '';
      setDigits(newDigits);
      inputRefs[index - 1].current?.focus();
    }
  };

  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!appointmentId || digits.some(d => d === '')) return;

    const phoneDigits = digits.join('');
    setChallengeLoading(true);
    setChallengeError(null);

    try {
      console.log('[PatientAccess] Enviando desafio de 4 dígitos para o agendamento:', appointmentId);
      const response = await api.get<AccessCredential[]>(
        `/v1/access/credentials/${appointmentId}?phoneDigits=${phoneDigits}`,
        {
          headers: {
            'X-Skip-Interceptor': 'true'
          }
        }
      );
      saveCredentialsWithOfflineCache(response.data || [], { phoneDigits });
      setVerifiedPhoneDigits(phoneDigits);
    } catch (err: unknown) {
      console.error('[PatientAccess] Falha no desafio de segurança:', err);
      setChallengeError('Código inválido. Tente novamente.');
      setDigits(['', '', '', '']);
      setTimeout(() => inputRefs[0].current?.focus(), 50);
    } finally {
      setChallengeLoading(false);
    }
  };


  const handleCpfSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const targetId = getActiveAppointmentId() || appointmentId || credentials[0]?.appointmentId;
    if (!targetId) {
      setCpfSubmitError('Não foi possível identificar seu agendamento. Por favor, recarregue a página.');
      return;
    }
    if (!cpfInput) {
      setCpfSubmitError('Por favor, informe os 11 dígitos do seu CPF.');
      return;
    }

    const cleanCpf = cpfInput.replace(/\D/g, '');
    if (cleanCpf.length !== 11) {
      setCpfSubmitError('Por favor, informe os 11 dígitos do seu CPF.');
      return;
    }

    setCpfSubmitLoading(true);
    setCpfSubmitError(null);

    try {
      console.log('[PatientAccess] Enviando CPF para validação:', cleanCpf, 'agendamento:', targetId);
      const validateRes = await api.post<{ authorized: boolean; requiresCpfFallback?: boolean; message?: string }>(
        '/v1/access/validate',
        {
          appointmentId: targetId,
          cpf: cleanCpf
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

      const query = verifiedToken
        ? `t=${encodeURIComponent(verifiedToken)}`
        : verifiedPhoneDigits
          ? `phoneDigits=${encodeURIComponent(verifiedPhoneDigits)}`
          : '';

      try {
        const response = await api.get<AccessCredential[]>(
          `/v1/access/credentials/${targetId}${query ? `?${query}` : ''}`,
          {
            headers: {
              'X-Skip-Interceptor': 'true'
            }
          }
        );
        if (response.data && response.data.length > 0) {
          saveCredentialsWithOfflineCache(response.data, { token: verifiedToken, phoneDigits: verifiedPhoneDigits });
          setCredentials(response.data);
          setIsEditingCpf(false);
          return;
        }
      } catch (fetchErr) {
        console.warn('[PatientAccess] Busca por appointmentId após CPF falhou, tentando lookup por CPF:', fetchErr);
      }

      // Fallback para auto-cadastros: busca as credenciais recém-emitidas pelo CPF
      try {
        const lookupRes = await api.post<AccessCredential[]>('/v1/access/lookup-by-cpf', {
          cpf: cleanCpf,
          clinic: clinicTheme.id
        });
        if (lookupRes.data && lookupRes.data.length > 0) {
          saveCredentialsWithOfflineCache(lookupRes.data);
          setCredentials(lookupRes.data);
        }
      } catch (lookupErr) {
        console.warn('[PatientAccess] Lookup por CPF após validação falhou:', lookupErr);
      }
      setIsEditingCpf(false);
    } catch (err: unknown) {
      console.error('[PatientAccess] Falha ao enviar CPF:', err);
      const apiErr = err as { response?: { data?: { message?: string } } };
      const msg = apiErr?.response?.data?.message || 'Ocorreu um erro ao salvar o CPF. Tente novamente.';
      setCpfSubmitError(msg);
    } finally {
      setCpfSubmitLoading(false);
    }
  };

  const getActiveAppointmentId = (): string | undefined => {
    if (appointmentId && appointmentId !== 'imagem' && appointmentId !== 'inovare') {
      return appointmentId;
    }
    const fromCred = credentials.find(c => c.appointmentId && c.appointmentId !== 'imagem' && c.appointmentId !== 'inovare')?.appointmentId;
    if (fromCred) return fromCred;
    const patientCred = credentials.find(c => c.userType === 'PATIENT' && c.cpf) || credentials.find(c => c.cpf);
    if (patientCred?.cpf) {
      return `${defaultPrefix}${patientCred.cpf.replace(/\D/g, '')}`;
    }
    return undefined;
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
    if (cleanCpf.length !== 11) {
      setCompanionSubmitError('Por favor, informe um CPF completo com 11 dígitos para o acompanhante.');
      return;
    }

    setCompanionSubmitLoading(true);
    setCompanionSubmitError(null);

    try {
      console.log('[PatientAccess] Cadastrando acompanhante:', companionName, 'para agendamento:', targetAppointmentId);
      const postResponse = await api.post<any>(
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

      // 1. Se a API já retornou a lista completa de credenciais atualizada
      if (Array.isArray(postResponse.data) && postResponse.data.length > 0) {
        updatedList = postResponse.data;
      } else if (postResponse.data && (postResponse.data.accessCredential || postResponse.data.credentialCode)) {
        // 2. Se retornou uma credencial avulsa criada, adiciona ao estado existente
        const newComp: AccessCredential = {
          appointmentId: targetAppointmentId,
          name: companionName.trim().toUpperCase(),
          userType: 'COMPANION',
          locator: postResponse.data.locator || '',
          credentialCode: postResponse.data.accessCredential || postResponse.data.credentialCode || '',
          cpf: cleanCpf,
          doctorName: credentials[0]?.doctorName || 'Clínica Inovare',
          appointmentDateTime: credentials[0]?.appointmentDateTime || 'Hoje',
          opensAt: '06:00',
          closesAt: '23:00'
        };
        updatedList = [...credentials, newComp];
      } else {
        // 3. Fallback: tenta buscar via GET
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
      setCompanionSubmitError('Erro ao cadastrar acompanhante. Tente novamente.');
    } finally {
      setCompanionSubmitLoading(false);
    }
  };

  const handleReactivateAccess = async () => {
    const targetAppointmentId = getActiveAppointmentId();

    if (!targetAppointmentId) {
      console.warn('[PatientAccess] Não foi possível reativar: ID do agendamento ausente.');
      return;
    }

    setIsReactivating(true);
    try {
      console.log('[PatientAccess] Reativando acesso físico:', targetAppointmentId);
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
      }
    } catch (err: unknown) {
      console.error('[PatientAccess] Falha ao reativar acesso:', err);
    } finally {
      setIsReactivating(false);
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
          {credentials.some(c => c.locator === 'CPF_MISSING') || isEditingCpf ? (
            <div className="space-y-3">
              {isEditingCpf && credentials.length > 0 && (
                <div className="flex justify-between items-center px-1">
                  <span className="text-xs font-bold text-slate-700">Atualizar CPF</span>
                  <button
                    type="button"
                    onClick={() => setIsEditingCpf(false)}
                    className="text-xs font-semibold text-slate-400 hover:text-slate-600 cursor-pointer"
                  >
                    Voltar
                  </button>
                </div>
              )}
              <CpfFallbackCard
                cpfInput={cpfInput}
                cpfSubmitLoading={cpfSubmitLoading}
                cpfSubmitError={cpfSubmitError}
                onCpfChange={(masked) => {
                  setCpfSubmitError(null);
                  setCpfInput(masked);
                }}
                onSubmit={handleCpfSubmit}
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
              onEditCpf={() => setIsEditingCpf(true)}
              isReactivating={isReactivating}
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
