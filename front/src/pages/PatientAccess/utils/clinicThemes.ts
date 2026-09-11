/**
 * Configuração de temas e identidade visual dinâmica para portais e clínicas.
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
  portal: {
    id: 'portal',
    name: 'Portal de Atendimento e Acesso',
    shortName: 'Portal de Acesso',
    subtitle: 'Pré-Cadastro & Cartão de Acesso',
    logoText: 'PORTAL DE ACESSO',
    logoUrl: '/Logo.png',
    primaryColor: '#FFA145',
    primaryDarkColor: '#E08328',
    secondaryColor: '#FFD2A5',
    accentBgColor: 'bg-[#FFD2A5]/20',
    badgeBgColor: 'bg-[#FFD2A5]/50',
    badgeTextColor: 'text-amber-950',
    buttonGradient: 'from-[#FFA145] to-[#E08328]',
    address: 'Edifício Corporativo — Ponta Grossa - PR',
    mapsUrl: '',
    floorInfo: 'Recepção Central',
    phone: '',
    openingHours: 'Segunda a sexta, 08h – 18h30',
    instagramUrl: '',
    facebookUrl: '',
    whatsappUrl: '',
  },
  imagem: {
    id: 'imagem',
    name: 'Clínica Da Imagem',
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
    instagramUrl: 'https://www.instagram.com/clinicadaimagempg',
    facebookUrl: 'https://www.facebook.com/clinicadaimagempg',
    whatsappUrl: 'https://wa.me/5542999876740',
  },
};

/**
 * Resolve o tema ativo da clínica com base nos query parameters, rota atual ou valor padrão (portal).
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

  return CLINIC_THEMES.portal;
}

/**
 * Mapeamento oficial e refinado de localização por médico/especialista no edifício.
 */
