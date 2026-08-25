import { useState, useRef } from 'react';
import { X, Laptop, Box, AlertCircle, Image, Loader2, Split, Trash2 } from 'lucide-react';
import MDEditor from '@uiw/react-md-editor';

import { useResolveTicket } from './hooks/useResolveTicket';
import type { ResolveTicketRequest, Ticket, User } from '@/types/models';
import SearchableDropdown from '@/components/common/SearchableDropdown';
import { uploadMarkdownAttachment } from '@/services/ticketService';

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
    // Novos campos desestruturados para manutenção
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

  const handlePaste = (e: React.ClipboardEvent) => {
    if (e.clipboardData.files && e.clipboardData.files.length > 0) {
      const file = e.clipboardData.files[0];
      if (file.type.startsWith('image/')) {
        e.preventDefault();
        void handleFileUpload(file);
      }
    }
  };

  const handleDrop = (e: React.DragEvent) => {
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

          {hasAutoInventoryDeduction ? (
            <div className="flex flex-col gap-4">
              <div className="flex items-start gap-3 rounded-2xl border border-brand-primary/35 bg-brand-secondary/55 p-4">
                <AlertCircle size={18} className="mt-0.5 shrink-0 text-brand-primary-dark" />
                <div>
                  <p className="text-sm font-semibold text-slate-800">Dedução Automática de Insumos</p>
                  <p className="mt-1 text-sm text-slate-700">
                    Os seguintes itens solicitados serão deduzidos automaticamente do stock ao fechar este chamado:
                  </p>
                </div>
              </div>

              <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 p-4 bg-slate-50/50">
                {itemsToDeliver.map((item) => {
                  const canSplit = item.quantity > 1;
                  const isSplitRow = itemsToDeliver.filter((i) => i.itemId === item.itemId).length > 1;

                  return (
                    <div
                      key={item.id}
                      className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm"
                    >
                      <div className="flex flex-col gap-1 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-bold text-slate-800">{item.itemName}</span>
                          {isSplitRow && (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-100 text-amber-800 border border-amber-200">
                              Entrega Parcial
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-slate-500 font-medium">Quantidade:</span>
                          <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-bold bg-slate-100 text-slate-700">
                            {item.quantity} un
                          </span>
                          {canSplit && (
                            <button
                              type="button"
                              onClick={() => handleSplitItem(item.id)}
                              className="inline-flex items-center gap-1 text-[11px] font-semibold text-brand-primary hover:text-brand-primary-dark hover:underline transition-colors ml-2 cursor-pointer"
                              title="Dividir este item para entregar a mais de uma pessoa"
                            >
                              <Split size={13} />
                              Dividir Entrega
                            </button>
                          )}
                        </div>
                      </div>

                      <div className="flex items-end gap-2 w-full sm:w-72">
                        <div className="flex flex-col gap-1 flex-1">
                          <label className="text-xs font-semibold text-slate-600">Entregar a quem? *</label>
                          <SearchableDropdown
                            options={availableRecipients}
                            value={item.recipientUserId}
                            onChange={(val) => handleRecipientChange(item.id, val)}
                            placeholder="Pesquise o médico ou secretária..."
                          />
                        </div>
                        {isSplitRow && (
                          <button
                            type="button"
                            onClick={() => handleRemoveSplitItem(item.id)}
                            className="p-2 rounded-xl text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors cursor-pointer"
                            title="Remover esta entrega parcial e unificar quantidade"
                          >
                            <Trash2 size={16} />
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ) : (
            <>
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="deliverEquipment"
                  checked={deliverEquipment}
                  onChange={(event) => setDeliverEquipment(event.target.checked)}
                  className="h-4 w-4 cursor-pointer rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
                  disabled={isSubmitting}
                />
                <label htmlFor="deliverEquipment" className="cursor-pointer text-sm font-medium text-slate-700">
                  Entregar Equipamento ou Material nesta resolução?
                </label>
              </div>

              {deliverEquipment && (
                <div className="flex flex-col gap-4 rounded-2xl border border-brand-primary/40 bg-brand-secondary/50 p-4">
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={() => setDeliveryType('asset')}
                      className={`flex items-center gap-2 rounded-2xl px-4 py-2 text-sm font-semibold transition-colors ${
                        deliveryType === 'asset'
                          ? 'bg-brand-primary text-white'
                          : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                      }`}
                      disabled={isSubmitting}
                    >
                      <Laptop size={16} />
                      Ativo de Patrimônio
                    </button>
                    <button
                      type="button"
                      onClick={() => setDeliveryType('item')}
                      className={`flex items-center gap-2 rounded-2xl px-4 py-2 text-sm font-semibold transition-colors ${
                        deliveryType === 'item'
                          ? 'bg-brand-primary text-white'
                          : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                      }`}
                      disabled={isSubmitting}
                    >
                      <Box size={16} />
                      Item de Consumo
                    </button>
                  </div>

                  {deliveryType === 'asset' && (
                    <div className="flex flex-col gap-4">
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => setAssetMode('existing')}
                          className={`rounded-2xl px-3 py-1.5 text-sm font-semibold transition-colors ${
                            assetMode === 'existing'
                              ? 'bg-brand-primary text-white'
                              : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                          }`}
                          disabled={isSubmitting}
                        >
                          Selecionar Existente
                        </button>
                        <button
                          type="button"
                          onClick={() => setAssetMode('new')}
                          className={`rounded-2xl px-3 py-1.5 text-sm font-semibold transition-colors ${
                            assetMode === 'new'
                              ? 'bg-brand-primary text-white'
                              : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-100'
                          }`}
                          disabled={isSubmitting}
                        >
                          Cadastrar Novo
                        </button>
                      </div>

                      {assetMode === 'existing' && (
                        <div className="flex flex-col gap-2">
                          <label className="text-sm font-medium text-slate-700">Selecione o Equipamento *</label>
                          {loadingAssets ? (
                            <div className="text-sm text-slate-500">Carregando equipamentos...</div>
                          ) : assets.length === 0 ? (
                            <div className="text-sm text-red-600">Nenhum equipamento disponível no estoque da TI.</div>
                          ) : (
                            <SearchableDropdown
                              options={assets.map((asset) => ({
                                id: asset.id,
                                name: `${asset.name} (${asset.patrimonyCode})`,
                              }))}
                              value={selectedAssetId}
                              onChange={(val) => setSelectedAssetId(val)}
                              placeholder="Selecione um equipamento..."
                              disabled={isSubmitting}
                            />
                          )}
                        </div>
                      )}

                      {assetMode === 'new' && (
                        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                          <div className="flex flex-col gap-2 md:col-span-2">
                            <label className="text-sm font-medium text-slate-700">Nome do Ativo *</label>
                            <input
                              type="text"
                              value={newAssetName}
                              onChange={(event) => setNewAssetName(event.target.value)}
                              className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                              placeholder="Ex.: Notebook Dell Latitude"
                              disabled={isSubmitting}
                            />
                          </div>

                          <div className="flex flex-col gap-2">
                            <label className="text-sm font-medium text-slate-700">Categoria *</label>
                            {loadingAssetCategories ? (
                              <div className="text-sm text-slate-500">Carregando categorias...</div>
                            ) : (
                              <SearchableDropdown
                                options={assetCategories.map((category) => ({
                                  id: category.id,
                                  name: category.name,
                                }))}
                                value={newAssetCategoryId}
                                onChange={(val) => setNewAssetCategoryId(val)}
                                placeholder="Selecione a categoria..."
                                disabled={isSubmitting}
                              />
                            )}
                          </div>

                          <div className="flex flex-col gap-2">
                            <label className="text-sm font-medium text-slate-700">Código do Patrimônio *</label>
                            <input
                              type="text"
                              value={newAssetPatrimonyCode}
                              onChange={(event) => setNewAssetPatrimonyCode(event.target.value)}
                              className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                              placeholder="Ex.: PAT-2026-001"
                              disabled={isSubmitting}
                            />
                          </div>

                          <div className="flex flex-col gap-2 md:col-span-2">
                            <label className="text-sm font-medium text-slate-700">Especificações</label>
                            <textarea
                              value={newAssetSpecifications}
                              onChange={(event) => setNewAssetSpecifications(event.target.value)}
                              className="w-full resize-none rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                              rows={3}
                              placeholder="CPU, memória, armazenamento, etc."
                              disabled={isSubmitting}
                            />
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {deliveryType === 'item' && (
                    <>
                      <div className="flex flex-col gap-2">
                        <label className="text-sm font-medium text-slate-700">Selecione o Material *</label>
                        {loadingItems ? (
                          <div className="text-sm text-slate-500">Carregando materiais...</div>
                        ) : items.length === 0 ? (
                          <div className="text-sm text-red-600">Nenhum material disponível em estoque.</div>
                        ) : (
                          <SearchableDropdown
                            options={items.map((item) => ({
                              id: item.id,
                              name: `${item.name} (Estoque: ${item.currentStock})`,
                            }))}
                            value={selectedItemId}
                            onChange={(val) => setSelectedItemId(val)}
                            placeholder="Selecione um material..."
                            disabled={isSubmitting}
                          />
                        )}
                      </div>

                      <div className="flex flex-col gap-2">
                        <label className="text-sm font-medium text-slate-700">Quantidade *</label>
                        <input
                          type="number"
                          value={quantity}
                          onChange={(event) => setQuantity(Math.max(1, Number.parseInt(event.target.value, 10) || 1))}
                          min="1"
                          className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                          disabled={isSubmitting}
                        />
                      </div>

                      <div className="flex flex-col gap-2">
                        <label className="text-sm font-medium text-slate-700">Entregar a quem? *</label>
                        <SearchableDropdown
                          options={availableRecipients}
                          value={recipientUserId}
                          onChange={(val) => setRecipientUserId(val)}
                          placeholder="Selecione quem recebeu..."
                        />
                      </div>
                    </>
                  )}
                </div>
              )}
            </>
          )}

          {/* Bloco de Vinculação Patrimonial de Insumos (Vertical 3) */}
          {(hasAutoInventoryDeduction || (deliverEquipment && deliveryType === 'item' && selectedItemId)) && (
            <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-slate-50/50 p-4">
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="linkInsumosToAsset"
                  checked={linkInsumosToAsset}
                  onChange={(event) => setLinkInsumosToAsset(event.target.checked)}
                  className="h-4 w-4 cursor-pointer rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
                  disabled={isSubmitting}
                />
                <label htmlFor="linkInsumosToAsset" className="cursor-pointer text-sm font-medium text-slate-700">
                  Vincular insumos diretamente a um Equipamento/Ativo?
                </label>
              </div>

              {linkInsumosToAsset && (
                <div className="flex flex-col gap-2">
                  <label className="text-sm font-medium text-slate-700">Ativo / Equipamento do Inventário *</label>
                  {loadingAllAssets ? (
                    <div className="text-sm text-slate-500">Carregando equipamentos do inventário...</div>
                  ) : allAssets.length === 0 ? (
                    <div className="text-sm text-amber-600 flex items-center gap-1.5">
                      <AlertCircle size={14} />
                      Nenhum equipamento físico encontrado no módulo de inventário.
                    </div>
                  ) : (
                    <SearchableDropdown
                      options={allAssets.map((asset) => ({
                        id: asset.id,
                        name: `${asset.name} (${asset.patrimonyCode})`,
                      }))}
                      value={targetAssetId}
                      onChange={(val) => setTargetAssetId(val)}
                      placeholder="Selecione o equipamento do inventário..."
                      disabled={isSubmitting}
                    />
                  )}
                </div>
              )}
            </div>
          )}

          {/* Bloco de Registro Opcional de Manutenção (Cenário A) */}
          <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-slate-50/50 p-4">
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="registerMaintenance"
                checked={registerMaintenance}
                onChange={(event) => setRegisterMaintenance(event.target.checked)}
                className="h-4 w-4 cursor-pointer rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
                disabled={isSubmitting}
              />
              <label htmlFor="registerMaintenance" className="cursor-pointer text-sm font-medium text-slate-700">
                Registrar Manutenção de Ativo associada a este chamado?
              </label>
            </div>

            {registerMaintenance && (
              <div className="flex flex-col gap-4">
                <div className="flex flex-col gap-2">
                  <label className="text-sm font-medium text-slate-700">Equipamento Afetado *</label>
                  {allAssets.length === 0 ? (
                    <div className="text-sm text-amber-600 flex items-center gap-1.5">
                      <AlertCircle size={14} />
                      Nenhum equipamento cadastrado no CMDB.
                    </div>
                  ) : (
                    <SearchableDropdown
                      options={allAssets.map((asset) => ({
                        id: asset.id,
                        name: `${asset.name} (${asset.patrimonyCode})`,
                      }))}
                      value={maintAssetId}
                      onChange={(val) => setMaintAssetId(val)}
                      placeholder="Selecione o equipamento..."
                      disabled={isSubmitting}
                    />
                  )}
                </div>

                <div className="flex flex-col gap-2">
                  <label className="text-sm font-medium text-slate-700">Tipo de Manutenção</label>
                  <select
                    value={maintType}
                    onChange={(event) => setMaintType(event.target.value as 'PREVENTIVE' | 'CORRECTIVE' | 'UPGRADE' | 'TRANSFER')}
                    className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                    disabled={isSubmitting}
                  >
                    <option value="PREVENTIVE">Preventiva</option>
                    <option value="CORRECTIVE">Corretiva</option>
                    <option value="UPGRADE">Upgrade</option>
                    <option value="TRANSFER">Transferência</option>
                  </select>
                </div>

                <div className="flex flex-col gap-2">
                  <label className="text-sm font-medium text-slate-700">Custo da Manutenção (R$)</label>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="0.00"
                    value={maintCost}
                    onChange={(event) => setMaintCost(event.target.value)}
                    className="w-full rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                    disabled={isSubmitting}
                  />
                </div>

                <div className="flex flex-col gap-2">
                  <label className="text-sm font-medium text-slate-700">Descrição/Laudo Técnico</label>
                  <textarea
                    value={maintDescription}
                    onChange={(event) => setMaintDescription(event.target.value)}
                    placeholder="Descreva o serviço realizado no equipamento..."
                    className="w-full resize-none rounded-2xl border border-slate-300 px-3 py-2 text-sm focus:ring-2 focus:ring-brand-primary/50"
                    rows={3}
                    disabled={isSubmitting}
                  />
                </div>
              </div>
            )}
          </div>

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
