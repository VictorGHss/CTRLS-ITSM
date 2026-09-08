/**
 * Tipos e interfaces compartilhados para o módulo de Acesso do Paciente (Portal Web).
 */

export interface AccessCredential {
  appointmentId?: string;
  name: string;
  userType: 'PATIENT' | 'COMPANION';
  locator: string;
  credentialCode: string;
  cpf?: string;
  doctorName?: string;
  appointmentDateTime?: string;
  opensAt?: string;
  closesAt?: string;
  id?: string;
}

export interface CompanionEntry {
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

export const formatCpf = (cpf?: string) => {
  if (!cpf) return '';
  const clean = cpf.replace(/\D/g, '');
  if (clean.length !== 11) return cpf;
  return `${clean.substring(0, 3)}.${clean.substring(3, 6)}.${clean.substring(6, 9)}-${clean.substring(9)}`;
};

