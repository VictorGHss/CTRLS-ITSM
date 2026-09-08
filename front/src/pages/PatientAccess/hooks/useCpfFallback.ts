import { useState } from 'react';
import type { AccessCredential } from '../types';
import api from '../../../services/api';
import { getApiErrorMessage } from '../../../lib/apiError';
import { isValidCpf } from '../utils/cpfValidator';

interface UseCpfFallbackProps {
  credentials: AccessCredential[];
  appointmentId?: string;
  clinicId: string;
  defaultPrefix: string;
  verifiedToken: string;
  verifiedPhoneDigits: string;
  setVerifiedToken: (t: string) => void;
  setVerifiedPhoneDigits: (p: string) => void;
  saveCredentialsWithOfflineCache: (
    data: AccessCredential[],
    authInfo?: { token?: string; phoneDigits?: string }
  ) => void;
  setCredentials: (creds: AccessCredential[]) => void;
}

export function useCpfFallback({
  credentials,
  appointmentId,
  clinicId,
  defaultPrefix,
  verifiedToken,
  verifiedPhoneDigits,
  setVerifiedToken,
  setVerifiedPhoneDigits,
  saveCredentialsWithOfflineCache,
  setCredentials
}: UseCpfFallbackProps) {
  const [cpfInput, setCpfInput] = useState<string>('');
  const [cpfSubmitLoading, setCpfSubmitLoading] = useState<boolean>(false);
  const [cpfSubmitError, setCpfSubmitError] = useState<string | null>(null);
  const [isEditingCpf, setIsEditingCpf] = useState<boolean>(false);
  const [editingCredential, setEditingCredential] = useState<AccessCredential | null>(null);

  const getActiveAppointmentId = (): string | undefined => {
    if (appointmentId && appointmentId !== 'imagem' && appointmentId !== 'inovare') {
      return appointmentId;
    }
    const fromCred = credentials.find(
      c => c.appointmentId && c.appointmentId !== 'imagem' && c.appointmentId !== 'inovare'
    )?.appointmentId;
    if (fromCred) return fromCred;
    const patientCred =
      credentials.find(c => c.userType === 'PATIENT' && c.cpf) || credentials.find(c => c.cpf);
    if (
      patientCred?.appointmentId &&
      patientCred.appointmentId !== 'imagem' &&
      patientCred.appointmentId !== 'inovare'
    ) {
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
    const targetId =
      editingCredential?.appointmentId ||
      missingCred?.appointmentId ||
      getActiveAppointmentId() ||
      appointmentId ||
      credentials[0]?.appointmentId;

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
      const validateRes = await api.post<{
        authorized: boolean;
        requiresCpfFallback?: boolean;
        message?: string;
      }>(
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
        setCpfSubmitError(
          validateRes.data?.message ||
            'CPF não encontrado ou inválido. Por favor, confira os números digitados.'
        );
        return;
      }

      const rootId =
        appointmentId && appointmentId !== 'inovare' && appointmentId !== 'imagem'
          ? appointmentId
          : targetId;
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
        console.warn(
          '[useCpfFallback] Busca por appointmentId após CPF falhou, tentando lookup por CPF:',
          fetchErr
        );
      }

      try {
        const lookupRes = await api.post<AccessCredential[]>('/v1/access/lookup-by-cpf', {
          cpf: cleanCpf,
          clinic: clinicId
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
        console.warn('[useCpfFallback] Lookup por CPF após validação falhou:', lookupErr);
      }
      setIsEditingCpf(false);
      setEditingCredential(null);
      setCpfInput('');
    } catch (err: unknown) {
      console.error('[useCpfFallback] Falha ao enviar CPF:', err);
      const msg = getApiErrorMessage(err, 'Ocorreu um erro ao salvar o CPF. Tente novamente.');
      setCpfSubmitError(msg);
    } finally {
      setCpfSubmitLoading(false);
    }
  };

  return {
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
  };
}
