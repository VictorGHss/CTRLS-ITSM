import { ArrowLeft } from 'lucide-react';

interface SettingsSectionHeaderProps {
  title: string;
  description: string;
  onBack: () => void;
}

export default function SettingsSectionHeader({
  title,
  description,
  onBack,
}: SettingsSectionHeaderProps) {
  return (
    <div className="mb-6 flex items-center gap-3">
      <button
        type="button"
        onClick={onBack}
        className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-600 shadow-sm transition-all hover:bg-slate-50 hover:text-slate-800 hover:scale-105 active:scale-95"
      >
        <ArrowLeft size={18} />
      </button>
      <div>
        <h2 className="text-base font-bold text-slate-900">{title}</h2>
        <p className="text-xs text-slate-500">{description}</p>
      </div>
    </div>
  );
}
