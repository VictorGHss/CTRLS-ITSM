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

/**
 * Mapeamento oficial de localização por médico/especialista na Clínica Inovare.
 */
export const DOCTOR_LOCATIONS_MAP: Record<string, string> = {
  "Vania Gulin": "Recepção do 2º Andar (Direita)",
  "Anestesistas": "Recepção do 1º Andar (Direita)",
  "Marcelo Valladão": "Recepção Central",
  "Rubens Sirtoli": "Recepção Central",
  "Liliana Pilatti": "Recepção do 1º Andar (Direita)",
  "Cesar Oda": "Recepção do 1º Andar (Esquerda)",
  "Joelson Gulin": "Recepção do 2º Andar (Direita)",
  "Daniel Oda": "Recepção Central",
  "Victor Mauro": "Recepção do 1º Andar (Direita)",
  "Magno Zanellato": "Recepção do 1º Andar (Esquerda)",
  "Bruno Pançan": "Recepção do 1º Andar (Direita)",
  "Ricardo Zanetti": "Recepção do 1º Andar (Direita)",
  "Karen Miyabukuro": "Recepção do 1º Andar (Direita)",
  "Irineu Zanellato": "Recepção do 1º Andar (Esquerda)",
  "Luiz Strack": "Recepção do 2º Andar (Direita)",
  "Ana Paula": "Recepção do 1º Andar (Dra. Ana Paula)",
  "Giuliano Campanari": "Recepção de Dermatologia (2º Andar)",
  "Alexandre Acuña": "Recepção de Endocrinologia (1º Andar)",
  "Clinica da Imagem": "Recepção Central",
  "Clinipon": "Recepção Central",
  "Marcos Marochi": "Recepção Central",
  "Cíntia Cenovicz": "Recepção de Oftalmologia (Térreo / 1º Andar)",
  "Claudio Solak": "Recepção Central",
  "Danilo Saad": "Recepção Central",
  "Caroline Saad": "Recepção Central",
  "Carlos Batista": "Recepção de Ginecologia (3º Andar)",
  "Eduardo Serman": "Recepção Central",
  "Brenda Aguiar": "Recepção de Ginecologia (3º Andar)",
  "Isabela Mongruel": "Recepção de Ginecologia (3º Andar)",
  "Lisa Paula Fernandes": "Recepção de Ginecologia (3º Andar)",
  "Tatyellen Dalzotto": "Recepção de Ginecologia (3º Andar)",
  "João Felipe Bueno": "Recepção do 1º Andar (Direita)",
  "Marcelo Tessari": "Recepção do 1º Andar (Esquerda)",
  "Carlos Henrique": "Recepção do 2º Andar (Direita)",
  "Roberto Kravchychyn": "Recepção do 1º Andar (Direita)",
  "Marcelo Cenovicz": "Recepção de Oftalmologia (Térreo / 1º Andar)",
  "Murilo Cenovicz": "Recepção de Oftalmologia (Térreo / 1º Andar)",
  "Fernanda Cenovicz": "Recepção de Oftalmologia (Térreo / 1º Andar)",
  "Carlos Miers": "Recepção de Ortopedia (3º Andar)",
  "Cristiano Gatelli": "Recepção de Ortopedia (3º Andar)",
  "Daniel Cartelli": "Recepção de Ortopedia (3º Andar)",
  "Franklin Hilgemberg": "Recepção de Ortopedia (3º Andar)",
  "Luis Felipe": "Recepção de Ortopedia (3º Andar)",
  "Rafael Pançan": "Recepção de Ortopedia (3º Andar)",
  "Rodrigo Fávaro": "Recepção de Ortopedia (3º Andar)",
  "Marina Polydoro": "Recepção de Ortopedia (3º Andar)",
  "Eduardo Mattos": "Recepção do 2º Andar (Direita)",
  "Fabíola Moreira": "Recepção do 2º Andar (Direita)",
  "Thais Fernanda": "Recepção de Saúde Mental (Psicologia / Psiquiatria)",
  "Kelly Melina": "Recepção de Saúde Mental (Psicologia / Psiquiatria)",
  "Marcelo Schafranski": "Recepção Central",
  "Alisson Fucio": "Recepção do 1º Andar (Esquerda)",
  "Carlos Koga": "Recepção do 1º Andar (Esquerda)",
  "Eduardo Bisinella": "Recepção do 1º Andar (Esquerda)",
  "Ricardo Jeczmionski": "Recepção do 1º Andar (Esquerda)"
};

function normalizeName(str: string): string {
  return str
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/^(dr|dra|doutor|doutora)\.?\s+/i, '')
    .trim();
}

/**
 * Retorna o andar/localização exato do consultório baseado no nome do médico.
 */
export function resolveDoctorLocation(doctorName?: string, defaultFloor = '1º Andar - Lado Direito'): string {
  if (!doctorName || !doctorName.trim()) return defaultFloor;

  const normalizedInput = normalizeName(doctorName);
  
  for (const [key, location] of Object.entries(DOCTOR_LOCATIONS_MAP)) {
    const normalizedKey = normalizeName(key);
    if (normalizedInput.includes(normalizedKey) || normalizedKey.includes(normalizedInput)) {
      return location;
    }
  }

  return defaultFloor;
}
