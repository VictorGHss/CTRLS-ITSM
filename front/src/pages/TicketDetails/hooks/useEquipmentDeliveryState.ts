import { useEffect, useState } from 'react';
import { toast } from 'react-toastify';
import { getAssetCategories, getAssets, getItems } from '@/services/inventoryService';
import type { Asset, AssetCategory, Item } from '@/types/models';

export function useEquipmentDeliveryState(isOpen: boolean) {
  const [deliverEquipment, setDeliverEquipment] = useState(false);
  const [deliveryType, setDeliveryType] = useState<'asset' | 'item'>('asset');
  const [assetMode, setAssetMode] = useState<'existing' | 'new'>('existing');

  const [assets, setAssets] = useState<Asset[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [assetCategories, setAssetCategories] = useState<AssetCategory[]>([]);
  const [allAssets, setAllAssets] = useState<Asset[]>([]);

  const [selectedAssetId, setSelectedAssetId] = useState('');
  const [selectedItemId, setSelectedItemId] = useState('');
  const [quantity, setQuantity] = useState(1);

  const [linkInsumosToAsset, setLinkInsumosToAsset] = useState(false);
  const [targetAssetId, setTargetAssetId] = useState('');

  const [newAssetName, setNewAssetName] = useState('');
  const [newAssetCategoryId, setNewAssetCategoryId] = useState('');
  const [newAssetPatrimonyCode, setNewAssetPatrimonyCode] = useState('');
  const [newAssetSpecifications, setNewAssetSpecifications] = useState('');

  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingItems, setLoadingItems] = useState(false);
  const [loadingAssetCategories, setLoadingAssetCategories] = useState(false);
  const [loadingAllAssets, setLoadingAllAssets] = useState(false);

  // Carrega ativos disponíveis quando o fluxo de entrega de patrimônio estiver ativo
  useEffect(() => {
    async function loadAssets() {
      if (!isOpen || !deliverEquipment || deliveryType !== 'asset' || assetMode !== 'existing') {
        setAssets([]);
        return;
      }

      setLoadingAssets(true);
      try {
        const allAssetsPage = await getAssets();
        const availableAssets = allAssetsPage.content.filter((asset) => !asset.userId);
        setAssets(availableAssets);
        setSelectedAssetId('');
      } catch {
        toast.error('Erro ao carregar equipamentos disponíveis.');
        setAssets([]);
      } finally {
        setLoadingAssets(false);
      }
    }

    void loadAssets();
  }, [assetMode, deliverEquipment, deliveryType, isOpen]);

  // Carrega categorias para cadastro de novo ativo
  useEffect(() => {
    async function loadAssetCategories() {
      if (!isOpen || !deliverEquipment || deliveryType !== 'asset' || assetMode !== 'new') {
        setAssetCategories([]);
        return;
      }

      setLoadingAssetCategories(true);
      try {
        const data = await getAssetCategories();
        setAssetCategories(data);
      } catch {
        toast.error('Erro ao carregar categorias de ativo.');
        setAssetCategories([]);
      } finally {
        setLoadingAssetCategories(false);
      }
    }

    void loadAssetCategories();
  }, [assetMode, deliverEquipment, deliveryType, isOpen]);

  // Carrega todos os ativos sem filtro de usuário para a vinculação opcional de insumos
  useEffect(() => {
    async function loadAllAssets() {
      if (!isOpen) {
        setAllAssets([]);
        return;
      }
      setLoadingAllAssets(true);
      try {
        const allAssetsPage = await getAssets({ page: 0, size: 1000 });
        setAllAssets(allAssetsPage.content || []);
      } catch (err) {
        console.error('Erro ao carregar lista geral de ativos:', err);
      } finally {
        setLoadingAllAssets(false);
      }
    }
    void loadAllAssets();
  }, [isOpen]);

  // Carrega itens de consumo com estoque disponível
  useEffect(() => {
    async function loadItems() {
      if (!isOpen || !deliverEquipment || deliveryType !== 'item') {
        setItems([]);
        return;
      }

      setLoadingItems(true);
      try {
        const allItemsPage = await getItems({ size: 1000 });
        const availableItems = allItemsPage.content.filter((item) => item.currentStock > 0);
        setItems(availableItems);
        setSelectedItemId('');
      } catch {
        toast.error('Erro ao carregar materiais disponíveis.');
        setItems([]);
      } finally {
        setLoadingItems(false);
      }
    }

    void loadItems();
  }, [deliverEquipment, deliveryType, isOpen]);

  function resetEquipmentDelivery() {
    setDeliverEquipment(false);
    setDeliveryType('asset');
    setAssetMode('existing');
    setSelectedAssetId('');
    setSelectedItemId('');
    setQuantity(1);
    setNewAssetName('');
    setNewAssetCategoryId('');
    setNewAssetPatrimonyCode('');
    setNewAssetSpecifications('');
    setLinkInsumosToAsset(false);
    setTargetAssetId('');
  }

  return {
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
  };
}
