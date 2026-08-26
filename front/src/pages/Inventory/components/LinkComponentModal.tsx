import type { FormEvent } from 'react';
import SearchableDropdown from '@/components/common/SearchableDropdown';
import type { Item } from '@/types/models';

interface LinkComponentModalProps {
  isOpen: boolean;
  onClose: () => void;
  itemName: string;
  availableItems: Item[];
  availableItemsLoading: boolean;
  selectedChildId: string;
  onSelectChildId: (id: string) => void;
  onSubmit: (e: FormEvent) => void;
  linking: boolean;
}

export default function LinkComponentModal({
  isOpen,
  onClose,
  itemName,
  availableItems,
  availableItemsLoading,
  selectedChildId,
  onSelectChildId,
  onSubmit,
  linking
}: LinkComponentModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
      <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        <div className="p-6 border-b border-slate-100">
          <h3 className="text-base font-bold text-slate-800">Acoplar Equipamento Componente</h3>
          <p className="text-xs text-slate-500 mt-1">
            Selecione um ativo disponível no estoque para vincular a <strong>{itemName}</strong>.
          </p>
        </div>

        <form onSubmit={onSubmit}>
          <div className="p-6 space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">
                Dispositivo Componente
              </label>
              {availableItemsLoading ? (
                <div className="text-xs text-slate-400 animate-pulse py-2">Buscando equipamentos...</div>
              ) : (
                <SearchableDropdown
                  options={availableItems}
                  value={selectedChildId}
                  onChange={onSelectChildId}
                  placeholder="Escolha um Monitor, Nobreak, etc..."
                />
              )}
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
              disabled={linking || !selectedChildId}
              className="px-4 py-2 text-xs font-bold text-white bg-brand-primary hover:bg-brand-primary-dark disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors shadow-sm"
            >
              {linking ? 'Salvando...' : 'Confirmar Vínculo'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
