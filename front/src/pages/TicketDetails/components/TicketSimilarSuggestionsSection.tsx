import { useState, useEffect } from 'react';
import { getSimilarTickets } from '../../../services/ticketService';
import type { Ticket } from '../../../types/models';

interface TicketSimilarSuggestionsSectionProps {
  ticket: Ticket;
  onApplyMacro?: (macroText: string) => void;
}

export function TicketSimilarSuggestionsSection({
  ticket,
  onApplyMacro,
}: TicketSimilarSuggestionsSectionProps) {
  const [similarTickets, setSimilarTickets] = useState<Ticket[]>([]);
  const [loadingSimilar, setLoadingSimilar] = useState(false);
  const [isAccordionOpen, setIsAccordionOpen] = useState(true);

  const macroTag = ticket.tags?.find((t) => t.defaultResolution && t.defaultResolution.trim());

  useEffect(() => {
    if (!ticket?.id || ticket.status !== 'IN_PROGRESS') {
      setSimilarTickets([]);
      return;
    }

    async function loadSimilar() {
      setLoadingSimilar(true);
      try {
        const data = await getSimilarTickets(ticket.id);
        setSimilarTickets(data);
      } catch (err) {
        console.error('Erro ao carregar chamados similares:', err);
      } finally {
        setLoadingSimilar(false);
      }
    }
    void loadSimilar();
  }, [ticket?.id, ticket?.status]);

  if (ticket.status !== 'IN_PROGRESS') {
    return null;
  }

  return (
    <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm overflow-hidden transition-all duration-300">
      <button
        type="button"
        onClick={() => setIsAccordionOpen(!isAccordionOpen)}
        className="flex items-center justify-between w-full text-left font-semibold text-slate-700 text-sm focus:outline-none"
      >
        <span className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-brand-primary animate-pulse" />
          Chamados Similares / Base de Conhecimento
        </span>
        <span
          className="text-xs text-slate-400 transform transition-transform duration-200"
          style={{ transform: isAccordionOpen ? 'rotate(180deg)' : 'rotate(0deg)' }}
        >
          ▼
        </span>
      </button>

      {isAccordionOpen && (
        <div className="mt-4 space-y-4">
          {macroTag && (
            <div className="rounded-xl border border-brand-primary/20 bg-brand-secondary/15 p-3 flex flex-col gap-2 shadow-sm">
              <p className="text-xs text-slate-500 font-medium leading-relaxed">
                Uma macro de resolução padrão foi localizada para a tag{' '}
                <span className="font-bold" style={{ color: macroTag.color }}>
                  {macroTag.name}
                </span>
                .
              </p>
              <button
                type="button"
                onClick={() => onApplyMacro && onApplyMacro(macroTag.defaultResolution || '')}
                className="w-full text-center py-2 px-3 bg-brand-primary hover:bg-brand-primary-dark text-white rounded-xl text-xs font-bold transition-all hover:scale-102 active:scale-98 shadow-sm flex items-center justify-center gap-1.5"
              >
                🚀 Aplicar Solução Padrão
              </button>
            </div>
          )}

          {loadingSimilar ? (
            <p className="text-xs text-slate-400 italic">Buscando chamados similares...</p>
          ) : similarTickets.length === 0 ? (
            <p className="text-xs text-slate-400 italic">
              Nenhum chamado similar resolvido com estas tags.
            </p>
          ) : (
            <div className="space-y-3 max-h-60 overflow-y-auto pr-1">
              {similarTickets.map((t) => (
                <div
                  key={t.id}
                  className="rounded-xl border border-slate-100 bg-slate-50/50 p-3 text-xs flex flex-col gap-1.5 shadow-sm"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-slate-700 truncate max-w-[150px]">{t.title}</span>
                    <span className="text-[10px] text-brand-primary font-bold bg-brand-secondary/20 px-2 py-0.5 rounded-full shrink-0">
                      #{t.id.slice(0, 8).toUpperCase()}
                    </span>
                  </div>
                  <p className="text-slate-500 line-clamp-2 leading-relaxed">{t.description}</p>
                  {t.solutionText && (
                    <div className="border-t border-slate-100 pt-1.5 mt-1">
                      <p className="text-[10px] text-emerald-600 font-bold uppercase tracking-wider">
                        Solução Aplicada:
                      </p>
                      <p className="text-slate-600 leading-relaxed bg-white border border-slate-100 rounded-lg p-2 mt-1 whitespace-pre-wrap">
                        {t.solutionText}
                      </p>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  );
}
