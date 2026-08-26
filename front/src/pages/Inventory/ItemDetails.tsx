// Página de detalhes de um item — exibe especificações, relacionamentos e histórico de movimentações
import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Package, FileText } from 'lucide-react';
import { toast } from 'react-toastify';
import { motion } from 'framer-motion';

import UploadInvoiceModal from '@/pages/Financeiro/components/UploadInvoiceModal';
import ItemBatchesTab from './components/ItemBatchesTab';
import ItemOutMovementsTab from './components/ItemOutMovementsTab';
import ItemTicketsTab from './components/ItemTicketsTab';
import ItemRelationshipsSection from './components/ItemRelationshipsSection';
import LinkComponentModal from './components/LinkComponentModal';
import AllocateConsumableModal from './components/AllocateConsumableModal';

import {
  getItemById,
  getItemBatches,
  getItemOutMovements,
  uploadBatchInvoice,
  downloadBatchInvoice,
  linkAssetComponent,
  allocateConsumable,
  getItemAllocations,
  getItems
} from '../../services/inventoryService';
import { getItemTickets } from '../../services/ticketService';
import type { Item, Batch, StockMovement, Ticket, ItemAllocation } from '../../types/models';

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
  const [item, setItem] = useState<Item | null>(null);
  const [batches, setBatches] = useState<Batch[]>([]);
  const [outMovements, setOutMovements] = useState<StockMovement[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'IN' | 'OUT' | 'TICKETS'>('IN');
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [selectedBatchForInvoice, setSelectedBatchForInvoice] = useState<Batch | null>(null);

  // Estados locais de chamados vinculados
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [ticketsPage, setTicketsPage] = useState(0);
  const [ticketsTotalPages, setTicketsTotalPages] = useState(1);
  const [ticketsLoading, setTicketsLoading] = useState(false);

  // Estados de Relacionamentos e Alocações
  const [allocations, setAllocations] = useState<ItemAllocation[]>([]);
  const [allocationsLoading, setAllocationsLoading] = useState(false);

  // Modais de Vínculo e Alocação
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [showAllocateModal, setShowAllocateModal] = useState(false);

  const [availableItems, setAvailableItems] = useState<Item[]>([]);
  const [availableItemsLoading, setAvailableItemsLoading] = useState(false);

  const [availableConsumables, setAvailableConsumables] = useState<Item[]>([]);
  const [availableConsumablesLoading, setAvailableConsumablesLoading] = useState(false);

  const [selectedChildId, setSelectedChildId] = useState('');
  const [linking, setLinking] = useState(false);

  const [selectedConsumableId, setSelectedConsumableId] = useState('');
  const [allocationQuantity, setAllocationQuantity] = useState(1);
  const [allocationTicketId, setAllocationTicketId] = useState('');
  const [allocating, setAllocating] = useState(false);

  const loadAllocations = useCallback(async () => {
    if (!id || !item) return;
    setAllocationsLoading(true);
    try {
      const data = await getItemAllocations(id, !item.isConsumable);
      setAllocations(data);
    } catch (error) {
      console.error('Erro ao carregar alocações:', error);
      toast.error('Erro ao carregar histórico de alocações.');
    } finally {
      setAllocationsLoading(false);
    }
  }, [id, item]);

  useEffect(() => {
    if (item) {
      loadAllocations();
    }
  }, [item, loadAllocations]);

  const loadAvailableItems = useCallback(async () => {
    setAvailableItemsLoading(true);
    try {
      const data = await getItems({ size: 100 });
      const filtered = data.content.filter(
        (i) => !i.isConsumable && !i.parent && i.id !== id
      );
      setAvailableItems(filtered);
    } catch (error) {
      console.error('Erro ao carregar itens:', error);
      toast.error('Erro ao buscar equipamentos livres.');
    } finally {
      setAvailableItemsLoading(false);
    }
  }, [id]);

  const loadAvailableConsumables = useCallback(async () => {
    setAvailableConsumablesLoading(true);
    try {
      const data = await getItems({ size: 100 });
      const filtered = data.content.filter((i) => i.isConsumable && i.currentStock > 0);
      setAvailableConsumables(filtered);
    } catch (error) {
      console.error('Erro ao carregar consumíveis:', error);
      toast.error('Erro ao buscar consumíveis em estoque.');
    } finally {
      setAvailableConsumablesLoading(false);
    }
  }, []);

  const loadData = useCallback(async () => {
    if (!id) return;
    try {
      const [itemData, batchesData, outMovementsData] = await Promise.all([
        getItemById(id),
        getItemBatches(id),
        getItemOutMovements(id),
      ]);
      setItem(itemData);
      setBatches(batchesData);
      setOutMovements(outMovementsData);
    } catch {
      toast.error('Item não encontrado.');
      navigate('/inventory');
    } finally {
      setLoading(false);
    }
  }, [id, navigate]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const loadTicketsData = useCallback(async () => {
    if (!id) return;
    setTicketsLoading(true);
    try {
      const data = await getItemTickets(id, ticketsPage);
      setTickets(data.content);
      setTicketsTotalPages(data.totalPages);
    } catch (error) {
      console.error('Erro ao buscar chamados associados ao item:', error);
      toast.error('Erro ao carregar chamados associados.');
      setTickets([]);
    } finally {
      setTicketsLoading(false);
    }
  }, [id, ticketsPage]);

  useEffect(() => {
    if (activeTab === 'TICKETS') {
      loadTicketsData();
    }
  }, [activeTab, loadTicketsData]);

  async function handleLinkComponent(e: React.FormEvent) {
    e.preventDefault();
    if (!id || !selectedChildId) return;

    setLinking(true);
    try {
      await linkAssetComponent(id, selectedChildId);
      toast.success('Equipamento acoplado com sucesso!');
      setShowLinkModal(false);
      setSelectedChildId('');
      loadData();
    } catch (error) {
      console.error('Erro ao acoplar equipamento:', error);
      toast.error('Erro ao acoplar equipamento.');
    } finally {
      setLinking(false);
    }
  }

  async function handleAllocateConsumable(e: React.FormEvent) {
    e.preventDefault();
    if (!id || !selectedConsumableId || allocationQuantity <= 0) return;

    setAllocating(true);
    try {
      await allocateConsumable(id, {
        childItemId: selectedConsumableId,
        quantity: allocationQuantity,
        ticketId: allocationTicketId || undefined,
      });
      toast.success('Insumo alocado com sucesso!');
      setShowAllocateModal(false);
      setSelectedConsumableId('');
      setAllocationQuantity(1);
      setAllocationTicketId('');
      loadData();
      loadAllocations();
    } catch (error) {
      console.error('Erro ao alocar insumo:', error);
      toast.error('Erro ao alocar insumo. Verifique se o saldo em estoque é suficiente.');
    } finally {
      setAllocating(false);
    }
  }

  function openInvoiceModal(batch: Batch) {
    setSelectedBatchForInvoice(batch);
    setShowInvoiceModal(true);
  }

  async function handleInvoiceUpload(file: File) {
    if (!id || !selectedBatchForInvoice) return;
    try {
      await uploadBatchInvoice(id, selectedBatchForInvoice.id, file);
      setShowInvoiceModal(false);
      setSelectedBatchForInvoice(null);
      loadData();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Erro desconhecido';
      throw new Error(message);
    }
  }

  async function handleInvoiceDownload(batch: Batch, e: React.MouseEvent) {
    if (!id) return;
    e.stopPropagation();

    if (!batch.invoiceFileName) {
      toast.error('Nenhuma nota fiscal anexada a este lote.');
      return;
    }

    try {
      const blob = await downloadBatchInvoice(id, batch.id);
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = batch.invoiceFileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch {
      toast.error('Erro ao baixar nota fiscal.');
    }
  }

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

  const hasStock = item.currentStock > 0;
  const specs = item.specifications || {};
  const hasSpecs = Object.keys(specs).length > 0;

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

      {/* Header do item */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
        <div className="flex flex-wrap items-start justify-between gap-3 mb-3">
          <div className="flex-1">
            <h2 className="text-xl font-bold text-slate-800 mb-1">{item.name}</h2>
            <p className="text-sm text-slate-500">{item.itemCategoryName}</p>
          </div>
          <span
            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
              hasStock ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
            }`}
          >
            {hasStock ? 'Em Estoque' : 'Sem Estoque'}
          </span>
        </div>
        <div className="mt-4 pt-4 border-t border-slate-100">
          <div className="flex items-center gap-2">
            <Package size={16} className="text-slate-400" />
            <span className="text-sm text-slate-600">
              Estoque Atual:{' '}
              <span className={`font-semibold ${hasStock ? 'text-green-600' : 'text-red-600'}`}>
                {item.currentStock} {item.currentStock === 1 ? 'unidade' : 'unidades'}
              </span>
            </span>
          </div>
        </div>
      </div>

      {/* Banner se possuir pai associado */}
      {!item.isConsumable && item.parent && (
        <div className="bg-blue-50 text-brand-primary border border-blue-100 rounded-xl p-4 mb-6 flex items-center justify-between shadow-sm">
          <div>
            <p className="text-sm font-semibold text-blue-900">Equipamento Vinculado</p>
            <p className="text-xs text-blue-800 mt-0.5 font-medium">
              Este dispositivo está atualmente acoplado ao ativo principal:{' '}
              <strong className="text-brand-primary font-bold">{item.parent.name}</strong>.
            </p>
          </div>
          <Link
            to={`/inventory/${item.parent.id}`}
            className="text-xs font-bold text-brand-primary hover:text-brand-primary-dark hover:underline bg-white border border-blue-200 px-3 py-1.5 rounded-lg shadow-sm transition-colors"
          >
            Ver Equipamento Pai
          </Link>
        </div>
      )}

      {/* Card de Especificações */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-2 mb-4">
          <FileText size={18} className="text-slate-600" />
          <h3 className="text-sm font-semibold text-slate-700">Especificações</h3>
        </div>
        {hasSpecs ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3">
            {Object.entries(specs).map(([key, value]) => (
              <div key={key} className="flex flex-col">
                <span className="text-xs font-medium text-slate-500 uppercase tracking-wide">
                  {key}
                </span>
                <span className="text-sm text-slate-800 mt-0.5">
                  {String(value)}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-slate-400 italic">Nenhuma especificação cadastrada.</p>
        )}
      </div>

      {/* Seção: Componentes e Vínculos */}
      <ItemRelationshipsSection
        item={item}
        allocations={allocations}
        allocationsLoading={allocationsLoading}
        onOpenLinkModal={() => {
          loadAvailableItems();
          setShowLinkModal(true);
        }}
        onOpenAllocateModal={() => {
          loadAvailableConsumables();
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
            { id: 'TICKETS', label: 'Chamados de Suporte' }
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
