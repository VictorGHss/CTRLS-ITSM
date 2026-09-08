import { useState, useRef, useMemo, useEffect } from 'react';
import { DOCTOR_SUGGESTIONS, type DoctorSuggestion } from '../utils/clinicThemes';

export function useDoctorAutocomplete(initialDoctor = '') {
  const [doctorInput, setDoctorInput] = useState<string>(initialDoctor);
  const [selectedLocation, setSelectedLocation] = useState<string | null>(null);
  const [isDoctorDropdownOpen, setIsDoctorDropdownOpen] = useState<boolean>(false);
  const doctorDropdownRef = useRef<HTMLDivElement>(null);

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

  return {
    doctorInput,
    setDoctorInput,
    selectedLocation,
    setSelectedLocation,
    isDoctorDropdownOpen,
    setIsDoctorDropdownOpen,
    doctorDropdownRef,
    filteredDoctors,
    handleSelectDoctor
  };
}
