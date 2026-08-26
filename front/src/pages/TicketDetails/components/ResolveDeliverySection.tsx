import { Laptop, Box } from 'lucide-react';
import SearchableDropdown from '@/components/common/SearchableDropdown';
import type { Asset, Item } from '@/types/models';

interface AssetCategory {
  id: string;
  name: string;
}

interface ResolveDeliverySectionProps {
  deliverEquipment: boolean;
  setDeliverEquipment: (val: boolean) => void;
  deliveryType: 'asset' | 'item';
  setDeliveryType: (val: 'asset' | 'item') => void;
  assetMode: 'existing' | 'new';
  setAssetMode: (val: 'existing' | 'new') => void;
  assets: Asset[];
  items: Item[];
  assetCategories: AssetCategory[];
  selectedAssetId: string;
  setSelectedAssetId: (val: string) => void;
  selectedItemId: string;
  setSelectedItemId: (val: string) => void;
  quantity: number;
  setQuantity: (val: number) => void;
  newAssetName: string;
  setNewAssetName: (val: string) => void;
  newAssetCategoryId: string;
  setNewAssetCategoryId: (val: string) => void;
  newAssetPatrimonyCode: string;
  setNewAssetPatrimonyCode: (val: string) => void;
  newAssetSpecifications: string;
  setNewAssetSpecifications: (val: string) => void;
  loadingAssets: boolean;
  loadingItems: boolean;
  loadingAssetCategories: boolean;
  isSubmitting: boolean;
  availableRecipients: { id: string; name: string }[];
  recipientUserId: string;
  setRecipientUserId: (val: string) => void;
}

