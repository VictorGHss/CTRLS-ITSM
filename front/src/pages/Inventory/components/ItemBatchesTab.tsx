import type { MouseEvent } from 'react';
import { Download, FileText } from 'lucide-react';
import type { Batch } from '@/types/models';

interface ItemBatchesTabProps {
  batches: Batch[];
  formatDate: (iso: string) => string;
  formatPrice: (price: number) => string;
  onDownloadInvoice: (batch: Batch, e: MouseEvent) => void;
  onOpenInvoiceModal: (batch: Batch) => void;
}

export default function ItemBatchesTab({
  batches,
  formatDate,
  formatPrice,
  onDownloadInvoice,
  onOpenInvoiceModal
}: ItemBatchesTabProps) {
  if (batches.length === 0) {
    return <p className="text-sm text-slate-400 italic">Nenhum lote registrado ainda.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
          <tr>
            <th className="pb-3 text-left font-medium">Data de Entrada</th>
            <th className="pb-3 text-right font-medium">Qtd. Comprada</th>
            <th className="pb-3 text-right font-medium">Qtd. Restante</th>
            <th className="pb-3 text-right font-medium">Preço Unitário</th>
            <th className="pb-3 text-center font-medium">Nota Fiscal</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {batches.map((batch) => (
            <tr key={batch.id} className="hover:bg-slate-50 transition-colors">
              <td className="py-3 text-slate-700">{formatDate(batch.entryDate)}</td>
              <td className="py-3 text-right text-slate-700">{batch.originalQuantity}</td>
              <td className="py-3 text-right">
                <span
                  className={
                    batch.remainingQuantity > 0 ? 'text-green-600 font-medium' : 'text-slate-400'
                  }
                >
                  {batch.remainingQuantity}
                </span>
              </td>
              <td className="py-3 text-right text-slate-700 font-medium">
                {formatPrice(batch.unitPrice)}
              </td>
              <td className="py-3">
                <div className="flex items-center justify-center">
                  {batch.invoiceFileName ? (
                    <button
                      onClick={(e) => onDownloadInvoice(batch, e)}
                      className="flex items-center gap-1.5 text-xs font-medium bg-brand-secondary text-orange-800 hover:bg-orange-200 px-2 py-1 rounded transition-colors"
                      title="Visualizar/baixar nota fiscal"
                    >
                      <Download size={13} />
                      Ver NF
                    </button>
                  ) : (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onOpenInvoiceModal(batch);
                      }}
                      className="flex items-center gap-1.5 text-xs font-medium text-slate-500 hover:text-slate-700 hover:bg-slate-100 px-2 py-1 rounded transition-colors"
                      title="Anexar nota fiscal"
                    >
                      <FileText size={13} />
                      Anexar
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
