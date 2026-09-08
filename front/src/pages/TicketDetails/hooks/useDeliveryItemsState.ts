import { useEffect, useMemo, useState } from 'react';
import { toast } from 'react-toastify';
import type { Ticket } from '@/types/models';

export interface ResolveTicketItemState {
  id: string;
  itemId: string;
  itemName: string;
  quantity: number;
  recipientUserId: string;
}

export function useDeliveryItemsState(isOpen: boolean, ticket?: Ticket) {
  const [itemsToDeliver, setItemsToDeliver] = useState<ResolveTicketItemState[]>([]);

  useEffect(() => {
    if (isOpen && ticket) {
      const initialItems: ResolveTicketItemState[] = [];
      if (ticket.requestedItems && ticket.requestedItems.length > 0) {
        ticket.requestedItems.forEach((ri, idx) => {
          initialItems.push({
            id: `${ri.itemId}-${idx}-${Date.now()}`,
            itemId: ri.itemId,
            itemName: ri.itemName || 'Material de Consumo',
            quantity: ri.quantity,
            recipientUserId: ticket.requesterId,
          });
        });
      } else if (ticket.requestedItemId && ticket.requestedQuantity) {
        initialItems.push({
          id: `${ticket.requestedItemId}-0-${Date.now()}`,
          itemId: ticket.requestedItemId,
          itemName: ticket.requestedItemName || 'Material de Consumo',
          quantity: ticket.requestedQuantity,
          recipientUserId: ticket.requesterId,
        });
      }
      queueMicrotask(() => {
        setItemsToDeliver(initialItems);
      });
    }
  }, [isOpen, ticket]);

  const hasAutoInventoryDeduction = useMemo(
    () => itemsToDeliver.length > 0,
    [itemsToDeliver],
  );

  function handleRecipientChange(rowId: string, recipientId: string) {
    setItemsToDeliver((prev) =>
      prev.map((item) =>
        item.id === rowId ? { ...item, recipientUserId: recipientId } : item,
      ),
    );
  }

  function handleSplitItem(rowId: string) {
    setItemsToDeliver((prev) => {
      const itemIndex = prev.findIndex((item) => item.id === rowId);
      if (itemIndex === -1) return prev;

      const targetItem = prev[itemIndex];
      if (targetItem.quantity <= 1) {
        toast.warning('A quantidade mínima para divisão é 2 unidades.');
        return prev;
      }

      const updatedTarget = {
        ...targetItem,
        quantity: targetItem.quantity - 1,
      };

      const newSplitRow: ResolveTicketItemState = {
        id: `${targetItem.itemId}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        itemId: targetItem.itemId,
        itemName: targetItem.itemName,
        quantity: 1,
        recipientUserId: '',
      };

      const newItems = [...prev];
      newItems.splice(itemIndex, 1, updatedTarget);
      newItems.splice(itemIndex + 1, 0, newSplitRow);
      return newItems;
    });
  }

  function handleRemoveSplitItem(rowId: string) {
    setItemsToDeliver((prev) => {
      const targetItem = prev.find((item) => item.id === rowId);
      if (!targetItem) return prev;

      const otherRowIndex = prev.findIndex(
        (item) => item.itemId === targetItem.itemId && item.id !== rowId,
      );
      if (otherRowIndex === -1) {
        return prev;
      }

      const updatedOther = {
        ...prev[otherRowIndex],
        quantity: prev[otherRowIndex].quantity + targetItem.quantity,
      };

      const remaining = prev.filter((item) => item.id !== rowId);
      const newOtherIdx = remaining.findIndex((item) => item.id === prev[otherRowIndex].id);
      if (newOtherIdx !== -1) {
        remaining[newOtherIdx] = updatedOther;
      }
      return remaining;
    });
  }

  function handleItemQuantityChange(rowId: string, newQty: number) {
    if (newQty < 1) return;
    setItemsToDeliver((prev) =>
      prev.map((item) => (item.id === rowId ? { ...item, quantity: newQty } : item)),
    );
  }

  function resetDeliveryItems() {
    setItemsToDeliver([]);
  }

  return {
    itemsToDeliver,
    setItemsToDeliver,
    hasAutoInventoryDeduction,
    handleRecipientChange,
    handleSplitItem,
    handleRemoveSplitItem,
    handleItemQuantityChange,
    resetDeliveryItems,
  };
}
