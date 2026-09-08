import React from 'react';
import { Bell, BellOff } from 'lucide-react';
import type { User } from '../../types/models';

interface UserTableProps {
  users: User[];
  loading: boolean;
  searchQuery: string;
  roleLabels: Record<string, string>;
  onSelectUser: (user: User) => void;
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export const UserTable: React.FC<UserTableProps> = ({
  users,
  loading,
  searchQuery,
  roleLabels,
  onSelectUser,
  currentPage,
  totalPages,
  onPageChange
}) => {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white shadow-sm p-6">
      {loading ? (
        <div className="p-12 text-center">
          <div className="animate-pulse space-y-3">
            <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
            <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
          </div>
        </div>
      ) : users.length === 0 ? (
        <p className="text-center text-slate-400 py-12 text-sm">
          {searchQuery ? 'Nenhum usuário correspondente à pesquisa.' : 'Nenhum usuário cadastrado.'}
        </p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full table-auto text-sm">
              <thead>
                <tr>
                  <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                    Nome
                  </th>
                  <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                    E-mail
                  </th>
                  <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                    Setor
                  </th>
                  <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                    Nível de Acesso
                  </th>
                  <th className="px-4 py-3 text-center text-[10px] font-bold uppercase tracking-widest text-slate-400">
                    Alertas Discord
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map(currentUser => (
                  <tr
                    key={currentUser.id}
                    onClick={() => onSelectUser(currentUser)}
                    className="cursor-pointer transition-colors hover:bg-orange-50/40"
                  >
                    <td className="px-4 py-3 font-medium text-slate-800">{currentUser.name}</td>
                    <td className="px-4 py-3 text-slate-600">{currentUser.email}</td>
                    <td className="px-4 py-3 text-slate-600">{currentUser.sectorName}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          currentUser.role === 'ADMIN'
                            ? 'bg-red-100 text-red-700'
                            : currentUser.role === 'TECHNICIAN'
                              ? 'bg-brand-secondary/40 text-brand-primary-dark'
                              : 'bg-slate-100 text-slate-700'
                        }`}
                      >
                        {roleLabels[currentUser.role]}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span
                        className="inline-flex"
                        title={
                          currentUser.receives_it_notifications
                            ? 'Recebe alertas de chamados e SLA no Discord'
                            : 'Não recebe alertas de chamados e SLA no Discord'
                        }
                      >
                        {currentUser.receives_it_notifications ? (
                          <Bell size={16} className="text-brand-primary" />
                        ) : (
                          <BellOff size={16} className="text-slate-400" />
                        )}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Paginação */}
          {totalPages > 1 && (
            <div className="mt-6 flex items-center justify-between border-t border-slate-100 pt-6">
              <p className="text-xs text-slate-500 font-medium">
                A mostrar página <span className="font-semibold text-slate-800">{currentPage + 1}</span> de{' '}
                <span className="font-semibold text-slate-800">{totalPages}</span>
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => onPageChange(Math.max(0, currentPage - 1))}
                  disabled={currentPage === 0}
                  className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                >
                  Anterior
                </button>
                <button
                  onClick={() => onPageChange(Math.min(totalPages - 1, currentPage + 1))}
                  disabled={currentPage >= totalPages - 1}
                  className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                >
                  Seguinte
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};
