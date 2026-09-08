// Página de detalhes de um item — exibe especificações, relacionamentos e histórico de movimentações
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { motion } from 'framer-motion';

import UploadInvoiceModal from '@/pages/Financeiro/components/UploadInvoiceModal';
import ItemBatchesTab from './components/ItemBatchesTab';
import ItemOutMovementsTab from './components/ItemOutMovementsTab';
import ItemTicketsTab from './components/ItemTicketsTab';
import ItemRelationshipsSection from './components/ItemRelationshipsSection';
import LinkComponentModal from './components/LinkComponentModal';
import AllocateConsumableModal from './components/AllocateConsumableModal';
import ItemHeaderCard from './components/ItemHeaderCard';
import ItemSpecificationsCard from './components/ItemSpecificationsCard';
import { useItemDetails } from './hooks/useItemDetails';

function formatDate(iso: string): string {
  try {
    const date = new Date(iso);
    return date.toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return '-';
  }
}

function formatPrice(price: number): string {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(price);
}

export default function ItemDetails() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const {
    item,
    batches,
    outMovements,
    loading,
    activeTab,
    setActiveTab,
    tickets,
    ticketsPage,
    setTicketsPage,
    ticketsTotalPages,
    ticketsLoading,
    allocations,
    allocationsLoading,
    showLinkModal,
    setShowLinkModal,
    showAllocateModal,
    setShowAllocateModal,
    availableItems,
    availableItemsLoading,
    loadAvailableItems,
    availableConsumables,
    availableConsumablesLoading,
    loadAvailableConsumables,
    selectedChildId,
    setSelectedChildId,
    linking,
    handleLinkComponent,
    selectedConsumableId,
    setSelectedConsumableId,
    allocationQuantity,
    setAllocationQuantity,
    allocationTicketId,
    setAllocationTicketId,
    allocating,
    handleAllocateConsumable,
    showInvoiceModal,
    setShowInvoiceModal,
    selectedBatchForInvoice,
    setSelectedBatchForInvoice,
    openInvoiceModal,
    handleInvoiceUpload,
    handleInvoiceDownload,
  } = useItemDetails(id);

  if (loading) {
    return (
      <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
        <div className="animate-pulse space-y-4">
          <div className="h-6 bg-slate-200 rounded w-1/3" />
          <div className="h-40 bg-slate-100 rounded-xl" />
          <div className="h-32 bg-slate-100 rounded-xl" />
        </div>
      </main>
    );
  }

  if (!item) return null;

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      {/* Navegação de retorno */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate('/inventory')}
          className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500 hover:text-slate-700 transition-colors"
          aria-label="Voltar"
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <p className="text-xs text-slate-400">Detalhes do Item</p>
          <h1 className="text-base font-bold text-slate-800 leading-tight">
            {item.name}
          </h1>
        </div>
      </div>

      {/* Header do item e Banner de equipamento pai */}
      <ItemHeaderCard item={item} />

      {/* Card de Especificações */}
      <ItemSpecificationsCard specifications={item.specifications} />

      {/* Seção: Componentes e Vínculos */}
      <ItemRelationshipsSection
        item={item}
        allocations={allocations}
        allocationsLoading={allocationsLoading}
        onOpenLinkModal={() => {
          void loadAvailableItems();
          setShowLinkModal(true);
        }}
        onOpenAllocateModal={() => {
          void loadAvailableConsumables();
          setShowAllocateModal(true);
        }}
        formatDate={formatDate}
      />

      {/* Card de Movimentações e Abas */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
        <div className="flex items-center gap-1.5 mb-6 relative border-b border-slate-100 pb-2">
          {[
            { id: 'IN', label: 'Lotes de Entrada' },
            { id: 'OUT', label: 'Histórico de Saídas' },
            { id: 'TICKETS', label: 'Chamados de Suporte' },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id as 'IN' | 'OUT' | 'TICKETS')}
              className={`relative px-4 py-2 text-xs font-bold transition-all z-10 ${
                activeTab === tab.id ? 'text-brand-primary' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {activeTab === tab.id && (
                <motion.div
                  layoutId="activeTabUnderline"
                  className="absolute bottom-0 left-0 right-0 h-0.5 bg-brand-primary"
                  transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                />
              )}
              {tab.label}
            </button>
          ))}
        </div>

        {activeTab === 'IN' && (
          <ItemBatchesTab
            batches={batches}
            formatDate={formatDate}
            formatPrice={formatPrice}
            onDownloadInvoice={handleInvoiceDownload}
            onOpenInvoiceModal={openInvoiceModal}
          />
        )}

        {activeTab === 'OUT' && (
          <ItemOutMovementsTab
            outMovements={outMovements}
            formatDate={formatDate}
          />
        )}

        {activeTab === 'TICKETS' && (
          <ItemTicketsTab
            tickets={tickets}
            ticketsLoading={ticketsLoading}
            ticketsPage={ticketsPage}
            ticketsTotalPages={ticketsTotalPages}
            onPageChange={setTicketsPage}
            formatDate={formatDate}
          />
        )}
      </div>

      {/* Modal de Nota Fiscal */}
      <UploadInvoiceModal
        isOpen={showInvoiceModal}
        onClose={() => {
          setShowInvoiceModal(false);
          setSelectedBatchForInvoice(null);
        }}
        onUpload={handleInvoiceUpload}
        entityName="Lote"
        entityId={selectedBatchForInvoice?.id ?? ''}
      />

      {/* Modal de Vincular Componente */}
      <LinkComponentModal
        isOpen={showLinkModal}
        onClose={() => {
          setShowLinkModal(false);
          setSelectedChildId('');
        }}
        itemName={item.name}
        availableItems={availableItems}
        availableItemsLoading={availableItemsLoading}
        selectedChildId={selectedChildId}
        onSelectChildId={setSelectedChildId}
        onSubmit={handleLinkComponent}
        linking={linking}
      />

      {/* Modal de Alocar Consumível */}
      <AllocateConsumableModal
        isOpen={showAllocateModal}
        onClose={() => {
          setShowAllocateModal(false);
          setSelectedConsumableId('');
          setAllocationQuantity(1);
          setAllocationTicketId('');
        }}
        itemName={item.name}
        availableConsumables={availableConsumables}
        availableConsumablesLoading={availableConsumablesLoading}
        selectedConsumableId={selectedConsumableId}
        onSelectConsumableId={setSelectedConsumableId}
        allocationQuantity={allocationQuantity}
        onQuantityChange={setAllocationQuantity}
        allocationTicketId={allocationTicketId}
        onTicketIdChange={setAllocationTicketId}
        onSubmit={handleAllocateConsumable}
        allocating={allocating}
      />
    </main>
  );
}
