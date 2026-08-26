import {
  Calendar,
  CheckCircle2,
  Clock,
  Package,
  Tag,
  UserRound,
} from 'lucide-react';
import SlaBadge from '@/components/ui/SlaBadge';
import SearchableDropdown from '@/components/common/SearchableDropdown';
import type { Ticket, TicketCategory } from '../../../types/models';

interface TicketInfoSectionProps {
  ticket: Ticket;
  canManageTicket: boolean;
  categories: TicketCategory[];
  loadingCategories: boolean;
  updatingCategory: boolean;
  onChangeCategory: (categoryId: string) => void;
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '-';

  try {
    return new Date(iso).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '-';
  }
}

export function TicketInfoSection({
  ticket,
  canManageTicket,
  categories,
  loadingCategories,
  updatingCategory,
  onChangeCategory,
}: TicketInfoSectionProps) {
  const handleCategoryChange = (categoryId: string) => {
    if (categoryId === ticket.categoryId) return;
    void onChangeCategory(categoryId);
  };

  return (
    <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
      <h3 className="mb-4 text-sm font-semibold text-slate-700">Informações</h3>
      <ul className="flex flex-col gap-3 text-sm">
        <li className="flex items-start gap-2.5 text-slate-600">
          <UserRound size={15} className="mt-0.5 shrink-0 text-slate-400" />
          <div>
            <p className="text-xs text-slate-400">Solicitante</p>
            <p className="font-medium text-slate-700">{ticket.requesterName}</p>
          </div>
        </li>
        <li className="flex items-start gap-2.5 text-slate-600">
          <Tag size={15} className="mt-0.5 shrink-0 text-slate-400" />
          <div>
            <p className="text-xs text-slate-400">Categoria</p>
            {canManageTicket ? (
              <div className="mt-1">
                <SearchableDropdown
                  options={
                    loadingCategories
                      ? [{ id: ticket.categoryId, name: 'Carregando...' }]
                      : categories.length === 0
                      ? [{ id: ticket.categoryId, name: ticket.categoryName || 'Sem categoria' }]
                      : categories.map((c) => ({ id: c.id, name: c.name }))
                  }
                  value={ticket.categoryId}
                  onChange={handleCategoryChange}
                  disabled={loadingCategories || updatingCategory}
                  placeholder="Selecione uma categoria..."
                />
              </div>
            ) : (
              <p className="font-medium text-slate-700">{ticket.categoryName}</p>
            )}
          </div>
        </li>
        <li className="flex items-start gap-2.5 text-slate-600">
          <Calendar size={15} className="mt-0.5 shrink-0 text-slate-400" />
          <div>
            <p className="text-xs text-slate-400">Criado em</p>
            <p className="font-medium text-slate-700">{formatDate(ticket.createdAt)}</p>
          </div>
        </li>
        <li className="flex items-start gap-2.5 text-slate-600">
          <Clock size={15} className="mt-0.5 shrink-0 text-slate-400" />
          <div>
            <p className="text-xs text-slate-400">Prazo SLA</p>
            <div className="flex items-center gap-2">
              <p className="font-medium text-slate-700">
                {ticket.slaDeadline ? formatDate(ticket.slaDeadline) : 'Sem prazo'}
              </p>
              <SlaBadge
                deadline={ticket.slaDeadline}
                status={ticket.status}
                closedAt={ticket.closedAt}
              />
            </div>
          </div>
        </li>
        {ticket.requestedItemName && (
          <li className="flex items-start gap-2.5 text-slate-600">
            <Package size={15} className="mt-0.5 shrink-0 text-slate-400" />
            <div>
              <p className="text-xs text-slate-400">Item Solicitado</p>
              <p className="font-medium text-slate-700">
                {ticket.requestedItemName}
                {ticket.requestedQuantity != null && (
                  <span className="font-normal text-slate-400"> x {ticket.requestedQuantity}</span>
                )}
              </p>
            </div>
          </li>
        )}
        {ticket.closedAt && (
          <li className="flex items-start gap-2.5 text-slate-600">
            <CheckCircle2 size={15} className="mt-0.5 shrink-0 text-green-500" />
            <div>
              <p className="text-xs text-slate-400">Fechado em</p>
              <p className="font-medium text-slate-700">{formatDate(ticket.closedAt)}</p>
            </div>
          </li>
        )}
      </ul>
    </section>
  );
}
