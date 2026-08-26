import { useState, useRef } from 'react';
import type { ClipboardEvent, DragEvent } from 'react';
import { X, AlertCircle, Image, Loader2 } from 'lucide-react';
import MDEditor from '@uiw/react-md-editor';

import { useResolveTicket } from './hooks/useResolveTicket';
import type { ResolveTicketRequest, Ticket, User } from '@/types/models';
import { uploadMarkdownAttachment } from '@/services/ticketService';

import ResolveAutoDeductionSection from './components/ResolveAutoDeductionSection';
import ResolveDeliverySection from './components/ResolveDeliverySection';
import ResolveInsumoLinkSection from './components/ResolveInsumoLinkSection';
import ResolveMaintenanceSection from './components/ResolveMaintenanceSection';

interface ResolveTicketModalProps {
  isOpen: boolean;
  onClose: () => void;
  requesterId: string;
  onResolve: (request: ResolveTicketRequest) => Promise<void>;
  ticket?: Ticket;
  users?: User[];
  initialNotes?: string;
}

/**
 * Componente modal para encerramento de chamados (resolução).
 * Permite realizar a entrega nominal de ativos de património ou insumos a funcionárias originalmente vinculadas ao chamado.
 */
export default function ResolveTicketModal({
  isOpen,
  onClose,
  requesterId,
  onResolve,
  ticket,
  users = [],
  initialNotes,
}: ResolveTicketModalProps) {
  const [isUploadingImage, setIsUploadingImage] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const {
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
    handleSubmit,
    itemsToDeliver,
    handleRecipientChange,
    handleSplitItem,
    handleRemoveSplitItem,
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
  } = useResolveTicket({
    isOpen,
    onClose,
    requesterId,
    onResolve,
    ticket,
    users,
    initialNotes,
  });

  const handleFileUpload = async (file: File) => {
    if (!file || !file.type.startsWith('image/')) return;
    setIsUploadingImage(true);
    try {
      const { url } = await uploadMarkdownAttachment(file);
      const imageMarkdown = `![${file.name}](${url})`;
      setResolutionNotes((prev) => (prev ? `${prev}\n\n${imageMarkdown}` : imageMarkdown));
    } catch (err) {
      console.error('Erro ao fazer upload da imagem:', err);
    } finally {
      setIsUploadingImage(false);
    }
  };

  const handlePaste = (e: ClipboardEvent) => {
    if (e.clipboardData.files && e.clipboardData.files.length > 0) {
      const file = e.clipboardData.files[0];
      if (file.type.startsWith('image/')) {
        e.preventDefault();
        void handleFileUpload(file);
      }
    }
  };

  const handleDrop = (e: DragEvent) => {
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0];
      if (file.type.startsWith('image/')) {
        e.preventDefault();
        void handleFileUpload(file);
      }
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4 backdrop-blur-sm">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-slate-200 bg-white shadow-2xl">
        {/* Cabeçalho */}
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white/95 px-6 py-4 backdrop-blur">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-8 w-8 items-center justify-center rounded-2xl bg-brand-primary/20">
              <AlertCircle size={16} className="text-brand-primary-dark" />
            </span>
            <h2 className="text-lg font-bold text-slate-800">Resolver Chamado</h2>
          </div>
          <button
            onClick={onClose}
            className="rounded-2xl p-2 text-slate-400 transition-colors hover:bg-brand-secondary/40 hover:text-slate-700"
            disabled={isSubmitting}
          >
            <X size={18} />
          </button>
        </div>

        {/* Formulário */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-6 p-6">
          {/* Nota de resolução */}
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium text-slate-700">Nota de Resolução (Markdown) *</label>
              <div className="flex items-center gap-2">
                {isUploadingImage && (
                  <span className="flex items-center gap-1 text-xs text-amber-600 font-medium animate-pulse">
                    <Loader2 size={13} className="animate-spin" /> Enviando mídia...
                  </span>
                )}
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={isSubmitting || isUploadingImage}
                  className="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-100 disabled:opacity-50"
                  title="Anexar Imagem ou GIF"
                >
                  <Image size={14} className="text-amber-600" />
                  Anexar Imagem/GIF
                </button>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) void handleFileUpload(file);
                    e.target.value = '';
                  }}
                />
              </div>
            </div>

            <div
              data-color-mode="light"
              onPaste={handlePaste}
              onDrop={handleDrop}
              className="w-full overflow-hidden rounded-2xl border border-slate-300 focus-within:ring-2 focus-within:ring-brand-primary/50"
            >
              <MDEditor
                value={resolutionNotes}
                onChange={(val) => setResolutionNotes(val || '')}
                height={200}
                preview="edit"
                textareaProps={{
                  placeholder: 'Descreva como o problema foi resolvido... Cole (Ctrl+V) ou arraste imagens e GIFs aqui.',
                  disabled: isSubmitting,
                }}
              />
            </div>
            <p className="text-xs text-slate-400">
              💡 Suporta formatação **Markdown**. Cole (Ctrl+V) ou arraste imagens/GIFs para anexar automaticamente.
            </p>
          </div>

          {/* Dedução Automática de Insumos OU Entrega Manual */}
          {hasAutoInventoryDeduction ? (
            <ResolveAutoDeductionSection
              itemsToDeliver={itemsToDeliver}
              availableRecipients={availableRecipients}
              onRecipientChange={handleRecipientChange}
              onSplitItem={handleSplitItem}
              onRemoveSplitItem={handleRemoveSplitItem}
            />
          ) : (
            <ResolveDeliverySection
              deliverEquipment={deliverEquipment}
              setDeliverEquipment={setDeliverEquipment}
              deliveryType={deliveryType}
              setDeliveryType={setDeliveryType}
              assetMode={assetMode}
              setAssetMode={setAssetMode}
              assets={assets}
              items={items}
              assetCategories={assetCategories}
              selectedAssetId={selectedAssetId}
              setSelectedAssetId={setSelectedAssetId}
              selectedItemId={selectedItemId}
              setSelectedItemId={setSelectedItemId}
              quantity={quantity}
              setQuantity={setQuantity}
              newAssetName={newAssetName}
              setNewAssetName={setNewAssetName}
              newAssetCategoryId={newAssetCategoryId}
              setNewAssetCategoryId={setNewAssetCategoryId}
              newAssetPatrimonyCode={newAssetPatrimonyCode}
              setNewAssetPatrimonyCode={setNewAssetPatrimonyCode}
              newAssetSpecifications={newAssetSpecifications}
              setNewAssetSpecifications={setNewAssetSpecifications}
              loadingAssets={loadingAssets}
              loadingItems={loadingItems}
              loadingAssetCategories={loadingAssetCategories}
              isSubmitting={isSubmitting}
              availableRecipients={availableRecipients}
              recipientUserId={recipientUserId}
              setRecipientUserId={setRecipientUserId}
            />
          )}

          {/* Bloco de Vinculação Patrimonial de Insumos */}
          <ResolveInsumoLinkSection
            hasAutoInventoryDeduction={hasAutoInventoryDeduction}
            deliverEquipment={deliverEquipment}
            deliveryType={deliveryType}
            selectedItemId={selectedItemId}
            linkInsumosToAsset={linkInsumosToAsset}
            setLinkInsumosToAsset={setLinkInsumosToAsset}
            loadingAllAssets={loadingAllAssets}
            allAssets={allAssets}
            targetAssetId={targetAssetId}
            setTargetAssetId={setTargetAssetId}
            isSubmitting={isSubmitting}
          />

          {/* Bloco de Registro Opcional de Manutenção */}
          <ResolveMaintenanceSection
            registerMaintenance={registerMaintenance}
            setRegisterMaintenance={setRegisterMaintenance}
            allAssets={allAssets}
            maintAssetId={maintAssetId}
            setMaintAssetId={setMaintAssetId}
            maintType={maintType}
            setMaintType={setMaintType}
            maintCost={maintCost}
            setMaintCost={setMaintCost}
            maintDescription={maintDescription}
            setMaintDescription={setMaintDescription}
            isSubmitting={isSubmitting}
          />

          {/* Ações */}
          <div className="flex justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="rounded-2xl px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100"
              disabled={isSubmitting}
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="rounded-2xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? 'Resolvendo...' : 'Resolver Chamado'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