export const DOCTOR_LOCATIONS_MAP: Record<string, string> = {
  // === 1º ANDAR — LADO DIREITO ===
  "Liliana Pilatti": "Recepção do 1º Andar (Direita)",
  "Victor Mauro": "Recepção do 1º Andar (Direita)",
  "Bruno Pançan": "Recepção do 1º Andar (Direita)",
  "Bruno Pancan": "Recepção do 1º Andar (Direita)",
  "Ricardo Zanetti": "Recepção do 1º Andar (Direita)",
  "Karen Miyabukuro": "Recepção do 1º Andar (Direita)",
  "Karen Kono Miyabukuro": "Recepção do 1º Andar (Direita)",
  "João Felipe Bueno": "Recepção do 1º Andar (Direita)",
  "Joao Felipe Bueno": "Recepção do 1º Andar (Direita)",
  "João Felipe Lara Bueno": "Recepção do 1º Andar (Direita)",
  "Roberto Kravchychyn": "Recepção do 1º Andar (Direita)",
  "Anestesistas": "Recepção do 1º Andar (Direita)",
  "Anestesiologia": "Recepção do 1º Andar (Direita)",
  "Nefrologia": "Recepção do 1º Andar (Direita)",
  "Cirurgia Vascular": "Recepção do 1º Andar (Direita)",
  "Cirurgia Plástica": "Recepção do 1º Andar (Direita)",
  "Odontologia": "Recepção do 1º Andar (Direita)",

  // === 1º ANDAR — LADO ESQUERDO ===
  "Cesar Oda": "Recepção do 1º Andar (Esquerda)",
  "Irineu Zanellato": "Recepção do 1º Andar (Esquerda)",
  "Marcelo Tessari": "Recepção do 1º Andar (Esquerda)",
  "Alisson Fucio": "Recepção do 1º Andar (Esquerda)",
  "Alisson Vinicius Emerique Fucio": "Recepção do 1º Andar (Esquerda)",
  "Carlos Koga": "Recepção do 1º Andar (Esquerda)",
  "Carlos Heidi Koga": "Recepção do 1º Andar (Esquerda)",
  "Eduardo Bisinella": "Recepção do 1º Andar (Esquerda)",
  "Ricardo Jeczmionski": "Recepção do 1º Andar (Esquerda)",
  "Ricardo Angelo Jeczmionski": "Recepção do 1º Andar (Esquerda)",
  "Urologia": "Recepção do 1º Andar (Esquerda)",
  "Neurocirurgia": "Recepção do 1º Andar (Esquerda)",
  "Cirurgia do Aparelho Digestivo": "Recepção do 1º Andar (Esquerda)",
  "Cirurgia do Ap Digestivo": "Recepção do 1º Andar (Esquerda)",

  // === 2º ANDAR — LADO DIREITO ===
  "Vania Gulin": "Recepção do 2º Andar (Direita)",
  "Joelson Gulin": "Recepção do 2º Andar (Direita)",
  "Luiz Strack": "Recepção do 2º Andar (Direita)",
  "Carlos Henrique": "Recepção do 2º Andar (Direita)",
  "Eduardo Mattos": "Recepção do 2º Andar (Direita)",
  "Fabíola Moreira": "Recepção do 2º Andar (Direita)",
  "Fabiola Moreira": "Recepção do 2º Andar (Direita)",
  "Ana Paula": "Recepção do 2º Andar (Direita)",
  "Ana Paula Costa de Pádua": "Recepção do 2º Andar (Direita)",
  "Giuliano Campanari": "Recepção do 2º Andar (Direita)",
  "Kelly Melina": "Recepção do 2º Andar (Direita)",
  "Thais Fernanda": "Recepção do 2º Andar (Direita)",
  "Alergia e Imunologia": "Recepção do 2º Andar (Direita)",
  "Neurologia": "Recepção do 2º Andar (Direita)",
  "Pediatria": "Recepção do 2º Andar (Direita)",
  "Ortopedia Pediátrica": "Recepção do 2º Andar (Direita)",
  "Ortopedia Pediatrica": "Recepção do 2º Andar (Direita)",
  "Dermatologia": "Recepção do 2º Andar (Direita)",
  "Psicologia": "Recepção do 2º Andar (Direita)",
  "Psiquiatria": "Recepção do 2º Andar (Direita)",
  "Clínica Geral": "Recepção do 2º Andar (Direita)",
  "Clinica Geral": "Recepção do 2º Andar (Direita)",

  // === 2º ANDAR — LADO ESQUERDO ===
  "Marcelo Valladão": "Recepção do 2º Andar (Esquerda)",
  "Marcelo Valladao": "Recepção do 2º Andar (Esquerda)",
  "Rubens Sirtoli": "Recepção do 2º Andar (Esquerda)",
  "Marcelo Schafranski": "Recepção do 2º Andar (Esquerda)",
  "Marcelo Derbli Schafranski": "Recepção do 2º Andar (Esquerda)",
  "Marcos Marochi": "Recepção do 2º Andar (Esquerda)",
  "Alexandre Acuña": "Recepção do 2º Andar (Esquerda)",
  "Alexandre Acuna": "Recepção do 2º Andar (Esquerda)",
  "Alexandre Barão Acuña": "Recepção do 2º Andar (Esquerda)",
  "Claudio Solak": "Recepção do 2º Andar (Esquerda)",
  "Cardiologia": "Recepção do 2º Andar (Esquerda)",
  "Endocrinologia": "Recepção do 2º Andar (Esquerda)",
  "Reumatologia": "Recepção do 2º Andar (Esquerda)",

  // === 2º ANDAR — OFTALMOLOGIA (ESQUERDA) ===
  "Cíntia Cenovicz": "Recepção de Oftalmologia (2º Andar - Esquerda)",
  "Cintia Cenovicz": "Recepção de Oftalmologia (2º Andar - Esquerda)",
  "Marcelo Cenovicz": "Recepção de Oftalmologia (2º Andar - Esquerda)",
  "Murilo Cenovicz": "Recepção de Oftalmologia (2º Andar - Esquerda)",
  "Fernanda Cenovicz": "Recepção de Oftalmologia (2º Andar - Esquerda)",
  "Oftalmologia": "Recepção de Oftalmologia (2º Andar - Esquerda)",
  "Fonoaudiologia": "Recepção de Oftalmologia (2º Andar - Esquerda)",

  // === 3º ANDAR — GINECOLOGIA (ESQUERDA) ===
  "Carlos Batista": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Carlos Alberto Batista": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Brenda Aguiar": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Brenda de Almeida Aguiar": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Isabela Mongruel": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Isabela Baumel Mongruel": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Lisa Paula Fernandes": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Tatyellen Dalzotto": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Tatyelen Dalzotto": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Edson Felipe Grudinski": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Edson Felipe Grudinski Delfrate": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Edson Delfrate": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Eduardo Serman": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Ginecologia": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Obstetrícia": "Recepção de Ginecologia (3º Andar - Esquerda)",
  "Obstetricia": "Recepção de Ginecologia (3º Andar - Esquerda)",

  // === 3º ANDAR — ORTOPEDIA (DIREITA) ===
  "Carlos Miers": "Recepção de Ortopedia (3º Andar - Direita)",
  "Cristiano Gatelli": "Recepção de Ortopedia (3º Andar - Direita)",
  "Daniel Cartelli": "Recepção de Ortopedia (3º Andar - Direita)",
  "Franklin Hilgemberg": "Recepção de Ortopedia (3º Andar - Direita)",
  "Luis Felipe": "Recepção de Ortopedia (3º Andar - Direita)",
  "Rafael Pançan": "Recepção de Ortopedia (3º Andar - Direita)",
  "Rafael Pancan": "Recepção de Ortopedia (3º Andar - Direita)",
  "Rodrigo Fávaro": "Recepção de Ortopedia (3º Andar - Direita)",
  "Rodrigo Favaro": "Recepção de Ortopedia (3º Andar - Direita)",
  "Marina Polydoro": "Recepção de Ortopedia (3º Andar - Direita)",
  "Magno Zanellato": "Recepção de Ortopedia (3º Andar - Direita)",
  "Cirurgia Torácica": "Recepção de Ortopedia (3º Andar - Direita)",
  "Cirurgia Toracica": "Recepção de Ortopedia (3º Andar - Direita)",
  "Pneumologia": "Recepção de Ortopedia (3º Andar - Direita)",
  "Ortopedia": "Recepção de Ortopedia (3º Andar - Direita)",

  // === TÉRREO ===
  "Daniel Oda": "Térreo — Endoscopia",
  "Danilo Saad": "Térreo — Endoscopia",
  "Caroline Saad": "Térreo — Endoscopia",
  "Caroline Tatim Saad": "Térreo — Endoscopia",
  "Endoscopia": "Térreo — Endoscopia",
  "Gastroenterologia": "Térreo — Endoscopia",
  "Clinica da Imagem": "Térreo — Clínica da Imagem",
  "Clínica da Imagem": "Térreo — Clínica da Imagem",
  "Clinipon": "Térreo — Clinipon",

  // === FALLBACK CENTRAL ===
  "Recepção Geral / Central": "1º Andar - Lado Direito"
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
 * Retorna o andar/localização exato do consultório baseado no nome do médico ou especialidade.
 * Utiliza verificação de substring, tokenização por palavras e verificação por sobrenome.
 */
export function resolveDoctorLocation(doctorName?: string, defaultFloor = '1º Andar - Lado Direito'): string {
  if (!doctorName || !doctorName.trim()) return defaultFloor;

  const normalizedInput = normalizeName(doctorName);
  if (!normalizedInput || normalizedInput === 'portal' || normalizedInput === 'central') {
    return defaultFloor;
  }

  // 1. Verificação direta por substring
  for (const [key, location] of Object.entries(DOCTOR_LOCATIONS_MAP)) {
    const normalizedKey = normalizeName(key);
    if (normalizedInput.includes(normalizedKey) || normalizedKey.includes(normalizedInput)) {
      return location;
    }
  }

  // 2. Verificação por tokens/palavras significativas (ex: "Brenda Aguiar" casa com "Brenda de Almeida Aguiar")
  const stopWords = new Set(['de', 'da', 'do', 'dos', 'das', 'e']);
  const inputTokens = normalizedInput.split(/\s+/).filter(t => t.length > 2 && !stopWords.has(t));

  for (const [key, location] of Object.entries(DOCTOR_LOCATIONS_MAP)) {
    const keyTokens = normalizeName(key).split(/\s+/).filter(t => t.length > 2 && !stopWords.has(t));
    if (keyTokens.length > 0 && keyTokens.every(kToken => inputTokens.includes(kToken))) {
      return location;
    }
  }

  // 3. Verificação pelo último sobrenome significativo (se o sobrenome for único na clínica)
  for (const [key, location] of Object.entries(DOCTOR_LOCATIONS_MAP)) {
    const keyTokens = normalizeName(key).split(/\s+/).filter(t => t.length > 3 && !stopWords.has(t));
    if (keyTokens.length > 0) {
      const lastKeyToken = keyTokens[keyTokens.length - 1];
      if (inputTokens.includes(lastKeyToken)) {
        return location;
      }
    }
  }

  return defaultFloor;
}

/**
 * Retorna a especialidade do médico para enriquecimento visual do cartão de acesso.
 */
export function resolveDoctorSpecialty(doctorName?: string): string {
  if (!doctorName || !doctorName.trim()) return '';

  const normalizedInput = normalizeName(doctorName);
  const stopWords = new Set(['de', 'da', 'do', 'dos', 'das', 'e']);
  const inputTokens = normalizedInput.split(/\s+/).filter(t => t.length > 2 && !stopWords.has(t));

  for (const doc of DOCTOR_SUGGESTIONS) {
    if (!doc.specialty) continue;
    const docNormalized = normalizeName(doc.name);
    if (normalizedInput.includes(docNormalized) || docNormalized.includes(normalizedInput)) {
      return doc.specialty;
    }
    const docTokens = docNormalized.split(/\s+/).filter(t => t.length > 2 && !stopWords.has(t));
    if (docTokens.length > 0 && docTokens.every(k => inputTokens.includes(k))) {
      return doc.specialty;
    }
  }

  return '';
}

export interface DoctorSuggestion {
  name: string;
  location: string;
  specialty?: string;
}

export const DOCTOR_SUGGESTIONS: DoctorSuggestion[] = [
  // Ginecologia (3º Andar - Esquerda)
  { name: "Dra. Brenda Silva", location: "Recepção de Ginecologia (3º Andar - Esquerda)", specialty: "Ginecologia" },
  { name: "Dr. Carlos Santos", location: "Recepção de Ginecologia (3º Andar - Esquerda)", specialty: "Ginecologia" },
  { name: "Dr. Edson Souza", location: "Recepção de Ginecologia (3º Andar - Esquerda)", specialty: "Ginecologia" },
  { name: "Dr. Edson Felipe", location: "Recepção de Ginecologia (3º Andar - Esquerda)", specialty: "Ginecologia" },
  { name: "Dr. Eduardo Lima", location: "Recepção de Ginecologia (3º Andar - Esquerda)", specialty: "Ginecologia" },
  { name: "Dra. Isabela Costa", location: "Recepção de Ginecologia (3º Andar - Esquerda)", specialty: "Ginecologia" },
  { name: "Dra. Lisa Fernandes", location: "Recepção de Ginecologia (3º Andar - Esquerda)", specialty: "Ginecologia" },
  { name: "Dra. Tatyellen Ramos", location: "Recepção de Ginecologia (3º Andar - Esquerda)", specialty: "Ginecologia" },

  // Ortopedia (3º Andar - Direita)
  { name: "Dr. Carlos Mendes", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Cristiano Rocha", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Daniel Castro", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Franklin Ribeiro", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Luis Felipe", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Magno Prado", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Cirurgia Torácica e Pneumologia" },
  { name: "Dra. Marina Pires", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Rafael Faria", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },
  { name: "Dr. Rodrigo Martins", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia" },

  // Oftalmologia (2º Andar - Esquerda)
  { name: "Dra. Cíntia Carvalho", location: "Recepção de Oftalmologia (2º Andar - Esquerda)", specialty: "Fonoaudiologia" },
  { name: "Dra. Fernanda Cardoso", location: "Recepção de Oftalmologia (2º Andar - Esquerda)", specialty: "Oftalmologia" },
  { name: "Dr. Marcelo Cunha", location: "Recepção de Oftalmologia (2º Andar - Esquerda)", specialty: "Oftalmologia" },
  { name: "Dr. Murilo Castro", location: "Recepção de Oftalmologia (2º Andar - Esquerda)", specialty: "Oftalmologia" },

  // 2º Andar - Direita
  { name: "Dra. Ana Paula", location: "Recepção do 2º Andar (Direita)", specialty: "Clínica Geral" },
  { name: "Dr. Carlos Henrique", location: "Recepção do 2º Andar (Direita)", specialty: "Neurologia" },
  { name: "Dr. Eduardo Mattos", location: "Recepção do 2º Andar (Direita)", specialty: "Ortopedia Pediátrica" },
  { name: "Dra. Fabíola Moreira", location: "Recepção do 2º Andar (Direita)", specialty: "Pediatria" },
  { name: "Dr. Giuliano Moreira", location: "Recepção do 2º Andar (Direita)", specialty: "Dermatologia" },
  { name: "Dr. Joelson Ribeiro", location: "Recepção do 2º Andar (Direita)", specialty: "Cirurgia do Ap Digestivo" },
  { name: "Dra. Kelly Silva", location: "Recepção do 2º Andar (Direita)", specialty: "Psiquiatria" },
  { name: "Dr. Luiz Strack", location: "Recepção do 2º Andar (Direita)", specialty: "Clínica Geral" },
  { name: "Dra. Thais Fernanda", location: "Recepção do 2º Andar (Direita)", specialty: "Psicologia" },
  { name: "Dra. Vania Gulin", location: "Recepção do 2º Andar (Direita)", specialty: "Alergia e Imunologia" },

  // 2º Andar - Esquerda
  { name: "Dr. Alexandre Acuña", location: "Recepção do 2º Andar (Esquerda)", specialty: "Endocrinologia" },
  { name: "Dr. Claudio Solak", location: "Recepção do 2º Andar (Esquerda)", specialty: "Gastroenterologia" },
  { name: "Dr. Marcelo Silva", location: "Recepção do 2º Andar (Esquerda)", specialty: "Reumatologia" },
  { name: "Dr. Marcelo Ferreira", location: "Recepção do 2º Andar (Esquerda)", specialty: "Cardiologia" },
  { name: "Dr. Marcos Marochi", location: "Recepção do 2º Andar (Esquerda)", specialty: "Exames Cardiológicos" },
  { name: "Dr. Rubens Sirtoli", location: "Recepção do 2º Andar (Esquerda)", specialty: "Cardiologia" },

  // 1º Andar - Direita
  { name: "Anestesistas", location: "Recepção do 1º Andar (Direita)", specialty: "Anestesiologia" },
  { name: "Dr. Bruno Prado", location: "Recepção do 1º Andar (Direita)", specialty: "Cirurgia Vascular" },
  { name: "Dr. João Felipe", location: "Recepção do 1º Andar (Direita)", specialty: "Nefrologia" },
  { name: "Dra. Karen Sato", location: "Recepção do 1º Andar (Direita)", specialty: "Cirurgia Vascular" },
  { name: "Dra. Liliana Ribeiro", location: "Recepção do 1º Andar (Direita)", specialty: "Cardiologia" },
  { name: "Dr. Ricardo Zanetti", location: "Recepção do 1º Andar (Direita)", specialty: "Cirurgia Vascular" },
  { name: "Dr. Roberto Krav", location: "Recepção do 1º Andar (Direita)", specialty: "Odontologia" },
  { name: "Dr. Victor Mauro", location: "Recepção do 1º Andar (Direita)", specialty: "Cirurgia Plástica" },

  // 1º Andar - Esquerda
  { name: "Dr. Alisson Ferreira", location: "Recepção do 1º Andar (Esquerda)", specialty: "Urologia" },
  { name: "Dr. Carlos Koga", location: "Recepção do 1º Andar (Esquerda)", specialty: "Urologia" },
  { name: "Dr. Cesar Tanaka", location: "Recepção do 1º Andar (Esquerda)", specialty: "Cirurgia do Ap Digestivo" },
  { name: "Dr. Eduardo Bisinella", location: "Recepção do 1º Andar (Esquerda)", specialty: "Urologia" },
  { name: "Dr. Irineu Zanetti", location: "Recepção do 1º Andar (Esquerda)", specialty: "Clínica Geral" },
  { name: "Dr. Marcelo Tessari", location: "Recepção do 1º Andar (Esquerda)", specialty: "Neurocirurgia" },
  { name: "Dr. Ricardo Ramos", location: "Recepção do 1º Andar (Esquerda)", specialty: "Urologia" },

  // Térreo
  { name: "Dr. Daniel Tanaka", location: "Térreo — Endoscopia", specialty: "Cirurgia Geral / Endoscopia" },
  { name: "Dr. Danilo Santos", location: "Térreo — Endoscopia", specialty: "Gastroenterologia / Endoscopia" },
  { name: "Dra. Caroline Santos", location: "Térreo — Endoscopia", specialty: "Gastroenterologia / Endoscopia" },
  { name: "Clínica da Imagem", location: "Térreo — Clínica da Imagem", specialty: "Exames de Imagem" },
  { name: "Clinipon", location: "Térreo — Clinipon", specialty: "Exames Laboratoriais" },

  // Especialidades e Setores Gerais
  { name: "Ginecologia", location: "Recepção de Ginecologia (3º Andar - Esquerda)", specialty: "Ginecologia e Obstetrícia" },
  { name: "Ortopedia", location: "Recepção de Ortopedia (3º Andar - Direita)", specialty: "Ortopedia e Traumatologia" },
  { name: "Oftalmologia", location: "Recepção de Oftalmologia (2º Andar - Esquerda)", specialty: "Oftalmologia" },
  { name: "Endoscopia", location: "Térreo — Endoscopia", specialty: "Endoscopia Digestiva" },
  { name: "Recepção Geral / Central", location: "1º Andar - Lado Direito" }
];
