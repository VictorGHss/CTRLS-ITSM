// Página de listagem e cadastro de usuários
import { useEffect, useState, useCallback } from 'react';
import { PlusCircle, X, Upload, Search, ArrowDownWideNarrow } from 'lucide-react';
import { toast } from 'react-toastify';
import { getAllUsers, createUser, getSectors, resetUserPassword, adminReset2FA } from '../../services/userService';
import type { User, Sector, CreateUserDto } from '../../types/models';
import { useAuth } from '../../contexts/AuthContext';
import BulkImportModal from './BulkImportModal';
import EditUserModal from './EditUserModal';
import NewUserModal from './NewUserModal';
import { UserTable } from './UserTable';
import { UserDetailsModal } from './UserDetailsModal';
import { ResetPasswordConfirmModal } from './ResetPasswordConfirmModal';
import { Reset2FAConfirmModal } from './Reset2FAConfirmModal';
import PageHero from '@/components/ui/PageHero';
import { useDebounce } from '@/hooks/useDebounce';

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrador',
  TECHNICIAN: 'Técnico',
  USER: 'Usuário',
};

export default function Users() {
  const { user: authenticatedUser, invalidateTwoFactorVerification } = useAuth();

  const [users, setUsers] = useState<User[]>([]);
  const [sectors, setSectors] = useState<Sector[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [checkingContaAzul, setCheckingContaAzul] = useState(false);

  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [editingUser, setEditingUser] = useState<User | null>(null);

  const [resetTargetUser, setResetTargetUser] = useState<User | null>(null);
  const [resettingPassword, setResettingPassword] = useState(false);

  const [reset2FATargetUser, setReset2FATargetUser] = useState<User | null>(null);
  const [resetting2FA, setResetting2FA] = useState(false);

  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearch = useDebounce(searchQuery, 400);
  const [sortOption, setSortOption] = useState<'name-asc' | 'name-desc' | 'sector-asc'>('name-asc');
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [formData, setFormData] = useState<CreateUserDto>({
    name: '',
    email: '',
    password: '',
    role: 'USER',
    sectorId: '',
    contaAzulId: '',
    receives_it_notifications: true,
  });

  async function loadSectors() {
    try {
      const data = await getSectors(true);
      setSectors(data);
    } catch {
      toast.error('Erro ao carregar setores.');
    }
  }

  useEffect(() => {
    void loadSectors();
  }, []);

  useEffect(() => {
    setCurrentPage(0);
  }, [debouncedSearch]);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getAllUsers({
        page: currentPage,
        size: 15,
        search: debouncedSearch,
        sort: sortOption,
      });
      setUsers(response.content);
      setTotalPages(response.totalPages);
    } catch {
      toast.error('Erro ao carregar usuários.');
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [currentPage, debouncedSearch, sortOption]);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  function resetForm() {
    setFormData({
      name: '',
      email: '',
      password: '',
      role: 'USER',
      sectorId: '',
      contaAzulId: '',
      receives_it_notifications: true,
    });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!formData.name.trim() || !formData.email.trim() || !formData.password.trim()) {
      toast.error('Preencha todos os campos obrigatórios.');
      return;
    }

    if (!formData.sectorId) {
      toast.error('Selecione o setor do usuário.');
      return;
    }

    setSubmitting(true);
    try {
      await createUser({
        ...formData,
        contaAzulId: formData.contaAzulId?.trim() || undefined,
      });
      toast.success('Usuário cadastrado com sucesso!');
      setShowModal(false);
      resetForm();
      await loadUsers();
    } catch {
      toast.error('Erro ao cadastrar usuário. Verifique os dados.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleConfirmResetPassword() {
    if (!resetTargetUser) return;

    setResettingPassword(true);
    try {
      await resetUserPassword(resetTargetUser.id);
      toast.success(`Senha de ${resetTargetUser.name} redefinida para "Mudar@123".`);
      setResetTargetUser(null);
    } catch {
      toast.error('Erro ao redefinir senha. Tente novamente.');
    } finally {
      setResettingPassword(false);
    }
  }

  async function handleConfirmReset2FA() {
    if (!reset2FATargetUser) return;

    setResetting2FA(true);
    try {
      await adminReset2FA(reset2FATargetUser.id);
      toast.success(`2FA de ${reset2FATargetUser.name} redefinido com sucesso.`);

      if (authenticatedUser?.id === reset2FATargetUser.id) {
        invalidateTwoFactorVerification();
        toast.info('Seu 2FA atual foi invalidado. Configure novamente para acessar o cofre.');
      }

      setReset2FATargetUser(null);
    } catch {
      toast.error('Erro ao redefinir o 2FA. Tente novamente.');
    } finally {
      setResetting2FA(false);
    }
  }

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <title>Equipe e Usuários — Inovare TI</title>
      <meta name="description" content="Gestão de usuários, permissões e acessos ao sistema Inovare TI" />
      <PageHero
        eyebrow="Administração"
        title="Equipe"
        description="Gerencie usuários, permissões e preferências de notificação do time de forma centralizada."
        actions={(
          <>
            <button
              onClick={() => setShowImportModal(true)}
              className="flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark cursor-pointer"
            >
              <Upload size={17} />
              Importar Planilha
            </button>
            <button
              onClick={() => setShowModal(true)}
              className="flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark cursor-pointer"
            >
              <PlusCircle size={17} />
              Novo Usuário
            </button>
          </>
        )}
      />

      {/* Barra de Pesquisa e Filtro */}
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
        <div className="relative flex-1 max-w-md">
          <input
            type="text"
            placeholder="Pesquisar por nome, e-mail ou setor..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setCurrentPage(0);
            }}
            className="w-full rounded-xl border border-slate-200 bg-white pl-10 pr-10 py-2.5 text-sm text-slate-800 placeholder-slate-400 focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition"
          />
          <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
            <Search size={16} />
          </div>
          {searchQuery && (
            <button
              onClick={() => {
                setSearchQuery('');
                setCurrentPage(0);
              }}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
            >
              <X size={16} />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <span className="text-xs font-bold uppercase tracking-widest text-slate-400 flex items-center gap-1">
            <ArrowDownWideNarrow size={14} /> Ordenar:
          </span>
          <select
            value={sortOption}
            onChange={(e) => {
              setSortOption(e.target.value as 'name-asc' | 'name-desc' | 'sector-asc');
              setCurrentPage(0);
            }}
            className="cursor-pointer rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-700 shadow-sm focus:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-primary/20 transition-all"
          >
            <option value="name-asc">Nome (A-Z)</option>
            <option value="name-desc">Nome (Z-A)</option>
            <option value="sector-asc">Setor (A-Z)</option>
          </select>
        </div>
      </div>

      {/* Tabela de Usuários com Paginação */}
      <UserTable
        users={users}
        loading={loading}
        searchQuery={searchQuery}
        roleLabels={ROLE_LABELS}
        onSelectUser={setSelectedUser}
        currentPage={currentPage}
        totalPages={totalPages}
        onPageChange={setCurrentPage}
      />

      <NewUserModal
        isOpen={showModal}
        submitting={submitting}
        formData={formData}
        sectors={sectors}
        checkingContaAzul={checkingContaAzul}
        onClose={() => {
          setShowModal(false);
          resetForm();
        }}
        onSubmit={handleSubmit}
        onChange={setFormData}
        onCheckContaAzul={setCheckingContaAzul}
      />

      <UserDetailsModal
        user={selectedUser}
        roleLabels={ROLE_LABELS}
        onClose={() => setSelectedUser(null)}
        onEdit={(user) => {
          setSelectedUser(null);
          setEditingUser(user);
        }}
        onResetPassword={(user) => {
          setSelectedUser(null);
          setResetTargetUser(user);
        }}
        onReset2FA={(user) => {
          setSelectedUser(null);
          setReset2FATargetUser(user);
        }}
      />

      <BulkImportModal
        isOpen={showImportModal}
        onClose={() => setShowImportModal(false)}
        onSuccess={loadUsers}
      />

      <EditUserModal
        user={editingUser}
        sectors={sectors}
        onClose={() => setEditingUser(null)}
        onSuccess={loadUsers}
      />

      <ResetPasswordConfirmModal
        targetUser={resetTargetUser}
        loading={resettingPassword}
        onClose={() => setResetTargetUser(null)}
        onConfirm={handleConfirmResetPassword}
      />

      <Reset2FAConfirmModal
        targetUser={reset2FATargetUser}
        loading={resetting2FA}
        onClose={() => setReset2FATargetUser(null)}
        onConfirm={handleConfirmReset2FA}
      />
    </main>
  );
}
