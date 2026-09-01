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
 * Mapeamento oficial e refinado de localização por médico/especialista na Clínica Inovare.
 */
export const DOCTOR_LOCATIONS_MAP: Record<string, string> = {
  // === 1º ANDAR — LADO DIREITO ===
  "Liliana Pilatti": "Recepção do 1º Andar (Direita)",
  "Victor Mauro": "Recepção do 1º Andar (Direita)",
  "Bruno Pançan": "Recepção do 1º Andar (Direita)",
  "Ricardo Zanetti": "Recepção do 1º Andar (Direita)",
  "Karen Miyabukuro": "Recepção do 1º Andar (Direita)",
  "João Felipe Bueno": "Recepção do 1º Andar (Direita)",
  "Roberto Kravchychyn": "Recepção do 1º Andar (Direita)",
  "Anestesistas": "Recepção do 1º Andar (Direita)",

  // === 1º ANDAR — LADO ESQUERDO ===
  "Cesar Oda": "Recepção do 1º Andar (Esquerda)",
  "Irineu Zanellato": "Recepção do 1º Andar (Esquerda)",
  "Marcelo Tessari": "Recepção do 1º Andar (Esquerda)",
  "Alisson Fucio": "Recepção do 1º Andar (Esquerda)",
  "Carlos Koga": "Recepção do 1º Andar (Esquerda)",
  "Eduardo Bisinella": "Recepção do 1º Andar (Esquerda)",
  "Ricardo Jeczmionski": "Recepção do 1º Andar (Esquerda)",

  // === 2º ANDAR — LADO DIREITO ===
  "Vania Gulin": "Recepção do 2º Andar (Direita)",
  "Joelson Gulin": "Recepção do 2º Andar (Direita)",
  "Luiz Strack": "Recepção do 2º Andar (Direita)",
  "Carlos Henrique": "Recepção do 2º Andar (Direita)",
  "Eduardo Mattos": "Recepção do 2º Andar (Direita)",
  "Fabíola Moreira": "Recepção do 2º Andar (Direita)",
  "Ana Paula": "Recepção do 2º Andar (Direita)",
  "Giuliano Campanari": "Recepção do 2º Andar (Direita)",
  "Kelly Melina": "Recepção do 2º Andar (Direita)",
  "Thais Fernanda": "Recepção do 2º Andar (Direita)",

  // === 2º ANDAR — LADO ESQUERDO ===
  "Marcelo Valladão": "Recepção do 2º Andar (Esquerda)",
  "Rubens Sirtoli": "Recepção do 2º Andar (Esquerda)",
  "Marcelo Schafranski": "Recepção do 2º Andar (Esquerda)",
  "Marcos Marochi": "Recepção do 2º Andar (Esquerda)",
  "Alexandre Acuña": "Recepção do 2º Andar (Esquerda)",
  "Claudio Solak": "Recepção do 2º Andar (Esquerda)",

  // === 2º ANDAR — OFTALMOLOGIA ===
  "Cíntia Cenovicz": "Recepção de Oftalmologia (2º Andar)",
  "Marcelo Cenovicz": "Recepção de Oftalmologia (2º Andar)",
  "Murilo Cenovicz": "Recepção de Oftalmologia (2º Andar)",
  "Fernanda Cenovicz": "Recepção de Oftalmologia (2º Andar)",

  // === 3º ANDAR — GINECOLOGIA ===
  "Carlos Batista": "Recepção de Ginecologia (3º Andar)",
  "Brenda Aguiar": "Recepção de Ginecologia (3º Andar)",
  "Isabela Mongruel": "Recepção de Ginecologia (3º Andar)",
  "Lisa Paula Fernandes": "Recepção de Ginecologia (3º Andar)",
  "Tatyellen Dalzotto": "Recepção de Ginecologia (3º Andar)",
  "Edson Felipe Grudinski": "Recepção de Ginecologia (3º Andar)",
  "Edson Felipe Grudinski Delfrate": "Recepção de Ginecologia (3º Andar)",
  "Edson Delfrate": "Recepção de Ginecologia (3º Andar)",
  "Eduardo Serman": "Recepção de Ginecologia (3º Andar)",

  // === 3º ANDAR — ORTOPEDIA (DIREITA) ===
  "Carlos Miers": "Recepção de Ortopedia (3º Andar - Direita)",
  "Cristiano Gatelli": "Recepção de Ortopedia (3º Andar - Direita)",
  "Daniel Cartelli": "Recepção de Ortopedia (3º Andar - Direita)",
  "Franklin Hilgemberg": "Recepção de Ortopedia (3º Andar - Direita)",
  "Luis Felipe": "Recepção de Ortopedia (3º Andar - Direita)",
  "Rafael Pançan": "Recepção de Ortopedia (3º Andar - Direita)",
  "Rodrigo Fávaro": "Recepção de Ortopedia (3º Andar - Direita)",
  "Marina Polydoro": "Recepção de Ortopedia (3º Andar - Direita)",
  "Magno Zanellato": "Recepção de Ortopedia (3º Andar - Direita)",

  // === TÉRREO ===
  "Daniel Oda": "Térreo — Endoscopia",
  "Danilo Saad": "Térreo — Endoscopia",
  "Caroline Saad": "Térreo — Endoscopia",
  "Clinica da Imagem": "Térreo — Clínica da Imagem",
  "Clinipon": "Térreo — Clinipon"
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
