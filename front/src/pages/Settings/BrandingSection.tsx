import { useState, useEffect } from 'react';
import { Save, RotateCcw, Palette, Eye } from 'lucide-react';
import { useBranding, DEFAULT_BRANDING, type BrandingConfig } from '@/contexts/BrandingContext';
import { toast } from 'react-toastify';

const inputClassName =
  'w-full rounded-2xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/50 focus:border-brand-primary transition-all';

export default function BrandingSection() {
  const { branding, updateBranding, resetToDefaults } = useBranding();
  const [formValues, setFormValues] = useState<BrandingConfig>(branding);
  const [saving, setSaving] = useState(false);
  const [hasChanges, setHasChanges] = useState(false);

  useEffect(() => {
    setFormValues(branding);
  }, [branding]);

  useEffect(() => {
    const isDifferent = Object.keys(branding).some(
      (k) => formValues[k as keyof BrandingConfig] !== branding[k as keyof BrandingConfig]
    );
    setHasChanges(isDifferent);
  }, [formValues, branding]);

  const handleChange = (field: keyof BrandingConfig, value: string) => {
    setFormValues((prev) => ({ ...prev, [field]: value }));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await updateBranding(formValues);
      toast.success('Identidade visual atualizada com sucesso!');
      setHasChanges(false);
    } catch {
      toast.error('Erro ao salvar as configurações de marca.');
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async () => {
    if (!window.confirm('Deseja restaurar as cores e configurações visuais padrão do CTRLS ITSM?')) {
      return;
    }
    setSaving(true);
    try {
      await resetToDefaults();
      setFormValues(DEFAULT_BRANDING);
      toast.success('Configurações padrão restauradas com sucesso!');
      setHasChanges(false);
    } catch {
      toast.error('Erro ao restaurar configurações padrão.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Card Principal de Configurações */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-brand-secondary/40 flex items-center justify-center text-brand-primary-dark">
              <Palette size={18} />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-slate-900">Identidade Visual & White-Label</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Personalize o nome, logotipo e paleta de cores do sistema para sua organização.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleReset}
            disabled={saving}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors cursor-pointer"
          >
            <RotateCcw size={12} />
            Restaurar Padrão
          </button>
        </div>

        <div className="p-6 space-y-6">
          {/* Informações da Marca */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Nome da Aplicação / Sistema
              </label>
              <input
                type="text"
                value={formValues.appName}
                onChange={(e) => handleChange('appName', e.target.value)}
                placeholder="Ex: CTRLS ITSM ou Portal TI"
                className={inputClassName}
              />
              <p className="text-[11px] text-slate-400 mt-1">Exibido na barra de título do navegador e nas abas.</p>
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Nome da Empresa / Cliente
              </label>
              <input
                type="text"
                value={formValues.companyName}
                onChange={(e) => handleChange('companyName', e.target.value)}
                placeholder="Ex: CTRLS Tecnologia ou Sua Empresa"
                className={inputClassName}
              />
              <p className="text-[11px] text-slate-400 mt-1">Exibido em etiquetas de patrimônio e rodapés oficiais.</p>
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                URL do Logotipo
              </label>
              <div className="relative">
                <input
                  type="text"
                  value={formValues.logoUrl}
                  onChange={(e) => handleChange('logoUrl', e.target.value)}
                  placeholder="https://suaempresa.com/logo.png"
                  className={inputClassName}
                />
              </div>
              <p className="text-[11px] text-slate-400 mt-1">Link para a imagem transparente (PNG/SVG/WebP) do seu logotipo.</p>
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                E-mail de Suporte e Contato
              </label>
              <input
                type="email"
                value={formValues.supportEmail}
                onChange={(e) => handleChange('supportEmail', e.target.value)}
                placeholder="suporte@suaempresa.com"
                className={inputClassName}
              />
              <p className="text-[11px] text-slate-400 mt-1">E-mail para envio de notificações e rodapés de relatórios.</p>
            </div>
          </div>

          <div className="border-t border-slate-100 pt-6">
            <h3 className="text-xs font-bold text-slate-700 uppercase tracking-wider mb-4 flex items-center gap-1.5">
              <Palette size={14} className="text-brand-primary-dark" />
              Cores de Destaque da Marca
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {/* Cor Primária */}
              <div className="p-4 rounded-2xl border border-slate-200 bg-slate-50/50 space-y-2.5">
                <span className="text-xs font-bold text-slate-800">Cor Primária</span>
                <p className="text-[11px] text-slate-500">Botões principais, abas ativas e ícones de destaque.</p>
                <div className="flex items-center gap-2">
                  <input
                    type="color"
                    value={formValues.primaryColor}
                    onChange={(e) => handleChange('primaryColor', e.target.value)}
                    className="w-10 h-10 rounded-xl cursor-pointer border-0 p-0 bg-transparent"
                  />
                  <input
                    type="text"
                    value={formValues.primaryColor}
                    onChange={(e) => handleChange('primaryColor', e.target.value)}
                    className="w-28 rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-mono font-bold text-slate-800"
                  />
                </div>
              </div>

              {/* Cor Primária Escura */}
              <div className="p-4 rounded-2xl border border-slate-200 bg-slate-50/50 space-y-2.5">
                <span className="text-xs font-bold text-slate-800">Cor de Destaque / Hover</span>
                <p className="text-[11px] text-slate-500">Estado de foco/hover dos botões e textos de títulos.</p>
                <div className="flex items-center gap-2">
                  <input
                    type="color"
                    value={formValues.primaryDarkColor}
                    onChange={(e) => handleChange('primaryDarkColor', e.target.value)}
                    className="w-10 h-10 rounded-xl cursor-pointer border-0 p-0 bg-transparent"
                  />
                  <input
                    type="text"
                    value={formValues.primaryDarkColor}
                    onChange={(e) => handleChange('primaryDarkColor', e.target.value)}
                    className="w-28 rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-mono font-bold text-slate-800"
                  />
                </div>
              </div>

              {/* Cor Secundária Suave */}
              <div className="p-4 rounded-2xl border border-slate-200 bg-slate-50/50 space-y-2.5">
                <span className="text-xs font-bold text-slate-800">Cor Secundária Suave</span>
                <p className="text-[11px] text-slate-500">Badges sutis, cartões destacados e fundos suaves.</p>
                <div className="flex items-center gap-2">
                  <input
                    type="color"
                    value={formValues.secondaryColor}
                    onChange={(e) => handleChange('secondaryColor', e.target.value)}
                    className="w-10 h-10 rounded-xl cursor-pointer border-0 p-0 bg-transparent"
                  />
                  <input
                    type="text"
                    value={formValues.secondaryColor}
                    onChange={(e) => handleChange('secondaryColor', e.target.value)}
                    className="w-28 rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-mono font-bold text-slate-800"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Barra de Ação Inferior */}
        <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex items-center justify-between">
          <span className="text-xs text-slate-500">
            {hasChanges ? '⚠️ Você tem alterações não salvas' : '✅ Identidade visual sincronizada'}
          </span>
          <button
            type="button"
            disabled={saving || !hasChanges}
            onClick={handleSave}
            className="inline-flex items-center gap-2 rounded-2xl bg-brand-primary px-5 py-2.5 text-sm font-bold text-slate-900 transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-40 shadow-sm cursor-pointer"
          >
            <Save size={14} />
            {saving ? 'Salvando...' : 'Salvar Identidade Visual'}
          </button>
        </div>
      </div>

      {/* Pré-visualização em Tempo Real (Live Preview) */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden p-6 space-y-4">
        <div className="flex items-center gap-2 text-slate-800 font-bold text-sm">
          <Eye size={16} className="text-brand-primary-dark" />
          Pré-visualização da Sua Marca em Tempo Real
        </div>

        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 space-y-5">
          {/* Header Preview */}
          <div className="bg-white rounded-xl border border-slate-200 p-3.5 flex items-center justify-between shadow-xs">
            <div className="flex items-center gap-3">
              {formValues.logoUrl ? (
                <img
                  src={formValues.logoUrl}
                  alt={formValues.appName}
                  className="h-8 max-w-[120px] object-contain"
                  onError={(e) => {
                    e.currentTarget.style.display = 'none';
                  }}
                />
              ) : (
                <div
                  className="h-8 px-3 rounded-lg flex items-center justify-center text-xs font-extrabold text-slate-900"
                  style={{ backgroundColor: formValues.primaryColor }}
                >
                  {formValues.appName || 'LOGO'}
                </div>
              )}
              <span className="text-xs font-extrabold text-slate-800 tracking-tight">
                {formValues.appName}
              </span>
            </div>

            <div className="flex items-center gap-2">
              <span
                className="text-[11px] font-bold px-2.5 py-1 rounded-lg"
                style={{
                  backgroundColor: formValues.secondaryColor,
                  color: formValues.primaryDarkColor,
                }}
              >
                Ativo
              </span>
              <button
                type="button"
                className="px-3.5 py-1.5 rounded-xl text-xs font-bold text-slate-900 shadow-xs cursor-default"
                style={{ backgroundColor: formValues.primaryColor }}
              >
                Novo Chamado
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs text-slate-600">
            <div className="bg-white p-3 rounded-xl border border-slate-200 flex items-center justify-between">
              <span>Empresa Cadastrada:</span>
              <span className="font-bold text-slate-900">{formValues.companyName || '—'}</span>
            </div>
            <div className="bg-white p-3 rounded-xl border border-slate-200 flex items-center justify-between">
              <span>Canal de Suporte:</span>
              <span className="font-bold text-slate-900">{formValues.supportEmail || '—'}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
