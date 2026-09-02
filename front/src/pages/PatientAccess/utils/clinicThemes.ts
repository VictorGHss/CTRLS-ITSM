/**
 * Configuração de temas e identidade visual dinâmica para multi-clínicas (Inovare e Clínica Imagem).
 */

export interface ClinicTheme {
  id: string;
  name: string;
  shortName: string;
  subtitle: string;
  logoText: string;
  logoUrl: string;
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
  phone: string;
  openingHours: string;
  instagramUrl: string;
  facebookUrl: string;
  whatsappUrl: string;
}

export const CLINIC_THEMES: Record<string, ClinicTheme> = {
  inovare: {
    id: 'inovare',
    name: 'Inovare – Serviços de Saúde',
    shortName: 'Inovare',
    subtitle: 'Pré-Cadastro & Cartão de Acesso',
    logoText: 'INOVARE',
    logoUrl: '/Logo.png',
    primaryColor: '#FFA145',
    primaryDarkColor: '#E08328',
    secondaryColor: '#FFD2A5',
    accentBgColor: 'bg-[#FFD2A5]/20',
    badgeBgColor: 'bg-[#FFD2A5]/50',
    badgeTextColor: 'text-amber-950',
    buttonGradient: 'from-[#FFA145] to-[#E08328]',
    address: 'R. Carlos Osternack, 111, Estrela, Ponta Grossa - PR, 84040-120',
    mapsUrl: 'https://maps.app.goo.gl/S2BaxmJFgr4YAjRT7',
    floorInfo: '1º Andar - Lado Direito',
    phone: '(42) 3026-2600',
    openingHours: 'Segunda a sexta, 08h – 12h e 13h – 18h30',
    instagramUrl: 'https://www.instagram.com/inovaress/',
    facebookUrl: 'https://www.facebook.com/inovarepg',
    whatsappUrl: 'https://wa.me/554230262601',
  },
  imagem: {
    id: 'imagem',
    name: 'Clínica Da Imagem - Unidade Inovare',
    shortName: 'Clínica Da Imagem',
    subtitle: 'Pré-Cadastro & Cartão de Acesso',
    logoText: 'CLÍNICA DA IMAGEM',
    logoUrl: '/logo-clinica-imagem.png',
    primaryColor: '#B8004B',
    primaryDarkColor: '#7A002E',
    secondaryColor: '#FDF2F4',
    accentBgColor: 'bg-rose-50',
    badgeBgColor: 'bg-rose-100/80',
    badgeTextColor: 'text-rose-800',
    buttonGradient: 'from-[#B8004B] to-[#7A002E]',
    address: 'R. Carlos Osternack, 111 - Centro - Ponta Grossa - PR - CEP 84040-120',
    mapsUrl: 'https://share.google/2TuHlAGsINfBLh00P',
    floorInfo: 'Térreo — Clínica da Imagem',
    phone: '(42) 3026-2620',
    openingHours: 'Segunda à sexta: 08h às 19h',
    instagramUrl: 'https://www.instagram.com/clinicadaimagempg?utm_source=ig_web_button_share_sheet&igsi=ZDNlZDc0MzIxNw==',
    facebookUrl: 'https://www.facebook.com/clinicadaimagempg?utm_source=ig&utm_medium=social&utm_content=link_in_bio',
    whatsappUrl: 'https://wa.me/554230262620',
  },
};

/**
 * Resolve o tema ativo da clínica com base nos query parameters, rota atual ou valor padrão (Inovare).
 */
