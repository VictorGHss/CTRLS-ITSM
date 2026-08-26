import { AlertCircle } from 'lucide-react';
import SearchableDropdown from '@/components/common/SearchableDropdown';
import type { Asset } from '@/types/models';

interface ResolveMaintenanceSectionProps {
  registerMaintenance: boolean;
  setRegisterMaintenance: (val: boolean) => void;
  allAssets: Asset[];
  maintAssetId: string;
  setMaintAssetId: (val: string) => void;
  maintType: 'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE' | 'TRANSFER';
  setMaintType: (val: 'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE' | 'TRANSFER') => void;
  maintCost: string;
  setMaintCost: (val: string) => void;
  maintDescription: string;
  setMaintDescription: (val: string) => void;
  isSubmitting: boolean;
}

export default function ResolveMaintenanceSection({
  registerMaintenance,
  setRegisterMaintenance,
  allAssets,
  maintAssetId,
  setMaintAssetId,
  maintType,
  setMaintType,
  maintCost,
  setMaintCost,
  maintDescription,
  setMaintDescription,
  isSubmitting,
}: ResolveMaintenanceSectionProps) {
  return (
    <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-slate-50/50 p-4">
      <div className="flex items-center gap-2">
        <input
          type="checkbox"
          id="registerMaintenance"
          checked={registerMaintenance}
          onChange={(event) => setRegisterMaintenance(event.target.checked)}
          className="h-4 w-4 cursor-pointer rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
          disabled={isSubmitting}
        />
        <label htmlFor="registerMaintenance" className="cursor-pointer text-sm font-medium text-slate-700">
          Registrar Manutenção de Ativo associada a este chamado?
        </label>
      </div>

      {registerMaintenance && (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <label className="text-sm font-medium text-slate-700">Equipamento Afetado *</label>
            {allAssets.length === 0 ? (
              <div className="text-sm text-amber-600 flex items-center gap-1.5">
                <AlertCircle size={14} />
                Nenhum equipamento cadastrado no CMDB.
              </div>
            ) : (
              <SearchableDropdown
                options={allAssets.map((asset) => ({
                  id: asset.id,
                  name: `${asset.name} (${asset.patrimonyCode})`,
                }))}
                value={maintAssetId}
                onChange={(val) => setMaintAssetId(val)}
                placeholder="Selecione o equipamento..."
                disabled={isSubmitting}
              />
            )}
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-sm font-medium text-slate-700">Tipo de Manutenção</label>
            <select
              value={maintType}
              onChange={(event) => setMaintType(event.target.value as 'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE' | 'TRANSFER')}
              className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
              disabled={isSubmitting}
            >
              <option value="PREVENTIVE">Preventiva</option>
              <option value="CORRECTIVE">Corretiva</option>
              <option value="UPGRADE">Upgrade</option>
              <option value="TRANSFER">Transferência</option>
            </select>
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-sm font-medium text-slate-700">Custo da Manutenção (R$)</label>
            <input
              type="number"
              step="0.01"
              min="0"
              placeholder="0.00"
              value={maintCost}
              onChange={(event) => setMaintCost(event.target.value)}
              className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
              disabled={isSubmitting}
            />
          </div>

          <div className="flex flex-col gap-2">
            <label className="text-sm font-medium text-slate-700">Descrição/Laudo Técnico</label>
            <textarea
              value={maintDescription}
              onChange={(event) => setMaintDescription(event.target.value)}
              placeholder="Descreva o serviço realizado no equipamento..."
              className="w-full resize-none rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
              rows={3}
              disabled={isSubmitting}
            />
          </div>
        </div>
      )}
    </div>
  );
}
