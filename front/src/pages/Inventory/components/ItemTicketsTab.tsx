import { Link } from 'react-router-dom';
import type { Ticket } from '@/types/models';

interface ItemTicketsTabProps {
  tickets: Ticket[];
  ticketsLoading: boolean;
  ticketsPage: number;
  ticketsTotalPages: number;
  onPageChange: (page: number) => void;
  formatDate: (iso: string) => string;
}

export default function ItemTicketsTab({
  tickets,
  ticketsLoading,
  ticketsPage,
  ticketsTotalPages,
  onPageChange,
  formatDate
}: ItemTicketsTabProps) {
  if (ticketsLoading) {
    return (
      <div className="p-12 text-center">
        <div className="animate-pulse space-y-3">
          <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
          <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
        </div>
      </div>
    );
  }

  if (tickets.length === 0) {
    return <p className="text-sm text-slate-400 italic py-4">Nenhum chamado registrado para este equipamento.</p>;
  }

  const statusColors: Record<string, string> = {
    OPEN: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    IN_PROGRESS: 'bg-blue-50 text-blue-700 border-blue-200',
    RESOLVED: 'bg-slate-100 text-slate-600 border-slate-200',
  };

  const statusLabels: Record<string, string> = {
    OPEN: 'Aberto',
    IN_PROGRESS: 'Em Progresso',
    RESOLVED: 'Resolvido',
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
            <tr>
              <th className="pb-3 text-left font-medium">Chamado</th>
              <th className="pb-3 text-left font-medium">Status</th>
              <th className="pb-3 text-left font-medium">Título</th>
              <th className="pb-3 text-left font-medium">Solicitante</th>
              <th className="pb-3 text-left font-medium">Data de Abertura</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {tickets.map((ticket) => {
              const statusCls = statusColors[ticket.status] || 'bg-amber-50 text-amber-700 border-amber-200';
              const statusLabel = statusLabels[ticket.status] || 'Desconhecido';
              const shortId = ticket.id.slice(0, 8).toUpperCase();

              return (
                <tr key={ticket.id} className="hover:bg-slate-50 transition-colors">
                  <td className="py-4 pr-3 text-slate-700">
                    <Link
                      to={`/tickets/${ticket.id}`}
                      className="inline-flex items-center gap-1 rounded-2xl bg-brand-secondary/70 border border-brand-primary/30 px-2.5 py-0.5 text-xs font-semibold text-orange-800 hover:bg-orange-100 hover:text-brand-primary-dark transition-colors shadow-sm"
                    >
                      🎟️ Chamado #{shortId}
                    </Link>
                  </td>
                  <td className="py-4 pr-3">
                    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-semibold ${statusCls}`}>
                      {statusLabel}
                    </span>
                  </td>
                  <td className="py-4 pr-3 text-slate-700 max-w-xs truncate" title={ticket.title}>
                    {ticket.title}
                  </td>
                  <td className="py-4 pr-3 text-slate-600">
                    {ticket.requesterName || 'Sistema'}
                  </td>
                  <td className="py-4 pr-3 text-slate-600">
                    {formatDate(ticket.createdAt)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Notas de Solução para chamados finalizados */}
      {tickets.some((t) => t.status === 'RESOLVED' && t.solutionText) && (
        <div className="mt-4 border-t border-slate-150 pt-6">
          <h4 className="text-xs font-bold uppercase tracking-widest text-slate-400 mb-3">Notas de Solução (Histórico de Reparos)</h4>
          <div className="flex flex-col gap-3">
            {tickets
              .filter((t) => t.status === 'RESOLVED' && t.solutionText)
              .map((t) => (
                <div key={t.id} className="bg-slate-50 border border-slate-200 rounded-xl p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-slate-700">Chamado #{t.id.slice(0, 8).toUpperCase()}</span>
                    <span className="text-xs text-slate-400">{formatDate(t.closedAt || '')}</span>
                  </div>
                  <p className="text-sm text-slate-800 font-semibold">{t.title}</p>
                  <div className="mt-2 text-xs text-slate-600 bg-white border border-slate-150 p-3 rounded-lg italic shadow-sm">
                    "{t.solutionText}"
                  </div>
                </div>
              ))}
          </div>
        </div>
      )}

      {/* Barra de Paginação para os chamados do item */}
      {ticketsTotalPages > 1 && (
        <div className="flex items-center justify-between border-t border-slate-100 pt-4 mt-2">
          <button
            type="button"
            onClick={() => onPageChange(Math.max(0, ticketsPage - 1))}
            disabled={ticketsPage === 0 || ticketsLoading}
            className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 border border-slate-200 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors"
          >
            Anterior
          </button>
          <span className="text-xs text-slate-500 font-semibold">
            Página {ticketsPage + 1} de {ticketsTotalPages}
          </span>
          <button
            type="button"
            onClick={() => onPageChange(Math.min(ticketsTotalPages - 1, ticketsPage + 1))}
            disabled={ticketsPage >= ticketsTotalPages - 1 || ticketsLoading}
            className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 border border-slate-200 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl transition-colors"
          >
            Seguinte
          </button>
        </div>
      )}
    </div>
  );
}
