/**
 * Configuração de temas e identidade visual dinâmica para multi-clínicas (Inovare e Clínica Imagem).
 */

export interface ClinicTheme {
  id: string;
  name: string;
  shortName: string;
  subtitle: string;
  logoText: string;
  primaryColor: string;
  primaryDarkColor: string;
  secondaryColor: string;
  accentBgColor: string;
  badgeBgColor: string;
  badgeTextColor: string;
  buttonGradient: string;
  address: string;
  mapsUrl: string;
  floorInfo: string;
}

export const CLINIC_THEMES: Record<string, ClinicTheme> = {
  inovare: {
    id: 'inovare',
    name: 'Clínica Inovare - Serviços de Saúde',
    shortName: 'Inovare',
    subtitle: 'Cartão de Acesso Digital & Recepção',
    logoText: 'INOVARE',
    primaryColor: '#00875F',
    primaryDarkColor: '#00583F',
    secondaryColor: '#E6F4EA',
    accentBgColor: 'bg-emerald-50',
    badgeBgColor: 'bg-emerald-100/80',
    badgeTextColor: 'text-emerald-800',
    buttonGradient: 'from-[#00875F] to-[#00583F]',
    address: 'R. Carlos Osternack, 111 - 1º Andar, Estrela, Ponta Grossa - PR, 84040-120',
    mapsUrl: 'https://maps.app.goo.gl/S2BaxmJFgr4YAjRT7',
    floorInfo: '1º Andar - Lado Direito',
  },
  imagem: {
    id: 'imagem',
    name: 'Clínica Imagem - Diagnóstico Médico',
    shortName: 'Clínica Imagem',
    subtitle: 'Cartão de Acesso Digital & Recepção',
    logoText: 'CLÍNICA IMAGEM',
    primaryColor: '#0284C7',
    primaryDarkColor: '#0369A1',
    secondaryColor: '#E0F2FE',
    accentBgColor: 'bg-sky-50',
    badgeBgColor: 'bg-sky-100/80',
    badgeTextColor: 'text-sky-800',
    buttonGradient: 'from-[#0284C7] to-[#0369A1]',
    address: 'R. Carlos Osternack, 111 - Edifício Inovare, Ponta Grossa - PR, 84040-120',
    mapsUrl: 'https://maps.app.goo.gl/S2BaxmJFgr4YAjRT7',
    floorInfo: 'Edifício Inovare',
  },
};

/**
 * Resolve o tema ativo da clínica com base nos query parameters ou valor padrão (Inovare).
 */
export function resolveClinicTheme(urlSearchParams?: URLSearchParams): ClinicTheme {
  if (!urlSearchParams) {
    return CLINIC_THEMES.inovare;
  }
  const clinicParam = (urlSearchParams.get('clinic') || urlSearchParams.get('c') || '').toLowerCase().trim();
  if (clinicParam.includes('imagem')) {
    return CLINIC_THEMES.imagem;
  }
  return CLINIC_THEMES.inovare;
}
