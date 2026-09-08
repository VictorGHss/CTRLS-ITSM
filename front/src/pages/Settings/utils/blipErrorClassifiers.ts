/**
 * Tradução e classificação amigável das categorias em Português do Brasil (PT-BR).
 * Utilizado para exibir labels elegantes na interface ao invés das constantes puras do backend.
 */
export const categoryFriendlyNames: Record<string, string> = {
  ERRO_INTERNO_OU_GATEWAY: 'Erro Interno ou Gateway',
  RATE_LIMIT_EXCEDIDO: 'Rate Limit Excedido',
  PARAMETRO_INVALIDO_TEMPLATE: 'Parâmetro de Template Inválido',
  DESTINATARIO_INVALIDO_WHATSAPP: 'Destinatário Inválido (WhatsApp)',
  CONFLITO_ATENDIMENTO_ATIVO: 'Conflito de Atendimento Ativo',
  EXPERIMENTO_META_BLOQUEADO: 'Campanha de Disparo Bloqueada',
  CONTA_BUSINESS_BLOQUEADA: 'Conta Business Bloqueada (WABA)',
  MEDIA_OU_TIPO_INCOMPATIVEL: 'Mídia ou Tipo Incompatível',
  FALHA_DESCONHECIDA: 'Outros / Falha Desconhecida',
};

/**
 * Mapeador de estilos CSS de severidade baseados na categoria do erro da Meta/Blip.
 *
 * @param category A categoria textual do erro.
 * @returns Classes TailwindCSS correspondentes ao design system do projeto.
 */
export const getCategoryBadgeStyle = (category: string) => {
  switch (category) {
    case 'CONTA_BUSINESS_BLOQUEADA':
      return 'bg-red-100 text-red-800 border border-red-200 animate-pulse font-semibold';
    case 'ERRO_INTERNO_OU_GATEWAY':
    case 'EXPERIMENTO_META_BLOQUEADO':
      return 'bg-rose-50 text-rose-700 border border-rose-200 font-medium';
    case 'RATE_LIMIT_EXCEDIDO':
    case 'PARAMETRO_INVALIDO_TEMPLATE':
      return 'bg-amber-50 text-amber-700 border border-amber-200 font-medium';
    case 'DESTINATARIO_INVALIDO_WHATSAPP':
    case 'CONFLITO_ATENDIMENTO_ATIVO':
    case 'MEDIA_OU_TIPO_INCOMPATIVEL':
      return 'bg-blue-50 text-blue-700 border border-blue-200 font-medium';
    default:
      return 'bg-slate-50 text-slate-700 border border-slate-200 font-medium';
  }
};

/**
 * Função auxiliar para traduzir o código de erro em sua correspondente categoria
 * textual que guiará a cor e o rótulo do badge.
 *
 * @param code O código numérico de erro retornado pela Meta/Blip.
 * @returns String identificadora da categoria de falha.
 */
export const classifyErrorCode = (code: number): string => {
  if (!code) return 'FALHA_DESCONHECIDA';
  if (code === 1 || code === 2 || code === 51 || code === 81 || code === 86 || code === 1601) {
    return 'ERRO_INTERNO_OU_GATEWAY';
  }
  if (code === 38 || code === 429) {
    return 'RATE_LIMIT_EXCEDIDO';
  }
  if (code === 100) {
    return 'PARAMETRO_INVALIDO_TEMPLATE';
  }
  if (code === 1505 || code === 131026) {
    return 'DESTINATARIO_INVALIDO_WHATSAPP';
  }
  if (code === 1602) {
    return 'CONFLITO_ATENDIMENTO_ATIVO';
  }
  if (code === 130472) {
    return 'EXPERIMENTO_META_BLOQUEADO';
  }
  if (code === 131031) {
    return 'CONTA_BUSINESS_BLOQUEADA';
  }
  if (code === 131051 || code === 131052 || code === 131053) {
    return 'MEDIA_OU_TIPO_INCOMPATIVEL';
  }
  return 'FALHA_DESCONHECIDA';
};

/**
 * Helper de formatação de strings de data ISO para o formato local pt-BR.
 */
export const formatDateTime = (isoString?: string) => {
  if (!isoString) return '-';
  try {
    const date = new Date(isoString);
    return date.toLocaleString('pt-BR');
  } catch {
    return isoString;
  }
};
