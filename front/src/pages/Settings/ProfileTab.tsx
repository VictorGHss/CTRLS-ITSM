import { Save, User as UserIcon } from 'lucide-react';
import type { User } from '@/types/models';

interface ProfileTabProps {
  user: User | null;
  onSavePreferences: () => void;
}

const inputClassName =
  'w-full rounded-2xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#feb56c]/60 focus:border-[#feb56c] transition-all';

export default function ProfileTab({ user, onSavePreferences }: ProfileTabProps) {
  return (
    <div className="space-y-3 max-w-2xl">
      <div className="bg-white rounded-2xl border border-[#feb56c]/35 px-6 py-6 shadow-sm">
        <div className="flex items-center gap-4">
          <div className="w-10 h-10 rounded-full bg-[#fff6ee] flex items-center justify-center">
            <UserIcon size={20} className="text-[#feb56c]" />
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-900">Perfil</p>
            <p className="text-xs text-slate-400 mt-0.5">Informações básicas da sua conta</p>
          </div>
        </div>

        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Nome</label>
            <input type="text" readOnly value={user?.name ?? ''} className={inputClassName} />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">E-mail</label>
            <input type="text" readOnly value={user?.email ?? ''} className={inputClassName} />
          </div>
        </div>

        <div className="flex justify-end mt-4">
          <button
            type="button"
            onClick={onSavePreferences}
            className="inline-flex items-center gap-2 rounded-2xl bg-[#feb56c] px-5 py-2.5 text-sm font-bold text-slate-900 transition-colors hover:bg-[#f6a455] shadow-sm"
          >
            <Save size={14} />
            Salvar Preferências
          </button>
        </div>
      </div>
    </div>
  );
}
