import { useMemo, useState } from 'react';
import { Globe, User as UserIcon } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

import { useAuth } from '../../contexts/AuthContext';
import FinancialTwoFactorChallenge from '@/pages/Financeiro/components/FinancialTwoFactorChallenge';
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
import BrandingSection from './BrandingSection';
import SlaSection from './SlaSection';
import ProfileTab from './ProfileTab';

import { useSystemSettings } from './hooks/useSystemSettings';
import SettingsSubSectionGrid from './components/SettingsSubSectionGrid';
import SettingsSectionHeader from './components/SettingsSectionHeader';
import {
  type TabType,
  type SubSectionType,
  getAvailableSubSections,
  SUBSECTION_TITLES,
  SUBSECTION_DESCRIPTIONS,
} from './types';

export default function Settings() {
  const { user, isTwoFactorVerified } = useAuth();
  const isSystemVisible = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN';
  const isAdmin = user?.role === 'ADMIN';

  const [activeTab, setActiveTab] = useState<TabType>(isSystemVisible ? 'system' : 'profile');
  const [activeSubSection, setActiveSubSection] = useState<SubSectionType>('menu');

  const {
    settings,
    values,
    setValues,
    loading,
    saving,
    discordWebhookStatus,
    adminConfig,
    hasChanges,
    slaKeys,
    handleSave,
    handleSaveSLA,
    handleSavePreferences,
  } = useSystemSettings(isAdmin);

  const subSections = useMemo(
    () => getAvailableSubSections(isAdmin, isSystemVisible),
    [isAdmin, isSystemVisible],
  );

  if (!isSystemVisible && activeTab === 'system') {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
          <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <Globe size={22} className="text-slate-400" />
          </div>
          <p className="text-sm font-medium text-slate-700">Acesso Restrito</p>
          <p className="text-xs text-slate-400 mt-1">
            Você não possui permissão para acessar configurações globais.
          </p>
        </div>
      </main>
    );
  }

  const tabs = [
    ...(isSystemVisible ? [{ id: 'system' as TabType, label: 'Sistema', icon: Globe }] : []),
    { id: 'profile' as TabType, label: 'Perfil', icon: UserIcon },
  ];

  const renderBackHeader = () => (
    <SettingsSectionHeader
      title={SUBSECTION_TITLES[activeSubSection]}
      description={SUBSECTION_DESCRIPTIONS[activeSubSection]}
      onBack={() => setActiveSubSection('menu')}
    />
  );

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <title>Configurações do Sistema — CTRLS ITSM</title>
      <meta
        name="description"
        content="Configurações globais, integrações e parâmetros da plataforma CTRLS ITSM"
      />
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
                <SettingsSubSectionGrid
                  subSections={subSections}
                  onSelectSubSection={setActiveSubSection}
                />
              )}

              {activeSubSection === 'branding' && (
                <motion.div
                  key="branding"
                  initial={{ opacity: 0, scale: 0.98 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderBackHeader()}
                  <BrandingSection />
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

              {activeSubSection === 'backups' &&
                (!isTwoFactorVerified ? (
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
                ))}

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
