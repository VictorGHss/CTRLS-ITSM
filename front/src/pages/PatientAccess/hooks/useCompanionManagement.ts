import { useState, useCallback } from 'react';
import type { AccessCredential } from '../types';
import api from '../../../services/api';
import { getApiErrorMessage } from '../../../lib/apiError';
import { isValidCpf } from '../utils/cpfValidator';

interface UseCompanionManagementProps {
  credentials: AccessCredential[];
  targetAppointmentId?: string;
  verifiedToken: string;
  verifiedPhoneDigits: string;
  onCredentialsUpdated: (creds: AccessCredential[]) => void;
  onScrollToCard: (index: number) => void;
}

export function useCompanionManagement({
  credentials,
  targetAppointmentId,
  verifiedToken,
  verifiedPhoneDigits,
  onCredentialsUpdated,
  onScrollToCard
}: UseCompanionManagementProps) {
  const [isCompanionModalOpen, setIsCompanionModalOpen] = useState<boolean>(false);
  const [companionName, setCompanionName] = useState<string>('');
  const [companionCpf, setCompanionCpf] = useState<string>('');
  const [companionBirthDate, setCompanionBirthDate] = useState<string>('');
  const [companionSubmitLoading, setCompanionSubmitLoading] = useState<boolean>(false);
  const [companionSubmitError, setCompanionSubmitError] = useState<string | null>(null);

  const resetCompanionForm = useCallback(() => {
    setIsCompanionModalOpen(false);
    setCompanionName('');
    setCompanionCpf('');
    setCompanionBirthDate('');
    setCompanionSubmitError(null);
  }, []);

  const handleCompanionSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

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
            doctorName: credentials[0]?.doctorName || 'Recepção Central',
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
          console.warn('[useCompanionManagement] Fallback GET após cadastro:', getErr);
        }
      }

      if (updatedList.length > 0) {
        onCredentialsUpdated(updatedList);
        resetCompanionForm();
        setTimeout(() => {
          onScrollToCard(updatedList.length - 1);
        }, 300);
      } else {
        resetCompanionForm();
      }
    } catch (err: unknown) {
      console.error('[useCompanionManagement] Falha ao cadastrar acompanhante:', err);
      const apiMsg = getApiErrorMessage(err, 'Erro ao cadastrar acompanhante. Tente novamente.');
      setCompanionSubmitError(apiMsg);
    } finally {
      setCompanionSubmitLoading(false);
    }
  };

  return {
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
  };
}
