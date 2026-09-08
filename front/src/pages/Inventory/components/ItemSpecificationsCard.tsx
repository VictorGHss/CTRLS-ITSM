import { FileText } from 'lucide-react';

interface ItemSpecificationsCardProps {
  specifications?: Record<string, unknown> | null;
}

export default function ItemSpecificationsCard({ specifications }: ItemSpecificationsCardProps) {
  const specs = specifications || {};
  const hasSpecs = Object.keys(specs).length > 0;

  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
      <div className="flex items-center gap-2 mb-4">
        <FileText size={18} className="text-slate-600" />
        <h3 className="text-sm font-semibold text-slate-700">Especificações</h3>
      </div>
      {hasSpecs ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3">
          {Object.entries(specs).map(([key, value]) => (
            <div key={key} className="flex flex-col">
              <span className="text-xs font-medium text-slate-500 uppercase tracking-wide">
                {key}
              </span>
              <span className="text-sm text-slate-800 mt-0.5">
                {String(value)}
              </span>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-sm text-slate-400 italic">Nenhuma especificação cadastrada.</p>
      )}
    </div>
  );
}
