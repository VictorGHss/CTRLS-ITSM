import { useState, useMemo, useEffect } from 'react';
import { Users, Plus, X } from 'lucide-react';
import SearchableDropdown from '@/components/common/SearchableDropdown';
import type { Ticket, User } from '../../../types/models';

interface TicketAdditionalUsersSectionProps {
  ticket: Ticket;
  canManageTicket: boolean;
  users: User[];
  loadingUsers: boolean;
  addingAdditionalUser: boolean;
  onAddAdditionalUser: (userId: string) => Promise<void>;
}

export function TicketAdditionalUsersSection({
  ticket,
  canManageTicket,
  users,
  loadingUsers,
  addingAdditionalUser,
  onAddAdditionalUser,
}: TicketAdditionalUsersSectionProps) {
  const [showAddAdditionalUser, setShowAddAdditionalUser] = useState(false);
  const [selectedAdditionalUserId, setSelectedAdditionalUserId] = useState('');
  const [additionalUserQuery, setAdditionalUserQuery] = useState('');
  const [sectorFilter, setSectorFilter] = useState('ALL');

  const additionalUserIds = ticket.additionalUserIds ?? [];
  const additionalUserIdsSerialized = additionalUserIds.join(',');

  const availableUsers = useMemo(() => {
    const ids = additionalUserIdsSerialized.split(',').filter(Boolean);
    return users
      .filter((user) => user.id !== ticket.requesterId)
      .filter((user) => !ids.includes(user.id))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [users, ticket.requesterId, additionalUserIdsSerialized]);

  const normalizedAdditionalQuery = additionalUserQuery.trim().toLowerCase();

  const sectorOptions = Array.from(
    new Set(users.map((user) => user.sectorName || 'Sem setor')),
  ).sort((a, b) => a.localeCompare(b));

  const filteredAvailableUsers = useMemo(() => {
    return availableUsers.filter((user) => {
      const userSector = user.sectorName || 'Sem setor';
      if (sectorFilter !== 'ALL' && userSector !== sectorFilter) return false;

      if (!normalizedAdditionalQuery) return true;
      const matchesName = user.name.toLowerCase().includes(normalizedAdditionalQuery);
      const matchesEmail = user.email.toLowerCase().includes(normalizedAdditionalQuery);
      return matchesName || matchesEmail;
    });
  }, [availableUsers, sectorFilter, normalizedAdditionalQuery]);

  useEffect(() => {
    if (!selectedAdditionalUserId) return;
    const stillAvailable = filteredAvailableUsers.some(
      (user) => user.id === selectedAdditionalUserId,
    );
    if (!stillAvailable) {
      setSelectedAdditionalUserId('');
    }
  }, [filteredAvailableUsers, selectedAdditionalUserId]);

  const handleConfirmAdditionalUser = async () => {
    if (!selectedAdditionalUserId) return;
    await onAddAdditionalUser(selectedAdditionalUserId);
    setSelectedAdditionalUserId('');
    setShowAddAdditionalUser(false);
  };

  return (
    <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-700 flex items-center gap-2">
          <Users size={16} className="text-brand-primary" />
          Colaboradores Afetados
        </h3>
        {canManageTicket && (
          <button
            type="button"
            onClick={() => setShowAddAdditionalUser((prev) => !prev)}
            disabled={addingAdditionalUser || loadingUsers || availableUsers.length === 0}
            className="inline-flex items-center gap-1 rounded-xl border border-brand-primary/20 bg-brand-secondary/20 px-2.5 py-1.5 text-xs font-semibold text-brand-primary hover:bg-brand-secondary/40 transition-colors disabled:opacity-60"
          >
            {showAddAdditionalUser ? <X size={14} /> : <Plus size={14} />}
            {showAddAdditionalUser ? 'Fechar' : 'Adicionar'}
          </button>
        )}
      </div>

      {loadingUsers ? (
        <p className="text-sm text-slate-400">Carregando colaboradores...</p>
      ) : additionalUserIds.length === 0 ? (
        <p className="text-sm italic text-slate-400">Nenhum colaborador adicional vinculado.</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {additionalUserIds.map((userId) => {
            const user = users.find((u) => u.id === userId);
            const fallbackName = `Usuario ${userId.slice(0, 8).toUpperCase()}`;
            return (
              <span
                key={userId}
                className="inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700"
              >
                {user?.name ?? fallbackName}
              </span>
            );
          })}
        </div>
      )}

      {showAddAdditionalUser && canManageTicket && (
        <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-3">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-2">
                Buscar por nome
              </label>
              <input
                type="text"
                value={additionalUserQuery}
                onChange={(event) => setAdditionalUserQuery(event.target.value)}
                placeholder="Digite o nome ou e-mail"
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-2">
                Filtrar setor
              </label>
              <select
                value={sectorFilter}
                onChange={(event) => setSectorFilter(event.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
                disabled={loadingUsers || sectorOptions.length === 0}
              >
                <option value="ALL">Todos os setores</option>
                {sectorOptions.map((sector) => (
                  <option key={sector} value={sector}>
                    {sector}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <label className="mt-3 block text-xs font-semibold text-slate-500">
            Selecionar colaborador
          </label>
          <SearchableDropdown
            options={[
              { id: '', name: 'Selecione um usuário' },
              ...filteredAvailableUsers.map((user) => ({
                id: user.id,
                name: `${user.name} — ${user.sectorName || 'Sem setor'}`,
              })),
            ]}
            value={selectedAdditionalUserId}
            onChange={(val) => setSelectedAdditionalUserId(val)}
            disabled={loadingUsers || filteredAvailableUsers.length === 0}
            placeholder="Selecione um usuário..."
          />
          <div className="mt-3 flex flex-wrap items-center justify-end gap-2">
            <button
              type="button"
              onClick={() => {
                setShowAddAdditionalUser(false);
                setSelectedAdditionalUserId('');
                setAdditionalUserQuery('');
                setSectorFilter('ALL');
              }}
              disabled={addingAdditionalUser}
              className="rounded-xl bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 border border-slate-200 hover:bg-slate-100 transition-colors disabled:opacity-60"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={() => void handleConfirmAdditionalUser()}
              disabled={!selectedAdditionalUserId || addingAdditionalUser}
              className="rounded-xl bg-brand-primary px-3 py-1.5 text-xs font-semibold text-white hover:bg-brand-primary-dark transition-colors disabled:opacity-60"
            >
              {addingAdditionalUser ? 'Adicionando...' : 'Confirmar'}
            </button>
          </div>
          {availableUsers.length === 0 && (
            <p className="mt-2 text-xs text-slate-400">Nenhum usuario disponivel para adicionar.</p>
          )}
          {availableUsers.length > 0 && filteredAvailableUsers.length === 0 && (
            <p className="mt-2 text-xs text-slate-400">Nenhum colaborador corresponde aos filtros.</p>
          )}
        </div>
      )}
    </section>
  );
}
