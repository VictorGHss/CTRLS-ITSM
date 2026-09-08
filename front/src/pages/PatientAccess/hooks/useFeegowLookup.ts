import { useState, useEffect, useRef, useCallback } from 'react';
import api from '../../../services/api';
import { maskPhone, maskDate } from '../utils/masks';
import type { FeegowAppointmentItem, FeegowLookupResponse } from '../types';

interface UseFeegowLookupProps {
  cpf: string;
  clinicId: string;
  name: string;
  phone: string;
  birthDate: string;
  manualDoctorMode: boolean;
  setName: (v: string) => void;
  setPhone: (v: string) => void;
  setBirthDate: (v: string) => void;
  onAppointmentSelected?: (appt: FeegowAppointmentItem) => void;
}

export function useFeegowLookup({
  cpf,
  clinicId,
  name,
  phone,
  birthDate,
  manualDoctorMode,
  setName,
  setPhone,
  setBirthDate,
  onAppointmentSelected
}: UseFeegowLookupProps) {
  const [isSearchingFeegow, setIsSearchingFeegow] = useState(false);
  const [feegowLookupDone, setFeegowLookupDone] = useState(false);
  const [feegowAppointments, setFeegowAppointments] = useState<FeegowAppointmentItem[]>([]);
  const [selectedFeegowApptId, setSelectedFeegowApptId] = useState<string | null>(null);

  // Mantém refs atualizadas para evitar re-execução do efeito ao digitar nome/telefone
  const formStateRef = useRef({ name, phone, birthDate, manualDoctorMode, onAppointmentSelected });
  useEffect(() => {
    formStateRef.current = { name, phone, birthDate, manualDoctorMode, onAppointmentSelected };
  });

  const selectFeegowAppointment = useCallback((appt: FeegowAppointmentItem) => {
    setSelectedFeegowApptId(appt.appointmentId);
    if (formStateRef.current.onAppointmentSelected) {
      formStateRef.current.onAppointmentSelected(appt);
    }
  }, []);

  useEffect(() => {
    const cleanCpf = cpf.replace(/\D/g, '');
    if (cleanCpf.length !== 11) {
      setFeegowAppointments([]);
      setSelectedFeegowApptId(null);
      setFeegowLookupDone(false);
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
            clinic: clinicId
          },
          {
            headers: { 'X-Skip-Interceptor': 'true' }
          }
        );

        if (!isMounted) return;

        if (resp.data && resp.data.found) {
          setFeegowLookupDone(true);
          const current = formStateRef.current;

          if (resp.data.patientName && (!current.name.trim() || !current.manualDoctorMode)) {
            setName(resp.data.patientName);
          }
          if (resp.data.phone && (!current.phone.trim() || !current.manualDoctorMode)) {
            setPhone(maskPhone(resp.data.phone));
          }
          if (resp.data.birthDate && (!current.birthDate.trim() || !current.manualDoctorMode)) {
            setBirthDate(maskDate(resp.data.birthDate));
          }

          const appts = resp.data.appointments || [];
          setFeegowAppointments(appts);

          if (appts.length > 0 && !current.manualDoctorMode) {
            const chosen = appts.find(a => a.isToday) || appts[0];
            selectFeegowAppointment(chosen);
          }
        }
      } catch (err: unknown) {
        console.warn('[useFeegowLookup] Falha defensiva ao consultar Feegow:', err);
      } finally {
        if (isMounted) setIsSearchingFeegow(false);
      }
    }, 450);

    return () => {
      isMounted = false;
      clearTimeout(timer);
    };
  }, [cpf, clinicId, setName, setPhone, setBirthDate, selectFeegowAppointment]);

  const resetLookup = useCallback(() => {
    setFeegowAppointments([]);
    setSelectedFeegowApptId(null);
    setFeegowLookupDone(false);
  }, []);

  return {
    isSearchingFeegow,
    feegowLookupDone,
    feegowAppointments,
    selectedFeegowApptId,
    setSelectedFeegowApptId,
    selectFeegowAppointment,
    resetLookup
  };
}