export default function ResolveDeliverySection({
  deliverEquipment,
  setDeliverEquipment,
  deliveryType,
  setDeliveryType,
  assetMode,
  setAssetMode,
  assets,
  items,
  assetCategories,
  selectedAssetId,
  setSelectedAssetId,
  selectedItemId,
  setSelectedItemId,
  quantity,
  setQuantity,
  newAssetName,
  setNewAssetName,
  newAssetCategoryId,
  setNewAssetCategoryId,
  newAssetPatrimonyCode,
  setNewAssetPatrimonyCode,
  newAssetSpecifications,
  setNewAssetSpecifications,
  loadingAssets,
  loadingItems,
  loadingAssetCategories,
  isSubmitting,
  availableRecipients,
  recipientUserId,
  setRecipientUserId,
}: ResolveDeliverySectionProps) {
  return (
    <>
      <div className="flex items-center gap-2">
        <input
          type="checkbox"
          id="deliverEquipment"
          checked={deliverEquipment}
          onChange={(event) => setDeliverEquipment(event.target.checked)}
          className="h-4 w-4 cursor-pointer rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
          disabled={isSubmitting}
        />
        <label htmlFor="deliverEquipment" className="cursor-pointer text-sm font-medium text-slate-700">
          Entregar Equipamento ou Material nesta resolução?
        </label>
      </div>

      {deliverEquipment && (
        <div className="flex flex-col gap-4 rounded-2xl border border-brand-primary/40 bg-brand-secondary/50 p-4">
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => setDeliveryType('asset')}
              className={`flex items-center gap-2 rounded-2xl px-4 py-2 text-sm font-semibold transition-colors ${
                deliveryType === 'asset'
                  ? 'bg-brand-primary text-white'
                  : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
              }`}
              disabled={isSubmitting}
            >
              <Laptop size={16} />
              Ativo de Patrimônio
            </button>
            <button
              type="button"
              onClick={() => setDeliveryType('item')}
              className={`flex items-center gap-2 rounded-2xl px-4 py-2 text-sm font-semibold transition-colors ${
                deliveryType === 'item'
                  ? 'bg-brand-primary text-white'
                  : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
              }`}
              disabled={isSubmitting}
            >
              <Box size={16} />
              Item de Consumo
            </button>
          </div>

          {deliveryType === 'asset' && (
            <div className="flex flex-col gap-4">
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => setAssetMode('existing')}
                  className={`rounded-2xl px-3 py-1.5 text-sm font-semibold transition-colors ${
                    assetMode === 'existing'
                      ? 'bg-brand-primary text-white'
                      : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                  }`}
                  disabled={isSubmitting}
                >
                  Selecionar Existente
                </button>
                <button
                  type="button"
                  onClick={() => setAssetMode('new')}
                  className={`rounded-2xl px-3 py-1.5 text-sm font-semibold transition-colors ${
                    assetMode === 'new'
                      ? 'bg-brand-primary text-white'
                      : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                  }`}
                  disabled={isSubmitting}
                >
                  Cadastrar Novo
                </button>
              </div>

              {assetMode === 'existing' && (
                <div className="flex flex-col gap-2">
                  <label className="text-sm font-medium text-slate-700">Selecione o Equipamento *</label>
                  {loadingAssets ? (
                    <div className="text-sm text-slate-500">Carregando equipamentos...</div>
                  ) : assets.length === 0 ? (
                    <div className="text-sm text-red-600">Nenhum equipamento disponível no estoque da TI.</div>
                  ) : (
                    <SearchableDropdown
                      options={assets.map((asset) => ({
                        id: asset.id,
                        name: `${asset.name} (${asset.patrimonyCode})`,
                      }))}
                      value={selectedAssetId}
                      onChange={(val) => setSelectedAssetId(val)}
                      placeholder="Selecione um equipamento..."
                      disabled={isSubmitting}
                    />
                  )}
                </div>
              )}

              {assetMode === 'new' && (
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                  <div className="flex flex-col gap-2 md:col-span-2">
                    <label className="text-sm font-medium text-slate-700">Nome do Ativo *</label>
                    <input
                      type="text"
                      value={newAssetName}
                      onChange={(event) => setNewAssetName(event.target.value)}
                      className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                      placeholder="Ex.: Notebook Dell Latitude"
                      disabled={isSubmitting}
                    />
                  </div>

                  <div className="flex flex-col gap-2">
                    <label className="text-sm font-medium text-slate-700">Categoria *</label>
                    {loadingAssetCategories ? (
                      <div className="text-sm text-slate-500">Carregando categorias...</div>
                    ) : (
                      <SearchableDropdown
                        options={assetCategories.map((category) => ({
                          id: category.id,
                          name: category.name,
                        }))}
                        value={newAssetCategoryId}
                        onChange={(val) => setNewAssetCategoryId(val)}
                        placeholder="Selecione a categoria..."
                        disabled={isSubmitting}
                      />
                    )}
                  </div>

                  <div className="flex flex-col gap-2">
                    <label className="text-sm font-medium text-slate-700">Código do Patrimônio *</label>
                    <input
                      type="text"
                      value={newAssetPatrimonyCode}
                      onChange={(event) => setNewAssetPatrimonyCode(event.target.value)}
                      className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                      placeholder="Ex.: PAT-2026-001"
                      disabled={isSubmitting}
                    />
                  </div>

                  <div className="flex flex-col gap-2 md:col-span-2">
                    <label className="text-sm font-medium text-slate-700">Especificações</label>
                    <textarea
                      value={newAssetSpecifications}
                      onChange={(event) => setNewAssetSpecifications(event.target.value)}
                      className="w-full resize-none rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                      rows={3}
                      placeholder="CPU, memória, armazenamento, etc."
                      disabled={isSubmitting}
                    />
                  </div>
                </div>
              )}
            </div>
          )}

          {deliveryType === 'item' && (
            <>
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-slate-700">Selecione o Material *</label>
                {loadingItems ? (
                  <div className="text-sm text-slate-500">Carregando materiais...</div>
                ) : items.length === 0 ? (
                  <div className="text-sm text-red-600">Nenhum material disponível em estoque.</div>
                ) : (
                  <SearchableDropdown
                    options={items.map((item) => ({
                      id: item.id,
                      name: `${item.name} (Estoque: ${item.currentStock})`,
                    }))}
                    value={selectedItemId}
                    onChange={(val) => setSelectedItemId(val)}
                    placeholder="Selecione um material..."
                    disabled={isSubmitting}
                  />
                )}
              </div>

              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-slate-700">Quantidade *</label>
                <input
                  type="number"
                  value={quantity}
                  onChange={(event) => setQuantity(Math.max(1, Number.parseInt(event.target.value, 10) || 1))}
                  min="1"
                  className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                  disabled={isSubmitting}
                />
              </div>

              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-slate-700">Entregar a quem? *</label>
                <SearchableDropdown
                  options={availableRecipients}
                  value={recipientUserId}
                  onChange={(val) => setRecipientUserId(val)}
                  placeholder="Selecione quem recebeu..."
                />
              </div>
            </>
          )}
        </div>
      )}
    </>
  );
}
