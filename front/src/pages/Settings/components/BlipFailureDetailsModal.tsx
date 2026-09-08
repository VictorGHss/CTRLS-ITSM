import { motion, AnimatePresence } from 'framer-motion';
import { X, Check, Copy } from 'lucide-react';
import type { BlipDeliveryFailure } from '@/types/models';
import { formatDateTime } from '../utils/blipErrorClassifiers';

interface BlipFailureDetailsModalProps {
  failure: BlipDeliveryFailure | null;
  onClose: () => void;
  onCopy: (text: string, type: 'trace' | 'message') => void;
  copiedId: string | null;
}

export default function BlipFailureDetailsModal({
  failure,
  onClose,
  onCopy,
  copiedId,
}: BlipFailureDetailsModalProps) {
  return (
    <AnimatePresence>
      {failure && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm">
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            className="w-full max-w-xl bg-white rounded-2xl shadow-xl overflow-hidden"
          >
            {/* Cabeçalho do Modal */}
            <header className="border-b border-slate-100 px-6 py-5 flex items-center justify-between bg-slate-50">
              <div>
                <h3 className="text-base font-bold text-slate-900">Detalhes da Falha de Entrega</h3>
                <p className="mt-0.5 text-xs text-slate-500">
                  Auditoria técnica do erro capturado via webhook
                </p>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-200 rounded-lg transition-colors"
              >
                <X size={16} />
              </button>
            </header>

            {/* Corpo de Informações Técnicas */}
            <div className="p-6 space-y-4 max-h-[70vh] overflow-y-auto">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    ID do Agendamento
                  </span>
                  <strong className="text-sm text-slate-800">{failure.appointmentId || 'N/A'}</strong>
                </div>
                <div>
                  <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    Código de Erro (Meta)
                  </span>
                  <strong className="text-sm text-slate-800">{failure.errorCode || '-'}</strong>
                </div>
              </div>

              <div>
                <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  Data do Registro
                </span>
                <p className="text-sm text-slate-700 font-medium">{formatDateTime(failure.createdAt)}</p>
              </div>

              <div>
                <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  Descrição do Erro
                </span>
                <div className="mt-1 p-3.5 bg-rose-50 border border-rose-100 rounded-xl text-xs font-mono text-rose-800 whitespace-pre-wrap break-all shadow-inner">
                  {failure.errorMessage || 'Descrição indisponível.'}
                </div>
              </div>

              {/* Identificadores adicionais para rastreabilidade ponta a ponta */}
              <div className="border-t border-slate-100 pt-4 space-y-3">
                <div>
                  <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    ID da Mensagem Original (Blip)
                  </span>
                  <div className="flex items-center justify-between p-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono text-slate-600">
                    <span className="break-all select-all">{failure.messageId}</span>
                    <button
                      type="button"
                      onClick={() => onCopy(failure.messageId, 'message')}
                      className="p-1.5 text-slate-400 hover:text-[#feb56c] hover:bg-slate-100 rounded-lg transition-colors ml-2"
                      title="Copiar Message ID"
                    >
                      {copiedId === failure.messageId ? (
                        <Check size={13} className="text-emerald-500" />
                      ) : (
                        <Copy size={13} />
                      )}
                    </button>
                  </div>
                </div>

                <div>
                  <span className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    Trace ID (Rastreamento Unificado)
                  </span>
                  <div className="flex items-center justify-between p-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono text-slate-600">
                    <span className="break-all select-all">{failure.traceId || '-'}</span>
                    {failure.traceId && (
                      <button
                        type="button"
                        onClick={() => onCopy(failure.traceId, 'trace')}
                        className="p-1.5 text-slate-400 hover:text-[#feb56c] hover:bg-slate-100 rounded-lg transition-colors ml-2"
                        title="Copiar Trace ID"
                      >
                        {copiedId === failure.traceId ? (
                          <Check size={13} className="text-emerald-500" />
                        ) : (
                          <Copy size={13} />
                        )}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Ações do Modal */}
            <footer className="border-t border-slate-100 px-6 py-4 flex justify-end bg-slate-50">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 transition-colors shadow-sm"
              >
                Fechar
              </button>
            </footer>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
