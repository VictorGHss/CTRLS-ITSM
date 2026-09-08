import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import {
  getItemById,
  getItemBatches,
  getItemOutMovements,
  uploadBatchInvoice,
  downloadBatchInvoice,
  linkAssetComponent,
  allocateConsumable,
  getItemAllocations,
  getItems,
} from '@/services/inventoryService';
import { getItemTickets } from '@/services/ticketService';
import type { Item, Batch, StockMovement, Ticket, ItemAllocation } from '@/types/models';

export function useItemDetails(id?: string) {
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
      void loadAllocations();
    }
  }, [item, loadAllocations]);

  const loadAvailableItems = useCallback(async () => {
    setAvailableItemsLoading(true);
    try {
      const data = await getItems({ size: 100 });
      const filtered = data.content.filter(
        (i) => !i.isConsumable && !i.parent && i.id !== id,
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
    void loadData();
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
      void loadTicketsData();
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
      void loadData();
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
      void loadData();
      void loadAllocations();
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
      void loadData();
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

  return {
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
    loadAllocations,
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
  };
}