export function resolveClinicTheme(urlSearchParams?: URLSearchParams, pathname?: string): ClinicTheme {
  const path = (pathname || window.location.pathname || '').toLowerCase();
  if (path.includes('/imagem') || path === '/imagem') {
    return CLINIC_THEMES.imagem;
  }

  if (urlSearchParams) {
    const clinicParam = (urlSearchParams.get('clinic') || urlSearchParams.get('c') || '').toLowerCase().trim();
    if (clinicParam.includes('imagem')) {
      return CLINIC_THEMES.imagem;
    }
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

  // === ESPECIALIDADES E SETORES ===
  "Ginecologia": "Recepção de Ginecologia (3º Andar)",
  "Ortopedia": "Recepção de Ortopedia (3º Andar - Direita)",
  "Oftalmologia": "Recepção de Oftalmologia (2º Andar)",
  "Inovare – Serviços de Saúde": "1º Andar - Lado Direito",
  "Inovare": "1º Andar - Lado Direito",

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

export interface DoctorSuggestion {
  name: string;
  location: string;
  specialty?: string;
}

export const DOCTOR_SUGGESTIONS: DoctorSuggestion[] = [
  // Ginecologia (3º Andar)
  { name: "Dra. Brenda Aguiar", location: "Recepção de Ginecologia (3º Andar)", specialty: "Ginecologia" },
  { name: "Dr. Carlos Batista", location: "Recepção de Ginecologia (3º Andar)", specialty: "Ginecologia" },
  { name: "Dr. Edson Delfrate", location: "Recepção de Ginecologia (3º Andar)", specialty: "Ginecologia" },
  { name: "Dr. Edson Felipe Grudinski", location: "Recepção de Ginecologia (3º Andar)", specialty: "Ginecologia" },
  { name: "Dr. Eduardo Serman", location: "Recepção de Ginecologia (3º Andar)", specialty: "Ginecologia" },
  { name: "Dra. Isabela Mongruel", location: "Recepção de Ginecologia (3º Andar)", specialty: "Ginecologia" },
  { name: "Dra. Lisa Paula Fernandes", location: "Recepção de Ginecologia (3º Andar)", specialty: "Ginecologia" },
  { name: "Dra. Tatyellen Dalzotto", location: "Recepção de Ginecologia (3º Andar)", specialty: "Ginecologia" },

  // Ortopedia (3º Andar - Direita)
  { name: "Dr. Carlos Miers", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Cristiano Gatelli", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Daniel Cartelli", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Franklin Hilgemberg", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Luis Felipe", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Magno Zanellato", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dra. Marina Polydoro", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Rafael Pançan", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Rodrigo Fávaro", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },

  // Oftalmologia (2º Andar)
  { name: "Dra. Cíntia Cenovicz", location: "Recepção de Oftalmologia (2º Andar)", specialty: "Oftalmologia" },
  { name: "Dra. Fernanda Cenovicz", location: "Recepção de Oftalmologia (2º Andar)", specialty: "Oftalmologia" },
  { name: "Dr. Marcelo Cenovicz", location: "Recepção de Oftalmologia (2º Andar)", specialty: "Oftalmologia" },
  { name: "Dr. Murilo Cenovicz", location: "Recepção de Oftalmologia (2º Andar)", specialty: "Oftalmologia" },

  // 2º Andar - Direita
  { name: "Dra. Ana Paula", location: "Recepção do 2º Andar (Direita)" },
  { name: "Dr. Carlos Henrique", location: "Recepção do 2º Andar (Direita)" },
  { name: "Dr. Eduardo Mattos", location: "Recepção do 2º Andar (Direita)" },
  { name: "Dra. Fabíola Moreira", location: "Recepção do 2º Andar (Direita)" },
  { name: "Dr. Giuliano Campanari", location: "Recepção do 2º Andar (Direita)" },
  { name: "Dr. Joelson Gulin", location: "Recepção do 2º Andar (Direita)" },
  { name: "Dra. Kelly Melina", location: "Recepção do 2º Andar (Direita)" },
  { name: "Dr. Luiz Strack", location: "Recepção do 2º Andar (Direita)" },
  { name: "Dra. Thais Fernanda", location: "Recepção do 2º Andar (Direita)" },
  { name: "Dra. Vania Gulin", location: "Recepção do 2º Andar (Direita)" },

  // 2º Andar - Esquerda
  { name: "Dr. Alexandre Acuña", location: "Recepção do 2º Andar (Esquerda)" },
  { name: "Dr. Claudio Solak", location: "Recepção do 2º Andar (Esquerda)" },
  { name: "Dr. Marcelo Schafranski", location: "Recepção do 2º Andar (Esquerda)" },
  { name: "Dr. Marcelo Valladão", location: "Recepção do 2º Andar (Esquerda)" },
  { name: "Dr. Marcos Marochi", location: "Recepção do 2º Andar (Esquerda)" },
  { name: "Dr. Rubens Sirtoli", location: "Recepção do 2º Andar (Esquerda)" },

  // 1º Andar - Direita
  { name: "Anestesistas", location: "Recepção do 1º Andar (Direita)" },
  { name: "Dr. Bruno Pançan", location: "Recepção do 1º Andar (Direita)" },
  { name: "Dr. João Felipe Bueno", location: "Recepção do 1º Andar (Direita)" },
  { name: "Dra. Karen Miyabukuro", location: "Recepção do 1º Andar (Direita)" },
  { name: "Dra. Liliana Pilatti", location: "Recepção do 1º Andar (Direita)" },
  { name: "Dr. Ricardo Zanetti", location: "Recepção do 1º Andar (Direita)" },
  { name: "Dr. Roberto Kravchychyn", location: "Recepção do 1º Andar (Direita)" },
  { name: "Dr. Victor Mauro", location: "Recepção do 1º Andar (Direita)" },

  // 1º Andar - Esquerda
  { name: "Dr. Alisson Fucio", location: "Recepção do 1º Andar (Esquerda)" },
  { name: "Dr. Carlos Koga", location: "Recepção do 1º Andar (Esquerda)" },
  { name: "Dr. Cesar Oda", location: "Recepção do 1º Andar (Esquerda)" },
  { name: "Dr. Eduardo Bisinella", location: "Recepção do 1º Andar (Esquerda)" },
  { name: "Dr. Irineu Zanellato", location: "Recepção do 1º Andar (Esquerda)" },
  { name: "Dr. Marcelo Tessari", location: "Recepção do 1º Andar (Esquerda)" },
  { name: "Dr. Ricardo Jeczmionski", location: "Recepção do 1º Andar (Esquerda)" },

  // Térreo
  { name: "Dr. Daniel Oda", location: "Térreo — Endoscopia", specialty: "Endoscopia" },
  { name: "Dr. Danilo Saad", location: "Térreo — Endoscopia", specialty: "Endoscopia" },
  { name: "Dra. Caroline Saad", location: "Térreo — Endoscopia", specialty: "Endoscopia" },
  { name: "Clínica da Imagem", location: "Térreo — Clínica da Imagem" },
  { name: "Clinipon", location: "Térreo — Clinipon" },

  // Especialidades e Setores Gerais
  { name: "Ginecologia", location: "Recepção de Ginecologia (3º Andar)" },
  { name: "Ortopedia", location: "Recepção de Ortopedia (3º Andar - Direita)" },
  { name: "Oftalmologia", location: "Recepção de Oftalmologia (2º Andar)" },
  { name: "Endoscopia", location: "Térreo — Endoscopia" },
  { name: "Recepção Geral / Central", location: "1º Andar - Lado Direito" }
];
