import { Link } from 'react-router-dom';
import { Package } from 'lucide-react';
import type { Item } from '@/types/models';

interface ItemHeaderCardProps {
  item: Item;
}

export default function ItemHeaderCard({ item }: ItemHeaderCardProps) {
  const hasStock = item.currentStock > 0;

  return (
    <>
      {/* Header do item */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
        <div className="flex flex-wrap items-start justify-between gap-3 mb-3">
          <div className="flex-1">
            <h2 className="text-xl font-bold text-slate-800 mb-1">{item.name}</h2>
            <p className="text-sm text-slate-500">{item.itemCategoryName}</p>
          </div>
          <span
            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
              hasStock ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
            }`}
          >
            {hasStock ? 'Em Estoque' : 'Sem Estoque'}
          </span>
        </div>
        <div className="mt-4 pt-4 border-t border-slate-100">
          <div className="flex items-center gap-2">
            <Package size={16} className="text-slate-400" />
            <span className="text-sm text-slate-600">
              Estoque Atual:{' '}
              <span className={`font-semibold ${hasStock ? 'text-green-600' : 'text-red-600'}`}>
                {item.currentStock} {item.currentStock === 1 ? 'unidade' : 'unidades'}
              </span>
            </span>
          </div>
        </div>
      </div>

      {/* Banner se possuir pai associado */}
      {!item.isConsumable && item.parent && (
        <div className="bg-blue-50 text-brand-primary border border-blue-100 rounded-xl p-4 mb-6 flex items-center justify-between shadow-sm">
          <div>
            <p className="text-sm font-semibold text-blue-900">Equipamento Vinculado</p>
            <p className="text-xs text-blue-800 mt-0.5 font-medium">
              Este dispositivo está atualmente acoplado ao ativo principal:{' '}
              <strong className="text-brand-primary font-bold">{item.parent.name}</strong>.
            </p>
          </div>
          <Link
            to={`/inventory/${item.parent.id}`}
            className="text-xs font-bold text-brand-primary hover:text-brand-primary-dark hover:underline bg-white border border-blue-200 px-3 py-1.5 rounded-lg shadow-sm transition-colors"
          >
            Ver Equipamento Pai
          </Link>
        </div>
      )}
    </>
  );
}
