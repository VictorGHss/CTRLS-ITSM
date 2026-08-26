import { AlertCircle } from 'lucide-react';
import SearchableDropdown from '@/components/common/SearchableDropdown';
import type { Asset } from '@/types/models';

interface ResolveInsumoLinkSectionProps {
  hasAutoInventoryDeduction: boolean;
  deliverEquipment: boolean;
  deliveryType: 'asset' | 'item';
  selectedItemId: string;
  linkInsumosToAsset: boolean;
  setLinkInsumosToAsset: (val: boolean) => void;
  loadingAllAssets: boolean;
  allAssets: Asset[];
  targetAssetId: string;
  setTargetAssetId: (val: string) => void;
  isSubmitting: boolean;
}

export default function ResolveInsumoLinkSection({
  hasAutoInventoryDeduction,
  deliverEquipment,
  deliveryType,
  selectedItemId,
  linkInsumosToAsset,
  setLinkInsumosToAsset,
  loadingAllAssets,
  allAssets,
  targetAssetId,
  setTargetAssetId,
  isSubmitting,
}: ResolveInsumoLinkSectionProps) {
  const shouldRender = hasAutoInventoryDeduction || (deliverEquipment && deliveryType === 'item' && selectedItemId);

  if (!shouldRender) return null;

  return (
    <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-slate-50/50 p-4">
      <div className="flex items-center gap-2">
        <input
          type="checkbox"
          id="linkInsumosToAsset"
          checked={linkInsumosToAsset}
          onChange={(event) => setLinkInsumosToAsset(event.target.checked)}
          className="h-4 w-4 cursor-pointer rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
          disabled={isSubmitting}
        />
        <label htmlFor="linkInsumosToAsset" className="cursor-pointer text-sm font-medium text-slate-700">
          Vincular insumos diretamente a um Equipamento/Ativo?
        </label>
      </div>

      {linkInsumosToAsset && (
        <div className="flex flex-col gap-2">
          <label className="text-sm font-medium text-slate-700">Ativo / Equipamento do Inventário *</label>
          {loadingAllAssets ? (
            <div className="text-sm text-slate-500">Carregando equipamentos do inventário...</div>
          ) : allAssets.length === 0 ? (
            <div className="text-sm text-amber-600 flex items-center gap-1.5">
              <AlertCircle size={14} />
              Nenhum equipamento físico encontrado no módulo de inventário.
            </div>
          ) : (
            <SearchableDropdown
              options={allAssets.map((asset) => ({
                id: asset.id,
                name: `${asset.name} (${asset.patrimonyCode})`,
              }))}
              value={targetAssetId}
              onChange={(val) => setTargetAssetId(val)}
              placeholder="Selecione o equipamento do inventário..."
              disabled={isSubmitting}
            />
          )}
        </div>
      )}
    </div>
  );
}
