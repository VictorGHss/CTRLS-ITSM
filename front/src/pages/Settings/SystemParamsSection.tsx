import type { Dispatch, SetStateAction } from 'react';
import { Save } from 'lucide-react';
import type { SystemSetting } from '@/types/models';

interface SystemParamsSectionProps {
  settings: SystemSetting[];
  values: Record<string, string>;
  setValues: Dispatch<SetStateAction<Record<string, string>>>;
  saving: boolean;
  hasChanges: boolean;
  onSave: () => void;
  discordWebhookStatus: string | null;
}

const inputClassName =
  'w-full rounded-2xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/60 focus:border-[#feb56c] transition-all';

const friendlyLabels: Record<string, string> = {
  SLA_URGENT_HOURS: 'SLA — Chamados Urgentes (Horas)',
  ATTACHMENT_SIZE_LIMIT_MB: 'Limite de Tamanho de Anexo (MB)',
};

function getFriendlyLabel(settingKey: string): string {
  return friendlyLabels[settingKey] ?? settingKey.replaceAll('_', ' ');
}

function WebhookBadge({ status }: { status: string }) {
  let text = 'Desconhecido';
  let cls = 'bg-amber-100 text-amber-700';
  switch (status) {
    case 'PRESENT':
      text = 'Presente';
      cls = 'bg-emerald-100 text-emerald-700';
      break;
    case 'INVALID':
      text = 'Erro/Inválido';
      cls = 'bg-red-100 text-red-700';
      break;
    case 'MISSING':
      text = 'Ausente';
      cls = 'bg-slate-100 text-slate-500';
      break;
    default:
      text = 'Desconhecido';
      cls = 'bg-amber-100 text-amber-700';
  }
  return <span className={`${cls} text-xs font-medium px-2 py-1 rounded`}>{text}</span>;
}

export default function SystemParamsSection({
  settings,
  values,
  setValues,
  saving,
  hasChanges,
  onSave,
  discordWebhookStatus,
}: SystemParamsSectionProps) {
  return (
    <div className="bg-white rounded-2xl border border-[#feb56c]/35 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-slate-100">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-sm font-semibold text-slate-900">Parâmetros Globais</h2>
            <p className="text-xs text-slate-500 mt-0.5">Configurações gerais do sistema.</p>
          </div>
          <div>
            {discordWebhookStatus && (
              <WebhookBadge status={discordWebhookStatus} />
            )}
          </div>
        </div>
      </div>
      <div className="divide-y divide-slate-100 bg-white">
        {Array.isArray(settings) && settings.map((setting) => (
          <div key={setting.id} className="px-6 py-4 flex flex-col lg:flex-row lg:items-center gap-3">
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-slate-800">{getFriendlyLabel(setting.id)}</p>
              <p className="text-xs text-slate-400 mt-0.5 truncate">{setting.description ?? 'Sem descrição disponível.'}</p>
            </div>
            <div className="w-full lg:w-48 shrink-0">
              <input
                type="text"
                value={values[setting.id] ?? ''}
                onChange={(event) => setValues((prev) => ({ ...prev, [setting.id]: event.target.value }))}
                className={inputClassName}
                placeholder="Valor"
              />
            </div>
          </div>
        ))}
      </div>
      <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end">
        <button
          type="button"
          disabled={saving || !hasChanges}
          onClick={onSave}
          className="inline-flex items-center gap-2 rounded-2xl bg-[#feb56c] px-5 py-2.5 text-sm font-bold text-slate-900 transition-colors hover:bg-[#f6a455] disabled:cursor-not-allowed disabled:opacity-40 shadow-sm"
        >
          <Save size={14} />
          {saving ? 'Salvando...' : 'Salvar Configurações'}
        </button>
      </div>
    </div>
  );
}
