/**
 * Funções utilitárias de formatação e máscaras para o módulo de Acesso do Paciente.
 */

export const maskCpf = (val: string): string => {
  return val
    .replace(/\D/g, '')
    .slice(0, 11)
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
};

export const maskPhone = (val: string): string => {
  if (!val) return '';
  let raw = val.trim();
  if (raw.startsWith('+55')) {
    raw = raw.slice(3);
  }
  raw = raw.replace(/\D/g, '');
  if (raw.startsWith('55') && (raw.length === 12 || raw.length === 13)) {
    raw = raw.slice(2);
  }
  raw = raw.slice(0, 11);
  if (raw.length <= 10) {
    return raw.replace(/(\d{2})(\d{4})(\d{0,4})/, '($1) $2-$3').replace(/-$/, '');
  }
  return raw.replace(/(\d{2})(\d{5})(\d{0,4})/, '($1) $2-$3').replace(/-$/, '');
};

export const maskDate = (val: string): string => {
  if (!val) return '';
  const trimmed = val.trim();

  // YYYY-MM-DD ou YYYY/MM/DD
  const ymdMatch = trimmed.match(/^(\d{4})[-/](\d{2})[-/](\d{2})/);
  if (ymdMatch) {
    return `${ymdMatch[3]}/${ymdMatch[2]}/${ymdMatch[1]}`;
  }

  // DD-MM-YYYY
  const dmyMatch = trimmed.match(/^(\d{2})-(\d{2})-(\d{4})/);
  if (dmyMatch) {
    return `${dmyMatch[1]}/${dmyMatch[2]}/${dmyMatch[3]}`;
  }

  return val
    .replace(/\D/g, '')
    .slice(0, 8)
    .replace(/(\d{2})(\d)/, '$1/$2')
    .replace(/(\d{2})(\d)/, '$1/$2');
};
