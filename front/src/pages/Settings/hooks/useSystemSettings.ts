import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-toastify';
import { getSystemSettings, getAdminConfig, updateSystemSettings } from '../../../services/inventoryService';
import type { AdminConfig, SystemSetting, UpdateSystemSettingsPayload } from '../../../types/models';

function isProblemDetail(obj: unknown): obj is { detail?: string } {
  return (
    typeof obj === 'object' &&
    obj !== null &&
    'detail' in (obj as Record<string, unknown>) &&
    typeof (obj as Record<string, unknown>).detail === 'string'
  );
}

function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (typeof error === 'object' && error !== null) {
    const maybeResponse = error as { response?: { data?: unknown } };
    const data = maybeResponse.response?.data;
    if (isProblemDetail(data)) return data.detail ?? fallbackMessage;
    if (typeof data === 'string' && data.includes('<html')) {
      return 'Resposta inesperada do servidor (HTML). Verifique proxy/NGINX.';
    }
  }
  return fallbackMessage;
}

export function useSystemSettings(isAdmin: boolean) {
  const [settings, setSettings] = useState<SystemSetting[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [discordWebhookStatus, setDiscordWebhookStatus] = useState<string | null>(null);
  const [adminConfig, setAdminConfig] = useState<AdminConfig | null>(null);

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
    void loadSettings();
  }, [isAdmin]);

  const hasChanges = useMemo(() => {
    return settings.some((setting) => values[setting.id] !== setting.value);
  }, [settings, values]);

  const slaKeys = useMemo(
    () => settings.filter((s) => s.id && s.id.startsWith('SLA_')),
    [settings],
  );

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

  async function handleSaveSLA() {
    if (!isAdmin) return;
    const payload: UpdateSystemSettingsPayload = {};
    slaKeys.forEach((s) => {
      payload[s.id] = values[s.id] ?? '';
    });
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
      toast.success('SLAs salvos com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Erro ao salvar SLAs.'));
    } finally {
      setSaving(false);
    }
  }

  function handleSavePreferences() {
    toast.success('Preferências salvas com sucesso.');
  }

  return {
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
  };
}
