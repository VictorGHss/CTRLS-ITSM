import { useState, useRef, useMemo, useEffect, useCallback } from 'react';
import type { AccessCredential } from '../types';
import type { ClinicTheme } from '../utils/clinicThemes';
import api from '../../../services/api';

interface UsePatientAccessAuthProps {
  appointmentId?: string;
  clinicTheme: ClinicTheme;
}

export function usePatientAccessAuth({ appointmentId, clinicTheme }: UsePatientAccessAuthProps) {
  // Desafio 2FA (4 dígitos)
  const [isVerified, setIsVerified] = useState<boolean>(false);
  const [digits, setDigits] = useState<string[]>(['', '', '', '']);
  const inputRef0 = useRef<HTMLInputElement>(null);
  const inputRef1 = useRef<HTMLInputElement>(null);
  const inputRef2 = useRef<HTMLInputElement>(null);
  const inputRef3 = useRef<HTMLInputElement>(null);
  const inputRefs = useMemo(() => [inputRef0, inputRef1, inputRef2, inputRef3], []);
  const [challengeLoading, setChallengeLoading] = useState<boolean>(false);
  const [challengeError, setChallengeError] = useState<string | null>(null);

  // Credenciais ativas
  const [credentials, setCredentials] = useState<AccessCredential[]>([]);
  const [verifiedPhoneDigits, setVerifiedPhoneDigits] = useState<string>('');
  const [verifiedToken, setVerifiedToken] = useState<string>('');

  const isPublicRoute =
    clinicTheme.id === 'imagem' ||
    clinicTheme.id === 'inovare' ||
    appointmentId === 'imagem' ||
    appointmentId === 'inovare';
  const defaultPrefix = clinicTheme.id === 'inovare' ? 'INOV-' : 'IMG-';

  // Salva no cache offline (localStorage)
  const saveCredentialsWithOfflineCache = useCallback(
    (data: AccessCredential[], authInfo?: { token?: string; phoneDigits?: string }) => {
      const todayDigits = new Date().toLocaleDateString('sv-SE').replace(/-/g, '');
      const normalizedData = (data || []).map(c => {
        const fallbackAppId = c.cpf ? `${defaultPrefix}${todayDigits}-${c.cpf.replace(/\D/g, '')}` : undefined;
        return {
          ...c,
          appointmentId:
            c.appointmentId && c.appointmentId !== 'imagem' && c.appointmentId !== 'inovare'
              ? c.appointmentId
              : appointmentId && appointmentId !== 'imagem' && appointmentId !== 'inovare'
                ? appointmentId
                : fallbackAppId
        };
      });

      setCredentials(normalizedData);
      setIsVerified(true);
      if (normalizedData.length > 0) {
        try {
          if (appointmentId && appointmentId !== 'imagem' && appointmentId !== 'inovare') {
            localStorage.setItem(
              `patient_access_credentials_${appointmentId}`,
              JSON.stringify(normalizedData)
            );
            if (authInfo?.token) {
              localStorage.setItem(`patient_access_token_${appointmentId}`, authInfo.token);
            }
            if (authInfo?.phoneDigits) {
              localStorage.setItem(`patient_access_phone_${appointmentId}`, authInfo.phoneDigits);
            }
          }
          if (isPublicRoute) {
            const todayStr = new Date().toLocaleDateString('sv-SE');
            let targetDate = todayStr;
            const firstAppId = normalizedData[0]?.appointmentId;
            const firstAppDateTime = normalizedData[0]?.appointmentDateTime;

            if (
              firstAppId &&
              firstAppId.length >= 13 &&
              (firstAppId.startsWith('INOV-') || firstAppId.startsWith('IMG-'))
            ) {
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
            localStorage.setItem(
              `patient_access_${clinicTheme.id}_last_credentials`,
              JSON.stringify({
                savedDate: todayStr,
                targetDate: targetDate,
                credentials: normalizedData
              })
            );
          }
        } catch {
          // Ignora falhas de gravação do localStorage
        }
      }
    },
    [appointmentId, clinicTheme.id, defaultPrefix, isPublicRoute]
  );

  const handleResetAccess = useCallback(() => {
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
  }, [appointmentId, clinicTheme.id]);

  const refreshCredentials = useCallback(
    async (silent = false) => {
      if (!appointmentId || appointmentId === 'imagem' || appointmentId === 'inovare') return;
      const params = new URLSearchParams(window.location.search);
      const token = (
        params.get('t') ||
        params.get('token') ||
        verifiedToken ||
        localStorage.getItem(`patient_access_token_${appointmentId}`) ||
        ''
      ).trim();
      const phoneDigits = (
        params.get('p') ||
        params.get('auth') ||
        verifiedPhoneDigits ||
        localStorage.getItem(`patient_access_phone_${appointmentId}`) ||
        ''
      ).trim();

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
        console.warn('[usePatientAccessAuth] Erro ao revalidar credenciais com o servidor:', err);
      } finally {
        if (!silent) setChallengeLoading(false);
      }
    },
    [appointmentId, saveCredentialsWithOfflineCache, verifiedPhoneDigits, verifiedToken]
  );

  const revalidatePublicCredentials = useCallback(
    async (patientCpf: string) => {
      if (!patientCpf) return;
      try {
        const response = await api.post<AccessCredential[]>(
          '/v1/access/lookup-by-cpf',
          {
            cpf: patientCpf,
            clinic: clinicTheme.id
          },
          {
            headers: {
              'X-Skip-Interceptor': 'true'
            }
          }
        );
        if (response.data && response.data.length > 0) {
          saveCredentialsWithOfflineCache(response.data);
        }
      } catch (err) {
        console.warn('[usePatientAccessAuth] Revalidação silenciosa pública falhou:', err);
      }
    },
    [clinicTheme.id, saveCredentialsWithOfflineCache]
  );

  // Desbloqueio automático via Magic Link (?t=...) ou parâmetro de telefone (?p=...)
  useEffect(() => {
    if (!appointmentId || appointmentId === 'imagem' || appointmentId === 'inovare') return;
    const params = new URLSearchParams(window.location.search);
    const tokenParam = (params.get('t') || params.get('token') || '').trim();
    const phoneDigitsParam = (params.get('p') || params.get('auth') || '').trim();

    if (tokenParam || phoneDigitsParam) {
      void refreshCredentials(false);
    }
  }, [appointmentId, refreshCredentials]);

  // Recupera credenciais em cache local (PWA Offline-First) com Revalidação em Background na inicialização
  const lastHydratedKeyRef = useRef<string | null>(null);
  useEffect(() => {
    const currentKey = appointmentId || clinicTheme.id;
    if (lastHydratedKeyRef.current === currentKey) return;
    lastHydratedKeyRef.current = currentKey;

    try {
      const todayStr = new Date().toLocaleDateString('sv-SE');

      if (isPublicRoute) {
        const cacheKey = `patient_access_${clinicTheme.id}_last_credentials`;
        const cachedRaw =
          localStorage.getItem(cacheKey) ||
          (clinicTheme.id === 'imagem'
            ? localStorage.getItem('patient_access_imagem_last_credentials')
            : null);
        if (cachedRaw) {
          try {
            const parsed = JSON.parse(cachedRaw);
            let creds: AccessCredential[] = [];
            let savedDate: string | null = null;

            if (
              parsed &&
              typeof parsed === 'object' &&
              !Array.isArray(parsed) &&
              Array.isArray(parsed.credentials)
            ) {
              creds = parsed.credentials;
              savedDate = parsed.savedDate;
            } else if (Array.isArray(parsed)) {
              creds = parsed;
            }

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
              localStorage.removeItem(cacheKey);
              setCredentials([]);
              setIsVerified(false);
              return;
            }

            if (creds.length > 0) {
              setCredentials(creds);
              setIsVerified(true);
              if (navigator.onLine) {
                const patientCpf = creds.find(c => c.cpf)?.cpf?.replace(/\D/g, '');
                if (patientCpf) {
                  void revalidatePublicCredentials(patientCpf);
                }
              }
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

            if (
              hasBlocked &&
              !savedToken &&
              !savedPhone &&
              !window.location.search.includes('t=') &&
              !window.location.search.includes('p=')
            ) {
              setIsVerified(false);
            } else {
              setCredentials(parsed);
              setIsVerified(true);
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
  }, [appointmentId, clinicTheme.id, isPublicRoute, refreshCredentials, revalidatePublicCredentials]);

  // Polling e sincronização de visibilidade
  useEffect(() => {
    const hasBlocked = credentials.some(c => c.credentialCode === 'BLOCKED_OUTSIDE_WINDOW');
    let interval: ReturnType<typeof setInterval> | null = null;
    if (hasBlocked && isVerified) {
      interval = setInterval(() => {
        if (navigator.onLine) {
          void refreshCredentials(true);
        }
      }, 20000);
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && navigator.onLine && isVerified) {
        if (isPublicRoute) {
          const patientCpf = credentials.find(c => c.cpf)?.cpf?.replace(/\D/g, '');
          if (patientCpf) {
            void revalidatePublicCredentials(patientCpf);
          }
        } else {
          void refreshCredentials(true);
        }
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      if (interval) clearInterval(interval);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [credentials, isVerified, isPublicRoute, refreshCredentials, revalidatePublicCredentials]);

  // Handlers para o desafio 2FA
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
      console.error('[usePatientAccessAuth] Falha no desafio de segurança:', err);
      let errorMsg = 'Código inválido. Tente novamente.';
      if (err && typeof err === 'object' && 'response' in err) {
        const axErr = err as { response?: { data?: { detail?: string; message?: string } } };
        if (axErr.response?.data?.detail) {
          errorMsg = axErr.response.data.detail;
        } else if (axErr.response?.data?.message) {
          errorMsg = axErr.response.data.message;
        }
      }
      setChallengeError(errorMsg);
      setDigits(['', '', '', '']);
      setTimeout(() => inputRefs[0].current?.focus(), 50);
    } finally {
      setChallengeLoading(false);
    }
  };

  return {
    isVerified,
    setIsVerified,
    digits,
    setDigits,
    inputRefs,
    challengeLoading,
    challengeError,
    setChallengeError,
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
    refreshCredentials,
    revalidatePublicCredentials,
    handleDigitChange,
    handleDigitKeyDown,
    handleUnlock
  };
}
