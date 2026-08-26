import type { Dispatch, SetStateAction } from 'react';
import { Save, Clock3 } from 'lucide-react';
import type { SystemSetting } from '@/types/models';

interface SlaSectionProps {
  slaKeys: SystemSetting[];
  values: Record<string, string>;
  setValues: Dispatch<SetStateAction<Record<string, string>>>;
  saving: boolean;
  onSaveSla: () => void;
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

export default function SlaSection({
  slaKeys,
  values,
  setValues,
  saving,
  onSaveSla,
}: SlaSectionProps) {
  return (
    <div className="bg-white rounded-2xl border border-[#feb56c]/35 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-slate-100 flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-amber-50 flex items-center justify-center shrink-0">
          <Clock3 size={16} className="text-[#feb56c]" />
        </div>
        <div>
          <h2 className="text-sm font-semibold text-slate-900">Configurações de SLA</h2>
          <p className="text-xs text-slate-500 mt-0.5">Defina os prazos de atendimento para cada nível de prioridade.</p>
        </div>
      </div>
      <div className="divide-y divide-slate-100 bg-white">
        {Array.isArray(slaKeys) && slaKeys.length === 0 ? (
          <div className="px-6 py-6 text-sm text-slate-500 text-center">Nenhuma configuração de SLA encontrada.</div>
        ) : (
          Array.isArray(slaKeys) && slaKeys.map((s) => (
            <div key={s.id} className="px-6 py-4 flex items-center gap-4">
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-slate-800">{getFriendlyLabel(s.id)}</p>
                <p className="text-xs text-slate-400 mt-0.5">{s.description ?? 'Sem descrição.'}</p>
              </div>
              <div className="w-32 shrink-0">
                <input
                  type="number"
                  min={0}
                  value={values[s.id] ?? ''}
                  onChange={(e) => setValues((prev) => ({ ...prev, [s.id]: e.target.value }))}
                  className={inputClassName}
                />
              </div>
            </div>
          ))
        )}
      </div>
      <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end">
        <button
          onClick={onSaveSla}
          disabled={saving}
          className="inline-flex items-center gap-2 rounded-2xl bg-[#feb56c] px-5 py-2.5 text-sm font-bold text-slate-900 transition-colors hover:bg-[#f6a455] disabled:opacity-40 shadow-sm"
        >
          <Save size={14} />
          Salvar SLAs
        </button>
      </div>
    </div>
  );
}
