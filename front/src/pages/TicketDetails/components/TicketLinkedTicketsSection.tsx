import { useState, useEffect } from 'react';
import { Search, Link2, Loader2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { toast } from 'react-toastify';
import { getTickets, relateTicket, linkTicket } from '../../../services/ticketService';
import type { Ticket } from '../../../types/models';

interface TicketLinkedTicketsSectionProps {
  ticket: Ticket;
  onRefresh?: () => void;
}

export function TicketLinkedTicketsSection({
  ticket,
  onRefresh,
}: TicketLinkedTicketsSectionProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [suggestions, setSuggestions] = useState<Ticket[]>([]);
  const [loadingTickets, setLoadingTickets] = useState(false);
  const [associatingId, setAssociatingId] = useState<string | null>(null);

  // Pesquisa dinamicamente os chamados conforme digita (debounced)
  useEffect(() => {
    const query = searchQuery.trim().replaceAll(/^#/g, '').toLowerCase();
    if (!query) {
      setSuggestions([]);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        setLoadingTickets(true);
        const data = await getTickets(undefined, 0, query);
        const items = Array.isArray(data) ? data : (data?.content ?? []);

        const linkedIds = (ticket.linkedTickets ?? []).map((lt) => lt.id);
        const relatedIds = ticket.relatedTicketIds ?? [];
        const existingIds = new Set([...linkedIds, ...relatedIds]);

        const filtered = items.filter((t) => {
          if (t.id === ticket.id) return false;
          if (existingIds.has(t.id)) return false;
          return true;
        });

        setSuggestions(filtered.slice(0, 5));
      } catch (err) {
        console.error('Erro ao pesquisar chamados para associação:', err);
      } finally {
        setLoadingTickets(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [searchQuery, ticket.id, ticket.relatedTicketIds, ticket.linkedTickets]);

  const handleAssociate = async (targetId: string) => {
    try {
      setAssociatingId(targetId);
      try {
        await linkTicket(ticket.id, targetId);
      } catch {
        await relateTicket(ticket.id, targetId);
      }
      toast.success('Chamado associado com sucesso!');
      setSearchQuery('');
      setSuggestions([]);
      if (onRefresh) {
        onRefresh();
      }
    } catch (err) {
      console.error('Erro ao associar chamado:', err);
      toast.error('Não foi possível associar o chamado.');
    } finally {
      setAssociatingId(null);
    }
  };

  const hasLinkedOrRelated =
    (ticket.linkedTickets && ticket.linkedTickets.length > 0) ||
    (ticket.relatedTicketIds && ticket.relatedTicketIds.length > 0);

  return (
    <>
      {/* ── Bloco: Associar Chamado ── */}
      <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
        <h3 className="mb-4 text-sm font-semibold text-slate-700 flex items-center gap-1.5">
          <Link2 size={16} className="text-brand-primary" />
          Associar Chamado
        </h3>

        <div className="relative">
          <div className="relative">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Pesquisar por ID ou título..."
              className="w-full rounded-xl border border-slate-200 bg-white pl-9 pr-3.5 py-2 text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition-all shadow-sm"
            />
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
              <Search size={15} />
            </div>
          </div>

          {suggestions.length > 0 && (
            <ul className="absolute left-0 right-0 mt-2 z-10 rounded-2xl border border-slate-200 bg-white p-1.5 shadow-lg max-h-60 overflow-y-auto">
              {suggestions.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    onClick={() => void handleAssociate(s.id)}
                    disabled={associatingId !== null}
                    className="flex flex-col items-start w-full text-left rounded-xl p-2.5 hover:bg-slate-50 transition-colors text-sm"
                  >
                    <span className="font-semibold text-slate-800 line-clamp-1">{s.title}</span>
                    <span className="mt-0.5 text-xs text-slate-400 flex items-center gap-1">
                      {associatingId === s.id ? (
                        <Loader2 size={12} className="animate-spin text-brand-primary" />
                      ) : (
                        `#${s.id.slice(0, 8).toUpperCase()}`
                      )}
                      · {s.requesterName}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {searchQuery.trim() !== '' && suggestions.length === 0 && !loadingTickets && (
            <p className="mt-2 text-xs italic text-slate-400">Nenhum chamado localizado.</p>
          )}
        </div>
      </section>

      {/* ── Bloco: Chamados Vinculados ── */}
      {hasLinkedOrRelated && (
        <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
          <h3 className="mb-4 text-sm font-semibold text-slate-700 flex items-center gap-1.5">
            <Link2 size={16} className="text-brand-primary" />
            Chamados Vinculados
          </h3>
          <ul className="flex flex-col gap-2">
            {ticket.linkedTickets && ticket.linkedTickets.length > 0 ? (
              ticket.linkedTickets.map((linked) => (
                <li key={linked.id}>
                  <Link
                    to={`/tickets/${linked.id}`}
                    className="flex flex-col gap-0.5 text-xs font-semibold text-brand-primary hover:text-brand-primary-dark transition-colors bg-brand-secondary/20 hover:bg-brand-secondary/40 px-3 py-2 rounded-xl border border-brand-primary/10 w-full"
                  >
                    <span className="flex items-center justify-between">
                      <span>Chamado #{linked.number || linked.id.slice(0, 8).toUpperCase()}</span>
                      <span className="text-[10px] uppercase font-bold text-slate-500">
                        {linked.status}
                      </span>
                    </span>
                    <span className="text-slate-700 font-normal truncate">{linked.title}</span>
                  </Link>
                </li>
              ))
            ) : (
              ticket.relatedTicketIds?.map((relId) => (
                <li key={relId}>
                  <Link
                    to={`/tickets/${relId}`}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand-primary hover:text-brand-primary-dark transition-colors break-all bg-brand-secondary/20 hover:bg-brand-secondary/40 px-3 py-2 rounded-xl border border-brand-primary/10 w-full"
                  >
                    Chamado #{relId.slice(0, 8).toUpperCase()}
                  </Link>
                </li>
              ))
            )}
          </ul>
        </section>
      )}
    </>
  );
}
