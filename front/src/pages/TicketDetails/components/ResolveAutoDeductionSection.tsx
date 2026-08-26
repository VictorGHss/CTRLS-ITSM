import { AlertCircle, Split, Trash2 } from 'lucide-react';
import SearchableDropdown from '@/components/common/SearchableDropdown';

interface DeliverItem {
  id: string;
  itemId: string;
  itemName: string;
  quantity: number;
  recipientUserId: string;
}

interface ResolveAutoDeductionSectionProps {
  itemsToDeliver: DeliverItem[];
  availableRecipients: { id: string; name: string }[];
  onRecipientChange: (id: string, recipientUserId: string) => void;
  onSplitItem: (id: string) => void;
  onRemoveSplitItem: (id: string) => void;
}

export default function ResolveAutoDeductionSection({
  itemsToDeliver,
  availableRecipients,
  onRecipientChange,
  onSplitItem,
  onRemoveSplitItem
}: ResolveAutoDeductionSectionProps) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start gap-3 rounded-2xl border border-brand-primary/35 bg-brand-secondary/55 p-4">
        <AlertCircle size={18} className="mt-0.5 shrink-0 text-brand-primary-dark" />
        <div>
          <p className="text-sm font-semibold text-slate-800">Dedução Automática de Insumos</p>
          <p className="mt-1 text-sm text-slate-700">
            Os seguintes itens solicitados serão deduzidos automaticamente do stock ao fechar este chamado:
          </p>
        </div>
      </div>

      <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 p-4 bg-slate-50/50">
        {itemsToDeliver.map((item) => {
          const canSplit = item.quantity > 1;
          const isSplitRow = itemsToDeliver.filter((i) => i.itemId === item.itemId).length > 1;

          return (
            <div
              key={item.id}
              className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm"
            >
              <div className="flex flex-col gap-1 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-bold text-slate-800">{item.itemName}</span>
                  {isSplitRow && (
                    <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-100 text-amber-800 border border-amber-200">
                      Entrega Parcial
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-slate-500 font-medium">Quantidade:</span>
                  <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-bold bg-slate-100 text-slate-700">
                    {item.quantity} un
                  </span>
                  {canSplit && (
                    <button
                      type="button"
                      onClick={() => onSplitItem(item.id)}
                      className="inline-flex items-center gap-1 text-[11px] font-semibold text-brand-primary hover:text-brand-primary-dark hover:underline transition-colors ml-2 cursor-pointer"
                      title="Dividir este item para entregar a mais de uma pessoa"
                    >
                      <Split size={13} />
                      Dividir Entrega
                    </button>
                  )}
                </div>
              </div>

              <div className="flex items-end gap-2 w-full sm:w-72">
                <div className="flex flex-col gap-1 flex-1">
                  <label className="text-xs font-semibold text-slate-600">Entregar a quem? *</label>
                  <SearchableDropdown
                    options={availableRecipients}
                    value={item.recipientUserId}
                    onChange={(val) => onRecipientChange(item.id, val)}
                    placeholder="Pesquise o médico ou secretária..."
                  />
                </div>
                {isSplitRow && (
                  <button
                    type="button"
                    onClick={() => onRemoveSplitItem(item.id)}
                    className="p-2 rounded-xl text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors cursor-pointer"
                    title="Remover esta entrega parcial e unificar quantidade"
                  >
                    <Trash2 size={16} />
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
