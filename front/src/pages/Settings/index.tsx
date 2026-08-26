import { useEffect, useMemo, useState } from 'react';
import { Globe, User as UserIcon, Clock3, ArrowLeft, Settings2, Database, Tag, FileText, Share2, HelpCircle, MessageCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import { motion, AnimatePresence } from 'framer-motion';

import { useAuth } from '../../contexts/AuthContext';
import { getSystemSettings, getAdminConfig, updateSystemSettings } from '../../services/inventoryService';
import FinancialTwoFactorChallenge from '@/pages/Financeiro/components/FinancialTwoFactorChallenge';
import type { AdminConfig, SystemSetting, UpdateSystemSettingsPayload } from '../../types/models';

import PageHero from '@/components/ui/PageHero';
import ReportSchedulesSection from './ReportSchedulesSection';
import AppointmentControlPanel from './AppointmentControlPanel';
import ProfessionalMappingPanel from './ProfessionalMappingPanel';
import BlipDeliveryFailuresPanel from './BlipDeliveryFailuresPanel';
import CategoriesSection from './CategoriesSection';
import BackupsSection from './BackupsSection';
import TagsSection from './TagsSection';
import FaqManagement from '../FaqManagement';
import IntegrationsSection from './IntegrationsSection';
import SystemParamsSection from './SystemParamsSection';
import SlaSection from './SlaSection';
import ProfileTab from './ProfileTab';

type TabType = 'system' | 'profile';
type SubSectionType = 'menu' | 'integrations' | 'system-params' | 'sla' | 'reports' | 'feegow' | 'categories' | 'tags' | 'backups' | 'faq';

export default function Settings() {
  const { user, isTwoFactorVerified } = useAuth();
  const isSystemVisible = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';
  const [activeTab, setActiveTab] = useState<TabType>(isSystemVisible ? 'system' : 'profile');
  const [activeSubSection, setActiveSubSection] = useState<SubSectionType>('menu');
  const [settings, setSettings] = useState<SystemSetting[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [discordWebhookStatus, setDiscordWebhookStatus] = useState<string | null>(null);
  const [adminConfig, setAdminConfig] = useState<AdminConfig | null>(null);

  const isAdmin = user?.role === 'ADMIN';

  useEffect(() => {
    async function loadSettings() {
      if (!isAdmin) {
        setLoading(false);
        return;
      }
      setLoading(true);
      try {
        const data = await getSystemSettings();
        const safeSettings = Array.isArray(data) ? data : [];
        setSettings(safeSettings);
        setValues(
          safeSettings.reduce<Record<string, string>>((acc, setting) => {
            acc[setting.id] = setting.value;
            return acc;
          }, {}),
        );
        try {
          const cfg = await getAdminConfig();
          setAdminConfig(cfg);
          setDiscordWebhookStatus(cfg.discordWebhookStatus ?? (cfg.discordWebhookPresent ? 'PRESENT' : 'MISSING'));
        } catch {
          setDiscordWebhookStatus(null);
          setAdminConfig(null);
        }
      } catch {
        toast.error('Erro ao carregar configurações globais.');
        setSettings([]);
        setValues({});
      } finally {
        setLoading(false);
      }
    }
    loadSettings();
  }, [isAdmin]);

  const hasChanges = useMemo(() => {
    return settings.some((setting) => values[setting.id] !== setting.value);
  }, [settings, values]);

  async function handleSave() {
    if (!isAdmin) return;
    const payload: UpdateSystemSettingsPayload = settings.reduce((acc, setting) => {
      acc[setting.id] = values[setting.id] ?? '';
      return acc;
    }, {} as UpdateSystemSettingsPayload);
    setSaving(true);
    try {
      const updated = await updateSystemSettings(payload);
      setSettings(updated);
      setValues(
        updated.reduce<Record<string, string>>((acc, setting) => {
          acc[setting.id] = setting.value;
          return acc;
        }, {}),
      );
      toast.success('Configurações salvas com sucesso.');
    } catch {
      toast.error('Erro ao salvar configurações.');
    } finally {
      setSaving(false);
    }
  }

  function handleSavePreferences() {
    toast.success('Preferências salvas com sucesso.');
  }

  function isProblemDetail(obj: unknown): obj is { detail?: string } {
    return typeof obj === 'object' && obj !== null && 'detail' in (obj as Record<string, unknown>) && typeof (obj as Record<string, unknown>).detail === 'string';
  }

  function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
    if (typeof error === 'object' && error !== null) {
      const maybeResponse = error as { response?: { data?: unknown } };
      const data = maybeResponse.response?.data;
      if (isProblemDetail(data)) return data.detail ?? fallbackMessage;
      if (typeof data === 'string' && data.includes('<html')) return 'Resposta inesperada do servidor (HTML). Verifique proxy/NGINX.';
    }
    return fallbackMessage;
  }

  const slaKeys = useMemo(() => settings.filter((s) => s.id && s.id.startsWith('SLA_')), [settings]);

  async function handleSaveSLA() {
    if (!isAdmin) return;
    const payload: UpdateSystemSettingsPayload = {};
    slaKeys.forEach((s) => { payload[s.id] = values[s.id] ?? ''; });
    setSaving(true);
    try {
      const updated = await updateSystemSettings(payload);
      setSettings(updated);
      setValues(updated.reduce<Record<string, string>>((acc, setting) => { acc[setting.id] = setting.value; return acc; }, {}));
      toast.success('SLAs salvos com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao salvar SLAs.'));
    } finally {
      setSaving(false);
    }
  }

  if (!isSystemVisible && activeTab === 'system') {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
          <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <Globe size={22} className="text-slate-400" />
          </div>
          <p className="text-sm font-medium text-slate-700">Acesso Restrito</p>
          <p className="text-xs text-slate-400 mt-1">Você não possui permissão para acessar configurações globais.</p>
        </div>
      </main>
    );
  }

  const tabs = [
    ...(isSystemVisible ? [{ id: 'system' as TabType, label: 'Sistema', icon: Globe }] : []),
    { id: 'profile' as TabType, label: 'Perfil', icon: UserIcon },
  ];

  const subSections = [
    ...(isAdmin ? [
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
    ] : []),
    ...(isSystemVisible ? [
      {
        id: 'faq' as SubSectionType,
        title: 'FAQ da TI',
        desc: 'Configure as palavras-chave (gatilhos do Discord), perguntas e respostas automáticas.',
        icon: HelpCircle,
        color: 'bg-[#feb56c]/10 text-[#feb56c]',
      },
    ] : []),
  ];

  const subSectionTitles: Record<SubSectionType, string> = {
    menu: 'Painel do Sistema',
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

  const subSectionDescriptions: Record<SubSectionType, string> = {
    menu: 'Gerencie todas as facetas administrativas e integrativas da plataforma.',
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

  const renderBackHeader = () => (
    <div className="mb-6 flex items-center gap-3">
      <button
        type="button"
        onClick={() => setActiveSubSection('menu')}
        className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 shadow-sm transition-all hover:bg-slate-50 hover:text-slate-800 hover:scale-105 active:scale-95"
      >
        <ArrowLeft size={18} />
      </button>
      <div>
        <h2 className="text-base font-bold text-slate-900">{subSectionTitles[activeSubSection]}</h2>
        <p className="text-xs text-slate-500">{subSectionDescriptions[activeSubSection]}</p>
      </div>
    </div>
  );

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <PageHero
        eyebrow="Sistema"
        title="Configurações"
        description="Ajuste parâmetros globais e personalize preferências para adaptar o ambiente ao fluxo da equipe."
      />

      {/* Tab Navigation */}
      <div className="flex items-center gap-1 mb-6 bg-slate-100 rounded-2xl p-1 w-fit border border-slate-200/40">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => {
              setActiveTab(id);
              if (id === 'profile') setActiveSubSection('menu');
            }}
            className={`flex items-center gap-2 px-5 py-2.5 text-sm font-bold rounded-xl transition-all ${
              activeTab === id
                ? 'bg-white text-[#feb56c] shadow-sm border border-slate-250/20'
                : 'text-slate-500 hover:text-[#feb56c]/90'
            }`}
          >
            <Icon size={15} />
            {label}
          </button>
        ))}
      </div>

      {/* ── TAB: SISTEMA ── */}
      {activeTab === 'system' && isSystemVisible && (
        <div className="space-y-6">
          {loading ? (
            <div className="space-y-3">
              <div className="h-20 rounded-2xl bg-slate-100 animate-pulse" />
              <div className="h-20 rounded-2xl bg-slate-100 animate-pulse" />
              <div className="h-20 rounded-2xl bg-slate-100 animate-pulse" />
            </div>
          ) : (
            <AnimatePresence mode="wait">
              {activeSubSection === 'menu' && (
                <motion.div
                  key="menu"
                  initial={{ opacity: 0, y: 15 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -15 }}
                  transition={{ duration: 0.2 }}
                  className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
                >
                  {subSections.map(({ id, title, desc, icon: Icon }) => (
                    <motion.button
                      key={id}
                      onClick={() => setActiveSubSection(id)}
                      whileHover={{ scale: 1.02, translateY: -4 }}
                      whileTap={{ scale: 0.98 }}
                      className="flex flex-col text-left p-6 rounded-2xl border border-[#feb56c]/30 bg-white shadow-sm hover:shadow-md hover:border-[#feb56c] transition-all cursor-pointer group relative overflow-hidden"
                    >
                      <div className="flex items-center gap-4 mb-3">
                        <div className="w-12 h-12 rounded-xl bg-[#feb56c]/10 text-[#feb56c] flex items-center justify-center shrink-0 shadow-sm transition-transform group-hover:scale-110 group-hover:bg-[#feb56c]/20">
                          <Icon size={22} />
                        </div>
                        <h3 className="text-sm font-bold text-slate-800 group-hover:text-[#feb56c] transition-colors">{title}</h3>
                      </div>
                      <p className="text-xs text-slate-500 leading-relaxed pr-4">{desc}</p>
                      
                      <div className="absolute bottom-4 right-4 text-[#feb56c] opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all">
                        <ArrowLeft size={16} className="rotate-180" />
                      </div>
                    </motion.button>
                  ))}
                </motion.div>
              )}

              {activeSubSection === 'integrations' && !isTwoFactorVerified && (
                <FinancialTwoFactorChallenge onClose={() => setActiveSubSection('menu')} />
              )}

              {activeSubSection === 'integrations' && isTwoFactorVerified && (
                <motion.div
                  key="integrations"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <IntegrationsSection
                    adminConfig={adminConfig}
                    discordWebhookStatus={discordWebhookStatus}
                  />
                </motion.div>
              )}

              {activeSubSection === 'system-params' && (
                <motion.div
                  key="system-params"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <SystemParamsSection
                    settings={settings}
                    values={values}
                    setValues={setValues}
                    saving={saving}
                    hasChanges={hasChanges}
                    onSave={handleSave}
                    discordWebhookStatus={discordWebhookStatus}
                  />
                </motion.div>
              )}

              {activeSubSection === 'sla' && (
                <motion.div
                  key="sla"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <SlaSection
                    slaKeys={slaKeys}
                    values={values}
                    setValues={setValues}
                    saving={saving}
                    onSaveSla={handleSaveSLA}
                  />
                </motion.div>
              )}

              {activeSubSection === 'reports' && (
                <motion.div
                  key="reports"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <ReportSchedulesSection />
                </motion.div>
              )}

              {activeSubSection === 'feegow' && (
                <motion.div
                  key="feegow"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                  className="space-y-6"
                >
                  {renderBackHeader()}
                  <AppointmentControlPanel />
                  <ProfessionalMappingPanel />
                  <BlipDeliveryFailuresPanel />
                </motion.div>
              )}

              {activeSubSection === 'categories' && (
                <motion.div
                  key="categories"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <CategoriesSection />
                </motion.div>
              )}

              {activeSubSection === 'tags' && (
                <motion.div
                  key="tags"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <TagsSection />
                </motion.div>
              )}

              {activeSubSection === 'backups' && (
                !isTwoFactorVerified ? (
                  <FinancialTwoFactorChallenge
                    onClose={() => setActiveSubSection('menu')}
                  />
                ) : (
                  <motion.div
                    key="backups"
                    initial={{ opacity: 0, scale: 0.98 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.98 }}
                    transition={{ duration: 0.2 }}
                  >
                    {renderBackHeader()}
                    <BackupsSection />
                  </motion.div>
                )
              )}

              {activeSubSection === 'faq' && (
                <motion.div
                  key="faq"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <FaqManagement />
                </motion.div>
              )}
            </AnimatePresence>
          )}
        </div>
      )}

      {/* ── TAB: PERFIL ── */}
      {activeTab === 'profile' && (
        <ProfileTab
          user={user}
          onSavePreferences={handleSavePreferences}
        />
      )}
    </main>
  );
}
