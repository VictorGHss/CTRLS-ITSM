import { Laptop, Monitor } from 'lucide-react';
import type { Asset } from '../../../types/models';

interface TicketRequesterAssetsSectionProps {
  assets: Asset[];
  loadingAssets: boolean;
}

export function TicketRequesterAssetsSection({
  assets,
  loadingAssets,
}: TicketRequesterAssetsSectionProps) {
  return (
    <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
      <h3 className="mb-4 text-sm font-semibold text-slate-700">Equipamentos do Solicitante</h3>

      {loadingAssets ? (
        <p className="text-sm text-slate-400">Carregando equipamentos...</p>
      ) : assets.length === 0 ? (
        <p className="text-sm italic text-slate-400">Nenhum equipamento registrado.</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {assets.map((asset) => {
            const normalizedName = asset.name.toLowerCase();
            const isLaptop =
              normalizedName.includes('notebook') || normalizedName.includes('laptop');
            const AssetIcon = isLaptop ? Laptop : Monitor;

            return (
              <li key={asset.id} className="rounded-2xl border border-slate-200 bg-[#fff8f1] p-3">
                <div className="flex items-start gap-2.5">
                  <AssetIcon size={16} className="mt-0.5 shrink-0 text-slate-500" />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-slate-700">{asset.name}</p>
                    <p className="mt-0.5 text-xs text-slate-500">Patrimônio: {asset.patrimonyCode}</p>
                    <p className="mt-1 whitespace-pre-wrap break-words text-xs text-slate-500">
                      {asset.specifications || 'Sem especificações.'}
                    </p>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
