import React from 'react';
import { X, KeyRound } from 'lucide-react';
import type { User } from '../../types/models';

interface ResetPasswordConfirmModalProps {
  targetUser: User | null;
  loading: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}

export const ResetPasswordConfirmModal: React.FC<ResetPasswordConfirmModalProps> = ({
  targetUser,
  loading,
  onClose,
  onConfirm
}) => {
  if (!targetUser) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-slate-800">Redefinir Senha</h2>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-200 transition-colors cursor-pointer"
            disabled={loading}
          >
            <X size={18} className="text-slate-500" />
          </button>
        </div>
        <p className="text-sm text-slate-600 mb-6">
          Tem certeza que deseja redefinir a senha de{' '}
          <strong className="text-slate-800">{targetUser.name}</strong> para{' '}
          <strong className="text-slate-800">"Mudar@123"</strong>?<br />
          <span className="text-slate-500 mt-1 inline-block">
            O usuário terá de criar uma nova senha no próximo login.
          </span>
        </p>
        <div className="flex justify-end gap-3">
          <button
            onClick={onClose}
            disabled={loading}
            className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-60 cursor-pointer"
          >
            Cancelar
          </button>
          <button
            onClick={() => void onConfirm()}
            disabled={loading}
            className="flex items-center gap-2 bg-amber-500 hover:bg-amber-600 disabled:opacity-60 disabled:cursor-not-allowed text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors cursor-pointer"
          >
            <KeyRound size={15} />
            {loading ? 'Redefinindo...' : 'Confirmar Redefinição'}
          </button>
        </div>
      </div>
    </div>
  );
};
