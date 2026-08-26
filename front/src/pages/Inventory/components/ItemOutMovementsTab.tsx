import { Link } from 'react-router-dom';
import type { StockMovement } from '@/types/models';

interface ItemOutMovementsTabProps {
  outMovements: StockMovement[];
  formatDate: (iso: string) => string;
}

export default function ItemOutMovementsTab({ outMovements, formatDate }: ItemOutMovementsTabProps) {
  function renderReference(ref: string) {
    if (!ref) return '-';
    if (ref.startsWith('TICKET:')) {
      const ticketId = ref.replace('TICKET:', '');
      const shortId = ticketId.slice(0, 8);
      return (
        <Link
          to={`/tickets/${ticketId}`}
          className="inline-flex items-center gap-1 rounded-2xl bg-brand-secondary/70 border border-brand-primary/30 px-2.5 py-0.5 text-xs font-semibold text-orange-800 hover:bg-orange-100 hover:text-brand-primary-dark transition-colors shadow-sm"
        >
          🎟️ Chamado #{shortId}
        </Link>
      );
    }
    return ref;
  }

  if (outMovements.length === 0) {
    return <p className="text-sm text-slate-400 italic">Nenhuma saída registrada ainda.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
          <tr>
            <th className="pb-3 text-left font-medium">Data da Saída</th>
            <th className="pb-3 text-right font-medium">Quantidade</th>
            <th className="pb-3 text-left font-medium">Referência</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {outMovements.map((movement) => (
            <tr key={movement.id} className="hover:bg-slate-50 transition-colors">
              <td className="py-3 text-slate-700">{formatDate(movement.date)}</td>
              <td className="py-3 text-right text-red-600 font-medium">-{movement.quantity}</td>
              <td className="py-3 text-slate-600">{renderReference(movement.reference)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
