import type { Asset, Ticket, TicketCategory, User } from '../../types/models';
import { TicketInfoSection } from './components/TicketInfoSection';
import { TicketSimilarSuggestionsSection } from './components/TicketSimilarSuggestionsSection';
import { TicketAdditionalUsersSection } from './components/TicketAdditionalUsersSection';
import { TicketRequesterAssetsSection } from './components/TicketRequesterAssetsSection';
import { TicketLinkedTicketsSection } from './components/TicketLinkedTicketsSection';

interface TicketSidebarProps {
  ticket: Ticket;
  assets: Asset[];
  loadingAssets: boolean;
  userRole?: string;
  categories: TicketCategory[];
  loadingCategories: boolean;
  updatingCategory: boolean;
  users: User[];
  loadingUsers: boolean;
  addingAdditionalUser: boolean;
  onChangeCategory: (categoryId: string) => void;
  onAddAdditionalUser: (userId: string) => Promise<void>;
  onRefresh?: () => void;
  onApplyMacro?: (macroText: string) => void;
}

export default function TicketSidebar({
  ticket,
  assets,
  loadingAssets,
  userRole,
  categories,
  loadingCategories,
  updatingCategory,
  users,
  loadingUsers,
  addingAdditionalUser,
  onChangeCategory,
  onAddAdditionalUser,
  onRefresh,
  onApplyMacro,
}: TicketSidebarProps) {
  const canManageTicket = userRole === 'ADMIN' || userRole === 'TECHNICIAN';

  return (
    <aside className="flex flex-col gap-4">
      {/* 1. Informações básicas, solicitante, SLA e categoria */}
      <TicketInfoSection
        ticket={ticket}
        canManageTicket={canManageTicket}
        categories={categories}
        loadingCategories={loadingCategories}
        updatingCategory={updatingCategory}
        onChangeCategory={onChangeCategory}
      />

      {/* 2. Chamados similares e sugestão de Macro da Base de Conhecimento */}
      <TicketSimilarSuggestionsSection
        ticket={ticket}
        onApplyMacro={onApplyMacro}
      />

      {/* 3. Colaboradores adicionais afetados */}
      <TicketAdditionalUsersSection
        ticket={ticket}
        canManageTicket={canManageTicket}
        users={users}
        loadingUsers={loadingUsers}
        addingAdditionalUser={addingAdditionalUser}
        onAddAdditionalUser={onAddAdditionalUser}
      />

      {/* 4. Equipamentos/Patrimônios do solicitante */}
      <TicketRequesterAssetsSection
        assets={assets}
        loadingAssets={loadingAssets}
      />

      {/* 5. Associação e listagem de chamados vinculados */}
      <TicketLinkedTicketsSection
        ticket={ticket}
        onRefresh={onRefresh}
      />
    </aside>
  );
}
