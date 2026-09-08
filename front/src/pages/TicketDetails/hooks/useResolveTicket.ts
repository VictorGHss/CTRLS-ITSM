import { useEffect, useState, type FormEvent } from 'react';
import { toast } from 'react-toastify';

import type { ResolveTicketRequest, Ticket, User } from '@/types/models';
import { useDeliveryItemsState, type ResolveTicketItemState } from './useDeliveryItemsState';
import { useEquipmentDeliveryState } from './useEquipmentDeliveryState';
import { useResolveRecipients } from './useResolveRecipients';

export type { ResolveTicketItemState };

interface UseResolveTicketParams {
  isOpen: boolean;
  onClose: () => void;
  requesterId: string;
  onResolve: (request: ResolveTicketRequest) => Promise<void>;
  ticket?: Ticket;
  initialNotes?: string;
  users?: User[];
}

/**
 * Hook personalizado para gerir o estado da resolução do chamado.
 * Lida com a entrega de ativos de património ou insumos de consumo nominalmente.
 */
export function useResolveTicket({
  isOpen,
  onClose,
  requesterId,
  onResolve,
  ticket,
  initialNotes = '',
  users = [],
}: UseResolveTicketParams) {
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Estados para a funcionalidade de registrar manutenção de ativo no fechamento
  const [registerMaintenance, setRegisterMaintenance] = useState(false);
  const [maintAssetId, setMaintAssetId] = useState('');
  const [maintType, setMaintType] = useState<'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE' | 'TRANSFER'>('CORRECTIVE');
  const [maintDescription, setMaintDescription] = useState('');
  const [maintCost, setMaintCost] = useState('');

  // Sub-hooks especializados
  const {
    itemsToDeliver,
    hasAutoInventoryDeduction,
    handleRecipientChange,
    handleSplitItem,
    handleRemoveSplitItem,
    handleItemQuantityChange,
    resetDeliveryItems,
  } = useDeliveryItemsState(isOpen, ticket);

  const {
    recipientUserId,
    setRecipientUserId,
    assetUsers,
    loadingAssetUsers,
    ticketUsers,
    availableRecipients,
    resetRecipients,
  } = useResolveRecipients(isOpen, ticket, users);

  const {
    deliverEquipment,
    setDeliverEquipment,
    deliveryType,
    setDeliveryType,
    assetMode,
    setAssetMode,
    assets,
    items,
    assetCategories,
    allAssets,
    selectedAssetId,
    setSelectedAssetId,
    selectedItemId,
    setSelectedItemId,
    quantity,
    setQuantity,
    newAssetName,
    setNewAssetName,
    newAssetCategoryId,
    setNewAssetCategoryId,
    newAssetPatrimonyCode,
    setNewAssetPatrimonyCode,
    newAssetSpecifications,
    setNewAssetSpecifications,
    linkInsumosToAsset,
    setLinkInsumosToAsset,
    targetAssetId,
    setTargetAssetId,
    loadingAssets,
    loadingItems,
    loadingAssetCategories,
    loadingAllAssets,
    resetEquipmentDelivery,
  } = useEquipmentDeliveryState(isOpen);

  useEffect(() => {
    if (isOpen) {
      setResolutionNotes(initialNotes);
    }
  }, [isOpen, initialNotes]);

  useEffect(() => {
    if (isOpen && ticket?.assetId) {
      setMaintAssetId(ticket.assetId);
    }
  }, [isOpen, ticket]);

  function resetForm() {
    setResolutionNotes('');
    resetEquipmentDelivery();
    resetRecipients();
    resetDeliveryItems();
    setRegisterMaintenance(false);
    setMaintAssetId(ticket?.assetId || '');
    setMaintType('CORRECTIVE');
    setMaintDescription('');
    setMaintCost('');
  }

  function validateBeforeSubmit() {
    if (!resolutionNotes.trim()) {
      toast.error('Digite a nota de resolução.');
      return false;
    }

    if (linkInsumosToAsset && !targetAssetId) {
      toast.error('Selecione o equipamento de destino para vinculação dos insumos.');
      return false;
    }

    if (hasAutoInventoryDeduction) {
      const missingRecipient = itemsToDeliver.some((item) => !item.recipientUserId);
      if (missingRecipient) {
        toast.error('Selecione quem recebeu cada um dos insumos solicitados.');
        return false;
      }
    }

    if (!deliverEquipment) {
      return true;
    }

    if (deliveryType === 'asset') {
      if (assetMode === 'existing' && !selectedAssetId) {
        toast.error('Selecione um equipamento para entregar.');
        return false;
      }

      if (assetMode === 'new') {
        if (!newAssetName.trim()) {
          toast.error('Informe o nome do ativo.');
          return false;
        }
        if (!newAssetCategoryId) {
          toast.error('Selecione a categoria do ativo.');
          return false;
        }
        if (!newAssetPatrimonyCode.trim()) {
          toast.error('Informe o código do patrimônio.');
          return false;
        }
      }
    }

    if (deliveryType === 'item') {
      if (!selectedItemId || quantity < 1) {
        toast.error('Selecione um material e quantidade válida.');
        return false;
      }
      if (!recipientUserId) {
        toast.error('Selecione quem recebeu o material de consumo entregue.');
        return false;
      }
    }

    if (registerMaintenance && !maintAssetId) {
      toast.error('Selecione o equipamento para registrar a manutenção.');
      return false;
    }

    return true;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    if (!validateBeforeSubmit()) {
      return;
    }

    setIsSubmitting(true);
    try {
      const finalItemsToDeliver = [...itemsToDeliver];

      // Se o técnico adicionou manualmente a entrega de um insumo avulso
      if (deliverEquipment && deliveryType === 'item' && selectedItemId) {
        finalItemsToDeliver.push({
          id: `${selectedItemId}-${Date.now()}`,
          itemId: selectedItemId,
          itemName: items.find((i) => i.id === selectedItemId)?.name || 'Insumo',
          quantity,
          recipientUserId: recipientUserId || requesterId,
        });
      }

      const itemsPayload = finalItemsToDeliver.map((item) => ({
        itemId: item.itemId,
        quantity: item.quantity,
        recipientUserId: item.recipientUserId || undefined,
      }));

      const payload: ResolveTicketRequest = {
        resolutionNotes: resolutionNotes.trim(),
        itemsToDeliver: itemsPayload.length > 0 ? itemsPayload : undefined,
        targetAssetId: linkInsumosToAsset ? targetAssetId : undefined,
      };

      if (registerMaintenance && maintAssetId) {
        payload.maintenance = {
          assetId: maintAssetId,
          type: maintType,
          description: maintDescription.trim() || undefined,
          cost: maintCost ? parseFloat(maintCost) : undefined,
        };
      }

      if (deliverEquipment && deliveryType === 'asset' && assetMode === 'existing') {
        payload.assetIdToDeliver = selectedAssetId;
        payload.recipientUserId = recipientUserId || undefined;
      } else if (deliverEquipment && deliveryType === 'asset' && assetMode === 'new') {
        payload.newAssetToDeliver = {
          userId: requesterId,
          name: newAssetName.trim(),
          patrimonyCode: newAssetPatrimonyCode.trim(),
          categoryId: newAssetCategoryId,
          specifications: newAssetSpecifications.trim() || undefined,
        };
        payload.recipientUserId = recipientUserId || undefined;
      } else if (deliverEquipment && deliveryType === 'item') {
        payload.recipientUserId = recipientUserId || undefined;
      }

      await onResolve(payload);

      resetForm();
      onClose();
    } catch (error) {
      console.error('Erro ao resolver chamado:', error);
    } finally {
      setIsSubmitting(false);
    }
  }

  return {
    resolutionNotes,
    setResolutionNotes,
    deliverEquipment,
    setDeliverEquipment,
    deliveryType,
    setDeliveryType,
    assetMode,
    setAssetMode,
    assets,
    items,
    assetCategories,
    selectedAssetId,
    setSelectedAssetId,
    selectedItemId,
    setSelectedItemId,
    quantity,
    setQuantity,
    newAssetName,
    setNewAssetName,
    newAssetCategoryId,
    setNewAssetCategoryId,
    newAssetPatrimonyCode,
    setNewAssetPatrimonyCode,
    newAssetSpecifications,
    setNewAssetSpecifications,
    loadingAssets,
    loadingItems,
    loadingAssetCategories,
    isSubmitting,
    hasAutoInventoryDeduction,
    recipientUserId,
    setRecipientUserId,
    assetUsers,
    loadingAssetUsers,
    handleSubmit,
    itemsToDeliver,
    handleRecipientChange,
    handleSplitItem,
    handleRemoveSplitItem,
    handleItemQuantityChange,
    ticketUsers,
    availableRecipients,
    linkInsumosToAsset,
    setLinkInsumosToAsset,
    targetAssetId,
    setTargetAssetId,
    loadingAllAssets,
    registerMaintenance,
    setRegisterMaintenance,
    maintAssetId,
    setMaintAssetId,
    maintType,
    setMaintType,
    maintDescription,
    setMaintDescription,
    maintCost,
    setMaintCost,
    allAssets,
  };
}
