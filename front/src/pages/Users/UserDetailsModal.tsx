import React from 'react';
import { X, Pencil, KeyRound, ShieldOff } from 'lucide-react';
import type { User } from '../../types/models';

interface UserDetailsModalProps {
  user: User | null;
  roleLabels: Record<string, string>;
  onClose: () => void;
  onEdit: (user: User) => void;
  onResetPassword: (user: User) => void;
  onReset2FA: (user: User) => void;
}

export const UserDetailsModal: React.FC<UserDetailsModalProps> = ({
  user,
  roleLabels,
  onClose,
  onEdit,
  onResetPassword,
  onReset2FA
}) => {
  if (!user) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-lg font-bold text-slate-800">Gestão de Usuário</h2>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-200 transition-colors cursor-pointer"
          >
            <X size={18} className="text-slate-500" />
          </button>
        </div>

        <div className="space-y-2 text-sm mb-6">
          <p>
            <span className="font-semibold text-slate-700">Nome:</span>{' '}
            <span className="text-slate-600">{user.name}</span>
          </p>
          <p>
            <span className="font-semibold text-slate-700">E-mail:</span>{' '}
            <span className="text-slate-600">{user.email}</span>
          </p>
          <p>
            <span className="font-semibold text-slate-700">Setor:</span>{' '}
            <span className="text-slate-600">{user.sectorName}</span>
          </p>
          <p>
            <span className="font-semibold text-slate-700">Perfil:</span>{' '}
            <span className="text-slate-600">{roleLabels[user.role]}</span>
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
          <button
            onClick={() => onEdit(user)}
            className="inline-flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg bg-blue-50 text-blue-700 hover:bg-blue-100 text-sm font-semibold transition-colors cursor-pointer"
          >
            <Pencil size={15} />
            Editar Dados
          </button>

          <button
            onClick={() => onResetPassword(user)}
            className="inline-flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg bg-amber-50 text-amber-700 hover:bg-amber-100 text-sm font-semibold transition-colors cursor-pointer"
          >
            <KeyRound size={15} />
            Redefinir Senha
          </button>

          <button
            onClick={() => onReset2FA(user)}
            className="inline-flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg bg-red-50 text-red-700 hover:bg-red-100 text-sm font-semibold transition-colors cursor-pointer"
          >
            <ShieldOff size={15} />
            Resetar 2FA
          </button>
        </div>
      </div>
    </div>
  );
};
