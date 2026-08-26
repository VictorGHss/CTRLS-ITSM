import type { FormEvent } from 'react';
import SearchableDropdown from '@/components/common/SearchableDropdown';
import type { Item } from '@/types/models';

interface AllocateConsumableModalProps {
  isOpen: boolean;
  onClose: () => void;
  itemName: string;
  availableConsumables: Item[];
  availableConsumablesLoading: boolean;
  selectedConsumableId: string;
  onSelectConsumableId: (id: string) => void;
  allocationQuantity: number;
  onQuantityChange: (qty: number) => void;
  allocationTicketId: string;
  onTicketIdChange: (ticketId: string) => void;
  onSubmit: (e: FormEvent) => void;
  allocating: boolean;
}

export default function AllocateConsumableModal({
  isOpen,
  onClose,
  itemName,
  availableConsumables,
  availableConsumablesLoading,
  selectedConsumableId,
  onSelectConsumableId,
  allocationQuantity,
  onQuantityChange,
  allocationTicketId,
  onTicketIdChange,
  onSubmit,
  allocating
}: AllocateConsumableModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        <div className="p-6 border-b border-slate-100">
          <h3 className="text-base font-bold text-slate-800">Alocar Consumível ou Suprimento</h3>
          <p className="text-xs text-slate-500 mt-1">
            Instale um consumível em <strong>{itemName}</strong> efetuando a baixa física em lote (FIFO).
          </p>
        </div>

        <form onSubmit={onSubmit}>
          <div className="p-6 space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">
                Insumo / Suprimento
              </label>
              {availableConsumablesLoading ? (
                <div className="text-xs text-slate-400 animate-pulse py-2">Buscando insumos...</div>
              ) : (
                <SearchableDropdown
                  options={availableConsumables.map((c) => ({
                    id: c.id,
                    name: `${c.name} (Saldo: ${c.currentStock})`
                  }))}
                  value={selectedConsumableId}
                  onChange={onSelectConsumableId}
                  placeholder="Escolha um Toner, Teclado, Mouse..."
                />
              )}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">
                  Quantidade
                </label>
                <input
                  type="number"
                  min={1}
                  value={allocationQuantity}
                  onChange={(e) => onQuantityChange(Math.max(1, parseInt(e.target.value) || 1))}
                  className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">
                  ID do Chamado (Opcional)
                </label>
                <input
                  type="text"
                  placeholder="UUID do chamado..."
                  value={allocationTicketId}
                  onChange={(e) => onTicketIdChange(e.target.value)}
                  className="w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition text-xs font-medium"
                />
              </div>
            </div>
          </div>

          <div className="p-6 bg-slate-50 border-t border-slate-100 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 rounded-xl transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={allocating || !selectedConsumableId || allocationQuantity <= 0}
              className="px-4 py-2 text-xs font-bold text-white bg-green-600 hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors shadow-sm"
            >
              {allocating ? 'Alocando...' : 'Confirmar Alocação'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
