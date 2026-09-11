import type { LucideIcon } from 'lucide-react';
import {
  Clock3,
  Database,
  FileText,
  HelpCircle,
  MessageCircle,
  Palette,
  Settings2,
  Share2,
  Tag,
} from 'lucide-react';

export type TabType = 'system' | 'profile';

export type SubSectionType =
  | 'menu'
  | 'branding'
  | 'integrations'
  | 'system-params'
  | 'sla'
  | 'reports'
  | 'feegow'
  | 'categories'
  | 'tags'
  | 'backups'
  | 'faq';

export interface SubSectionItem {
  id: SubSectionType;
  title: string;
  desc: string;
  icon: LucideIcon;
  color: string;
}

export function getAvailableSubSections(isAdmin: boolean, isSystemVisible: boolean): SubSectionItem[] {
  return [
    ...(isAdmin
      ? [
          {
            id: 'branding' as SubSectionType,
            title: 'Marca & White-Label',
            desc: 'Personalize o logotipo, cores destaque e o nome da sua empresa.',
            icon: Palette,
            color: 'bg-brand-primary/10 text-brand-primary-dark',
          },
          {
            id: 'integrations' as SubSectionType,
            title: 'Integrações',
            desc: 'Conecte Discord, Conta Azul, Feegow e Blip.',
            icon: Share2,
            color: 'bg-[#feb56c]/10 text-[#feb56c]',
          },
          {
            id: 'system-params' as SubSectionType,
            title: 'Parâmetros Globais',
            desc: 'Ajuste limites de anexos e chaves do sistema.',
            icon: Settings2,
            color: 'bg-[#feb56c]/10 text-[#feb56c]',
          },
          {
            id: 'sla' as SubSectionType,
            title: 'Prazos de SLA',
            desc: 'Defina limites de atendimento por prioridade.',
            icon: Clock3,
            color: 'bg-[#feb56c]/10 text-[#feb56c]',
          },
          {
            id: 'reports' as SubSectionType,
            title: 'Agendamento de Relatórios',
            desc: 'Programe envios automáticos de relatórios.',
            icon: FileText,
            color: 'bg-[#feb56c]/10 text-[#feb56c]',
          },
          {
            id: 'feegow' as SubSectionType,
            title: 'Mapeamento Feegow / Blip',
            desc: 'Vincule profissionais às filas de atendimento do Blip.',
            icon: MessageCircle,
            color: 'bg-[#feb56c]/10 text-[#feb56c]',
          },
          {
            id: 'categories' as SubSectionType,
            title: 'Categorias do Sistema',
            desc: 'Cadastre categorias de itens e ativos do inventário.',
            icon: Tag,
            color: 'bg-[#feb56c]/10 text-[#feb56c]',
          },
          {
            id: 'tags' as SubSectionType,
            title: 'Tags e Macros do Sistema',
            desc: 'Gerencie tags corporativas e configure macros de resoluções.',
            icon: Tag,
            color: 'bg-[#feb56c]/10 text-[#feb56c]',
          },
          {
            id: 'backups' as SubSectionType,
            title: 'Backups do Sistema',
            desc: 'Gere snapshots, baixe ZIPs ou delete backups.',
            icon: Database,
            color: 'bg-[#feb56c]/10 text-[#feb56c]',
          },
        ]
      : []),
    ...(isSystemVisible
      ? [
          {
            id: 'faq' as SubSectionType,
            title: 'FAQ da TI',
            desc: 'Configure as palavras-chave (gatilhos do Discord), perguntas e respostas automáticas.',
            icon: HelpCircle,
            color: 'bg-[#feb56c]/10 text-[#feb56c]',
          },
        ]
      : []),
  ];
}

export const SUBSECTION_TITLES: Record<SubSectionType, string> = {
  menu: 'Painel do Sistema',
  branding: 'Identidade Visual & White-Label',
  integrations: 'Integrações do Ecossistema',
  'system-params': 'Parâmetros Globais',
  sla: 'Configurações de SLA',
  reports: 'Agendamento de Relatórios',
  feegow: 'Mapeamento Feegow / Blip',
  categories: 'Categorias do Sistema',
  tags: 'Tags e Macros Contextuais',
  backups: 'Backups do Sistema',
  faq: 'FAQ da TI',
};

export const SUBSECTION_DESCRIPTIONS: Record<SubSectionType, string> = {
  menu: 'Gerencie todas as facetas administrativas e integrativas da plataforma.',
  branding: 'Ajuste as cores primárias, secundárias, logotipo e dados da sua organização.',
  integrations: 'Configure integrações com Discord, Conta Azul, Feegow e Blip.',
  'system-params': 'Ajuste limites de tamanho de anexo, e-mails e chaves globais.',
  sla: 'Defina os prazos de atendimento (horas) para chamados com base no nível de prioridade.',
  reports: 'Monitore e configure a geração automática de relatórios gerenciais.',
  feegow: 'Gerencie chaves do WhatsApp e associe médicos às filas do Blip.',
  categories: 'Configure e gerencie categorias de itens e de ativos.',
  tags: 'Configure tags visuais com cores e macros para resoluções de um clique.',
  backups: 'Gere backups de banco de dados, baixe snapshots de segurança ou delete antigos.',
  faq: 'Configure as palavras-chave (gatilhos do Discord), perguntas e respostas automáticas que o bot usa no comando /ajuda.',
};
