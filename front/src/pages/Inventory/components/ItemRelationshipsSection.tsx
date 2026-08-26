import { Link } from 'react-router-dom';
import { Cpu, Plus, History, Package } from 'lucide-react';
import type { Item, ItemAllocation } from '@/types/models';

interface ItemRelationshipsSectionProps {
  item: Item;
  allocations: ItemAllocation[];
  allocationsLoading: boolean;
  onOpenLinkModal: () => void;
  onOpenAllocateModal: () => void;
  formatDate: (iso: string) => string;
}

export default function ItemRelationshipsSection({
  item,
  allocations,
  allocationsLoading,
  onOpenLinkModal,
  onOpenAllocateModal,
  formatDate
}: ItemRelationshipsSectionProps) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-4 mb-4">
        <div className="flex items-center gap-2">
          <Cpu size={18} className="text-slate-600" />
          <h3 className="text-sm font-semibold text-slate-700">Componentes e Vínculos</h3>
        </div>

        {/* Ações apenas para ativos principais (não consumíveis) */}
        {!item.isConsumable && (
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onOpenLinkModal}
              className="inline-flex items-center gap-1 text-xs font-bold text-brand-primary bg-brand-secondary/50 hover:bg-brand-secondary px-3 py-1.5 rounded-lg border border-brand-primary/30 transition-all shadow-sm"
            >
              <Plus size={14} />
              Acoplar Ativo Componente
            </button>
            <button
              type="button"
              onClick={onOpenAllocateModal}
              className="inline-flex items-center gap-1 text-xs font-bold text-green-700 bg-green-50 hover:bg-green-100 px-3 py-1.5 rounded-lg border border-green-200 transition-all shadow-sm"
            >
              <Plus size={14} />
              Alocar Consumível / Periférico
            </button>
          </div>
        )}
      </div>

      {/* CENÁRIO C - Se for um consumível (ex.: Toner) */}
      {item.isConsumable ? (
        <div>
          <p className="text-xs text-slate-400 font-bold uppercase tracking-wider mb-3 flex items-center gap-1.5">
            <History size={14} /> HISTÓRICO DE DISTRIBUIÇÃO E CONSUMO
          </p>
          {allocationsLoading ? (
            <div className="p-4 text-center text-xs text-slate-400 animate-pulse">Carregando histórico...</div>
          ) : allocations.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="pb-2 text-left font-medium">Equipamento Destinatário</th>
                    <th className="pb-2 text-right font-medium">Qtd. Alocada</th>
                    <th className="pb-2 text-center font-medium">Data de Alocação</th>
                    <th className="pb-2 text-left font-medium pl-4">Técnico</th>
                    <th className="pb-2 text-center font-medium">Chamado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {allocations.map((alloc) => (
                    <tr key={alloc.id} className="hover:bg-slate-50 transition-colors">
                      <td className="py-2.5 text-slate-700 font-medium">
                        <Link to={`/inventory/${alloc.parentItemId}`} className="hover:underline text-brand-primary">
                          {alloc.parentItemName}
                        </Link>
                      </td>
                      <td className="py-2.5 text-right text-slate-700 font-semibold">{alloc.quantity}</td>
                      <td className="py-2.5 text-center text-slate-500 text-xs">{formatDate(alloc.allocatedAt)}</td>
                      <td className="py-2.5 text-slate-600 pl-4 text-xs">{alloc.allocatedByName}</td>
                      <td className="py-2.5 text-center">
                        {alloc.ticketId ? (
                          <Link
                            to={`/tickets/${alloc.ticketId}`}
                            className="inline-flex items-center gap-1 rounded-2xl bg-orange-50 border border-orange-200 px-2 py-0.5 text-[10px] font-bold text-orange-700 hover:bg-orange-100"
                          >
                            🎟️ Chamado #{alloc.ticketId.slice(0, 8).toUpperCase()}
                          </Link>
                        ) : (
                          <span className="text-slate-400 text-xs">-</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="text-xs text-slate-400 italic py-2">Este consumível ainda não foi alocado a nenhuma impressora ou equipamento.</p>
          )}
        </div>
      ) : (
        /* CENÁRIO A - Se for ativo principal (ex.: Computador) */
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Bloco 1: Componentes Acoplados (Filhos) */}
          <div>
            <p className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Cpu size={14} className="text-slate-400" /> Ativos Filhos Acoplados
            </p>
            {item.components && item.components.length > 0 ? (
              <div className="border border-slate-100 rounded-xl overflow-hidden shadow-sm">
                <table className="w-full text-xs">
                  <thead className="bg-slate-50 text-slate-500 uppercase tracking-wider font-semibold border-b border-slate-100">
                    <tr>
                      <th className="px-4 py-2 text-left">Nome do Dispositivo</th>
                      <th className="px-4 py-2 text-left">Categoria</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {item.components.map((comp) => (
                      <tr key={comp.id} className="hover:bg-slate-50">
                        <td className="px-4 py-2.5 font-medium">
                          <Link to={`/inventory/${comp.id}`} className="text-brand-primary hover:underline">
                            {comp.name}
                          </Link>
                        </td>
                        <td className="px-4 py-2.5 text-slate-500">{comp.itemCategoryName}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-xs text-slate-400 italic py-4 bg-slate-50/50 rounded-xl border border-dashed border-slate-200 text-center">
                Nenhum componente (ex.: Monitor, Nobreak) acoplado a este ativo.
              </p>
            )}
          </div>

          {/* Bloco 2: Consumíveis / Insumos Instalados */}
          <div>
            <p className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Package size={14} className="text-slate-400" /> Insumos e Periféricos Alocados
            </p>
            {allocationsLoading ? (
              <div className="p-4 text-center text-xs text-slate-400 animate-pulse">Carregando alocações...</div>
            ) : allocations.length > 0 ? (
              <div className="border border-slate-100 rounded-xl overflow-hidden shadow-sm">
                <table className="w-full text-xs">
                  <thead className="bg-slate-50 text-slate-500 uppercase tracking-wider font-semibold border-b border-slate-100">
                    <tr>
                      <th className="px-4 py-2 text-left">Insumo</th>
                      <th className="px-4 py-2 text-right">Qtd</th>
                      <th className="px-4 py-2 text-center">Instalação</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {allocations.map((alloc) => (
                      <tr key={alloc.id} className="hover:bg-slate-50">
                        <td className="px-4 py-2.5 font-medium">
                          <Link to={`/inventory/${alloc.childItemId}`} className="text-brand-primary hover:underline">
                            {alloc.childItemName}
                          </Link>
                        </td>
                        <td className="px-4 py-2.5 text-right font-bold text-slate-700">{alloc.quantity}</td>
                        <td className="px-4 py-2.5 text-center text-slate-400 text-[10px]">{formatDate(alloc.allocatedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-xs text-slate-400 italic py-4 bg-slate-50/50 rounded-xl border border-dashed border-slate-200 text-center">
                Nenhum consumível (ex.: Toner, Mouse) alocado a este ativo.
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
