import { useEffect, useState, useCallback, useTransition } from 'react';
import { Search, Copy, Check, Eye, AlertCircle, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react';
import { toast } from 'react-toastify';

import { getBlipDeliveryFailures } from '../../services/appointmentService';
import type { BlipDeliveryFailure } from '../../types/models';
import BlipFailureDetailsModal from './components/BlipFailureDetailsModal';
import {
  categoryFriendlyNames,
  getCategoryBadgeStyle,
  classifyErrorCode,
  formatDateTime,
} from './utils/blipErrorClassifiers';

/**
 * Painel de auditoria que lista as falhas de entrega de mensagens do Blip de forma paginada.
 * Oferece suporte a filtros em tempo real por agendamento Feegow (appointmentId) e categoria do erro.
 */
export default function BlipDeliveryFailuresPanel() {
  const [failures, setFailures] = useState<BlipDeliveryFailure[]>([]);
  const [loading, setLoading] = useState(true);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [appointmentIdInput, setAppointmentIdInput] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');

  const [selectedFailure, setSelectedFailure] = useState<BlipDeliveryFailure | null>(null);
  const [isPending, startTransition] = useTransition();

  const loadFailures = useCallback(async (page: number, apptId: string, cat: string) => {
    try {
      setLoading(true);
      const data = await getBlipDeliveryFailures({
        page,
        size: 15,
        appointmentId: apptId.trim() || undefined,
        category: cat || undefined,
      });

      setFailures(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (error) {
      console.error('Erro ao carregar falhas de entrega do Blip:', error);
      toast.error('Não foi possível obter o histórico de falhas de entrega do Blip.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    startTransition(() => {
      void loadFailures(currentPage, appointmentIdInput, selectedCategory);
    });
  }, [currentPage, appointmentIdInput, selectedCategory, loadFailures]);

  const handleCopyToClipboard = (text: string, type: 'trace' | 'message') => {
    void navigator.clipboard.writeText(text);
    setCopiedId(text);
    toast.success(`${type === 'trace' ? 'Trace ID' : 'ID da Mensagem'} copiado para a área de transferência!`);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleResetFilters = () => {
    setAppointmentIdInput('');
    setSelectedCategory('');
    setCurrentPage(0);
  };

  return (
    <section className="w-full rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden mt-6">
      {/* Cabeçalho do Painel */}
      <header className="border-b border-slate-100 px-6 py-5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-base font-bold text-slate-900">Histórico de Falhas de Entrega (Blip)</h2>
          <p className="mt-0.5 text-xs text-slate-500">
            Monitoramento em tempo real de mensagens não entregues via WhatsApp Business API
          </p>
        </div>

        <button
          type="button"
          disabled={loading || isPending}
          onClick={() => {
            startTransition(() => {
              void loadFailures(currentPage, appointmentIdInput, selectedCategory);
            });
          }}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-200 bg-slate-50 text-xs font-semibold text-slate-600 hover:bg-slate-100 hover:text-slate-800 transition-colors disabled:opacity-50"
        >
          <RefreshCw size={13} className={loading || isPending ? 'animate-spin' : ''} />
          Atualizar
        </button>
      </header>

      {/* Barra de Filtros */}
      <div className="p-6 border-b border-slate-100 bg-slate-50/50 flex flex-wrap items-center gap-4">
        {/* Filtro por ID do Agendamento Feegow */}
        <div className="relative flex-1 min-w-[200px] max-w-sm">
          <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Filtrar por ID do agendamento..."
            value={appointmentIdInput}
            onChange={(e) => {
              setAppointmentIdInput(e.target.value);
              setCurrentPage(0);
            }}
            className="w-full pl-9 pr-3.5 py-2 text-xs rounded-xl border border-slate-200 bg-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/30 focus:border-[#feb56c] transition-all"
          />
        </div>

        {/* Filtro por Categoria do Erro */}
        <div className="w-full sm:w-64">
          <select
            value={selectedCategory}
            onChange={(e) => {
              setSelectedCategory(e.target.value);
              setCurrentPage(0);
            }}
            aria-label="Filtrar por categoria"
            className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 bg-white text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/30 focus:border-[#feb56c] transition-all cursor-pointer"
          >
            <option value="">Todas as categorias</option>
            {Object.entries(categoryFriendlyNames).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </div>

        {(appointmentIdInput || selectedCategory) && (
          <button
            type="button"
            onClick={handleResetFilters}
            className="text-xs font-semibold text-[#feb56c] hover:text-[#e5944c] hover:underline transition-colors"
          >
            Limpar Filtros
          </button>
        )}
      </div>

      {/* Tabela de Falhas de Entrega */}
      <div className="overflow-x-auto relative min-h-[300px]">
        {(loading || isPending) && (
          <div className="absolute inset-0 bg-white/70 backdrop-blur-[1px] flex items-center justify-center z-10 transition-all">
            <div className="flex flex-col items-center gap-2">
              <RefreshCw size={24} className="animate-spin text-[#feb56c]" />
              <span className="text-xs font-medium text-slate-500">Consultando base de auditoria...</span>
            </div>
          </div>
        )}

        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                Data e Hora
              </th>
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                Agendamento (Feegow)
              </th>
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                Categoria de Erro
              </th>
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                Código
              </th>
              <th className="px-6 py-3.5 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                Trace ID
              </th>
              <th className="px-6 py-3.5 text-center text-xs font-semibold uppercase tracking-wider text-slate-500">
                Ações
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {failures.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-6 py-16 text-center">
                  <div className="flex flex-col items-center justify-center gap-3">
                    <AlertCircle size={32} className="text-slate-300" />
                    <div>
                      <p className="text-sm font-semibold text-slate-700">Nenhuma falha de entrega registrada</p>
                      <p className="text-xs text-slate-400 mt-1">Experimente alterar os critérios de filtro.</p>
                    </div>
                  </div>
                </td>
              </tr>
            ) : (
              failures.map((item) => {
                const categoryTag = classifyErrorCode(item.errorCode || 0);
                return (
                  <tr key={item.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap text-xs text-slate-600">
                      {formatDateTime(item.createdAt)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-slate-800">
                      {item.appointmentId || <span className="text-slate-400 font-normal">N/A</span>}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${getCategoryBadgeStyle(
                          categoryTag,
                        )}`}
                      >
                        {categoryFriendlyNames[categoryTag] || 'Desconhecido'}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-mono text-slate-700">
                      {item.errorCode ?? '-'}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-xs text-slate-500">
                      {item.traceId ? (
                        <div className="flex items-center gap-1.5">
                          <span className="font-mono">{item.traceId.slice(0, 8)}...</span>
                          <button
                            type="button"
                            onClick={() => handleCopyToClipboard(item.traceId, 'trace')}
                            className="p-1 text-slate-400 hover:text-[#feb56c] hover:bg-slate-100 rounded-lg transition-colors"
                            title="Copiar Trace ID completo"
                          >
                            {copiedId === item.traceId ? (
                              <Check size={12} className="text-emerald-500" />
                            ) : (
                              <Copy size={12} />
                            )}
                          </button>
                        </div>
                      ) : (
                        '-'
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-center">
                      <button
                        type="button"
                        onClick={() => setSelectedFailure(item)}
                        className="inline-flex items-center gap-1 text-xs font-semibold text-slate-600 hover:text-[#feb56c] px-2.5 py-1.5 rounded-lg hover:bg-slate-100 transition-colors"
                      >
                        <Eye size={13} />
                        Detalhes
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Rodapé e Navegação da Paginação */}
      {totalPages > 1 && (
        <footer className="border-t border-slate-100 px-6 py-4 flex items-center justify-between bg-slate-50/50">
          <span className="text-xs text-slate-500">
            Página <strong className="text-slate-800">{currentPage + 1}</strong> de{' '}
            <strong className="text-slate-800">{totalPages}</strong> (Exibindo {failures.length} de{' '}
            {totalElements} falhas)
          </span>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              disabled={currentPage === 0 || loading || isPending}
              onClick={() => setCurrentPage((prev) => Math.max(0, prev - 1))}
              className="p-1.5 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ChevronLeft size={16} />
            </button>
            <button
              type="button"
              disabled={currentPage >= totalPages - 1 || loading || isPending}
              onClick={() => setCurrentPage((prev) => Math.min(totalPages - 1, prev + 1))}
              className="p-1.5 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        </footer>
      )}

      {/* Modal de Detalhes da Auditoria */}
      <BlipFailureDetailsModal
        failure={selectedFailure}
        onClose={() => setSelectedFailure(null)}
        onCopy={handleCopyToClipboard}
        copiedId={copiedId}
      />
    </section>
  );
}
