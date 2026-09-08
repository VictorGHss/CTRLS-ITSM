import { useEffect, useMemo, useState } from 'react';
import { getAssetById } from '@/services/inventoryService';
import type { Ticket, User } from '@/types/models';

export function useResolveRecipients(
  isOpen: boolean,
  ticket?: Ticket,
  users: User[] = [],
) {
  const [recipientUserId, setRecipientUserId] = useState('');
  const [assetUsers, setAssetUsers] = useState<{ id: string; name: string }[]>([]);
  const [loadingAssetUsers, setLoadingAssetUsers] = useState(false);

  // Filtra os usuários originalmente vinculados ao chamado
  const ticketUsers = useMemo(() => {
    if (!ticket) return [];
    const ids = new Set(
      [ticket.requesterId, ...(ticket.additionalUserIds || [])].filter(Boolean) as string[],
    );
    const filtered = users.filter((u) => ids.has(u.id));

    if (!filtered.some((u) => u.id === ticket.requesterId)) {
      filtered.push({
        id: ticket.requesterId,
        name: ticket.requesterName || 'Requerente',
        email: '',
        role: 'USER',
        sectorId: '',
        sectorName: '',
        location: '',
        discordUserId: null,
        contaAzulId: null,
        receives_it_notifications: false,
      } as User);
    }

    return filtered.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
  }, [users, ticket]);

  // Lista expandida com todos os usuários/médicos da clínica para seleção flexível (solicitante no topo)
  const availableRecipients = useMemo(() => {
    const linkedIds = new Set(
      [ticket?.requesterId, ...(ticket?.additionalUserIds || [])].filter(Boolean) as string[],
    );
    const linked: { id: string; name: string }[] = [];
    const others: { id: string; name: string }[] = [];

    users.forEach((u) => {
      if (linkedIds.has(u.id)) {
        const isReq = u.id === ticket?.requesterId;
        linked.push({
          id: u.id,
          name: `${u.name}${isReq ? ' (Solicitante)' : ' (Vinculado)'}`,
        });
      } else {
        others.push({
          id: u.id,
          name: u.name || 'Sem nome',
        });
      }
    });

    if (ticket?.requesterId && !linked.some((l) => l.id === ticket.requesterId)) {
      linked.unshift({
        id: ticket.requesterId,
        name: `${ticket.requesterName || 'Solicitante'} (Solicitante)`,
      });
    }

    linked.sort((a, b) => a.name.localeCompare(b.name));
    others.sort((a, b) => a.name.localeCompare(b.name));

    return [...linked, ...others];
  }, [users, ticket]);

  // Carrega os usuários associados ao ativo do chamado
  useEffect(() => {
    async function loadAssetUsers() {
      if (!isOpen || !ticket?.assetId) {
        setAssetUsers([]);
        setRecipientUserId('');
        return;
      }

      setLoadingAssetUsers(true);
      try {
        const asset = await getAssetById(ticket.assetId);
        if (asset && asset.userIds && asset.assignedToNames) {
          const mappedUsers = asset.userIds.map((id, index) => ({
            id,
            name: asset.assignedToNames?.[index] || id,
          }));
          setAssetUsers(mappedUsers);
          if (mappedUsers.length > 0) {
            setRecipientUserId(mappedUsers[0].id);
          }
        } else {
          setAssetUsers([]);
          setRecipientUserId('');
        }
      } catch {
        setAssetUsers([]);
        setRecipientUserId('');
      } finally {
        setLoadingAssetUsers(false);
      }
    }

    void loadAssetUsers();
  }, [isOpen, ticket?.assetId]);

  function resetRecipients() {
    setRecipientUserId('');
    setAssetUsers([]);
  }

  return {
    recipientUserId,
    setRecipientUserId,
    assetUsers,
    loadingAssetUsers,
    ticketUsers,
    availableRecipients,
    resetRecipients,
  };
}
