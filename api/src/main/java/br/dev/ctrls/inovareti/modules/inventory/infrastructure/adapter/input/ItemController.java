package br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.input;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.infrastructure.shared.storage.FileStorageService;
import br.dev.ctrls.inovareti.infrastructure.shared.storage.InvoiceFileMetadata;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.AllocateConsumableRequestDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.BatchResponseDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.ItemAllocationResponseDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.ItemRequestDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.ItemResponseDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.LinkComponentRequestDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.StockBatchRequestDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.StockBatchResponseDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.dto.StockMovementResponseDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.AllocateConsumableUseCase;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.CreateItemUseCase;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.FindItemByIdUseCase;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.LinkAssetComponentUseCase;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.ListAllItemsUseCase;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.ListItemBatchesUseCase;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.RegisterStockBatchUseCase;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemAllocationEntity;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockBatchRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockMovementRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository.ItemAllocationJpaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para gerenciamento de itens de inventário e seus lotes de estoque.
 * Base path: /api/items
 */
@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
@Observed
public class ItemController {

    private final CreateItemUseCase createItemUseCase;
    private final ListAllItemsUseCase listAllItemsUseCase;
    private final RegisterStockBatchUseCase registerStockBatchUseCase;
    private final FindItemByIdUseCase findItemByIdUseCase;
    private final ListItemBatchesUseCase listItemBatchesUseCase;
    private final LinkAssetComponentUseCase linkAssetComponentUseCase;
    private final AllocateConsumableUseCase allocateConsumableUseCase;
    private final ItemAllocationJpaRepository itemAllocationRepository;
    private final StockBatchRepositoryPort stockBatchRepository;
    private final StockMovementRepositoryPort stockMovementRepository;
    private final FileStorageService fileStorageService;
    private final ItemRepositoryPort itemRepository;

    /**
     * Retorna todos os itens de inventário, com categoria carregada via JOIN FETCH.
     * Retorna 200 OK com a lista (vazia se não houver itens).
     * Todos os usuários autenticados podem ler (necessário para formulários de chamados).
     */
    @GetMapping
    public ResponseEntity<Page<ItemResponseDTO>> listAll(
            @RequestParam(defaultValue = "name") String sortField,
            @RequestParam(defaultValue = "ASC") Sort.Direction sortDirection,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, 15);
        
        if (search != null && !search.trim().isEmpty()) {
            Page<Item> itemsPage = itemRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
            Page<ItemResponseDTO> pageResult = itemsPage.map(ItemResponseDTO::from);
            return ResponseEntity.ok(pageResult);
        }
        
        return ResponseEntity.ok(listAllItemsUseCase.execute(sortField, sortDirection, lowStockOnly, pageable));
    }

    /**
     * Busca um item de inventário específico por ID.
     * Retorna 200 OK com os dados do item ou 404 se não encontrado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ItemResponseDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(findItemByIdUseCase.execute(id));
    }

    /**
     * Lista todos os lotes de estoque de um item específico.
     * Os lotes são retornados ordenados do mais recente para o mais antigo.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @GetMapping("/{id}/batches")
    public ResponseEntity<List<BatchResponseDTO>> listBatches(@PathVariable UUID id) {
        return ResponseEntity.ok(listItemBatchesUseCase.execute(id));
    }

    /**
     * Lista as movimentações de saída de estoque para um item específico.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @GetMapping("/{id}/movements/out")
    public ResponseEntity<List<StockMovementResponseDTO>> listOutMovements(@PathVariable UUID id) {
        List<StockMovementResponseDTO> response = stockMovementRepository
                .findByItemIdAndTypeOrderByDateDesc(id, StockMovementType.OUT)
                .stream()
                .map(StockMovementResponseDTO::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Cria um novo item de inventário com estoque inicial zero.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping
    public ResponseEntity<ItemResponseDTO> create(@Valid @RequestBody ItemRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createItemUseCase.execute(request));
    }

    /**
     * Registra um lote de entrada de estoque para o item informado.
     * Atualiza o estoque atual do item atomicamente.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping("/{id}/batches")
    public ResponseEntity<StockBatchResponseDTO> registerBatch(
            @PathVariable UUID id,
            @Valid @RequestBody StockBatchRequestDTO request) {

        // Garante que o itemId do path e do body sejam consistentes
        StockBatchRequestDTO consistentRequest = new StockBatchRequestDTO(
                id,
                request.quantity(),
                request.unitPrice(),
                request.brand(),
                request.supplier(),
                request.purchaseReason()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(registerStockBatchUseCase.execute(consistentRequest));
    }

    /**
     * Upload de nota fiscal (PDF ou imagem) para um lote de estoque.
     * O arquivo é salvo em disco e os metadados são armazenados na entidade StockBatch.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping("/{itemId}/batches/{batchId}/invoice")
    public ResponseEntity<BatchResponseDTO> uploadBatchInvoice(
            @PathVariable UUID itemId,
            @PathVariable UUID batchId,
            @RequestParam("file") MultipartFile file) throws BadRequestException {

        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Lote de estoque não encontrado com o id: " + batchId));

        // Valida que o lote pertence ao item informado
        if (!batch.getItem().getId().equals(itemId)) {
            throw new BadRequestException("O lote de estoque não pertence ao item informado.");
        }

        // Se já existe um arquivo anterior, remove-o do disco
        if (batch.getInvoiceFilePath() != null) {
            fileStorageService.deleteInvoiceFile(batch.getInvoiceFilePath());
        }

        // Salva o novo arquivo
        InvoiceFileMetadata metadata = fileStorageService.saveInvoiceFile(file, batchId, "batch");

        // Atualiza a entidade com os metadados do arquivo
        batch.setInvoiceFileName(metadata.getFileName());
        batch.setInvoiceContentType(metadata.getContentType());
        batch.setInvoiceFilePath(metadata.getFilePath());

        StockBatch updatedBatch = stockBatchRepository.save(batch);
        return ResponseEntity.ok(BatchResponseDTO.from(updatedBatch));
    }

    /**
     * Download de nota fiscal (PDF ou imagem) de um lote de estoque.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'INVENTORY_MANAGER')")
    @GetMapping("/{itemId}/batches/{batchId}/invoice")
    public ResponseEntity<byte[]> downloadBatchInvoice(
            @PathVariable UUID itemId,
            @PathVariable UUID batchId) {

        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Lote de estoque não encontrado com o id: " + batchId));

        // Valida que o lote pertence ao item informado
        if (!batch.getItem().getId().equals(itemId)) {
            throw new BadRequestException("O lote de estoque não pertence ao item informado.");
        }

        if (batch.getInvoiceFilePath() == null || batch.getInvoiceFilePath().isBlank()) {
            throw new NotFoundException("Nenhuma nota fiscal anexada a este lote.");
        }

        byte[] fileContent = fileStorageService.loadInvoiceFile(batch.getInvoiceFilePath());

        return ResponseEntity.ok()
                .header("Content-Type", batch.getInvoiceContentType())
                .header("Content-Disposition",
                        "inline; filename=\"" + batch.getInvoiceFileName() + "\"")
                .body(fileContent);
    }

    /**
     * Busca de forma paginada os itens que atingiram o fim de vida útil (obsolescência de hardware).
     */
    @GetMapping("/obsolete")
    public ResponseEntity<Page<ItemResponseDTO>> listObsolete(
            @RequestParam(defaultValue = "0") int page) {
        Pageable pageable = PageRequest.of(page, 15);
        Page<Item> itemsPage = itemRepository.findObsoleteItems(pageable);
        Page<ItemResponseDTO> pageResult = itemsPage.map(ItemResponseDTO::from);
        return ResponseEntity.ok(pageResult);
    }

    /**
     * Acopla um ativo filho (componente) a um ativo principal (pai) no inventário.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping("/{id}/components")
    public ResponseEntity<Void> linkComponent(
            @PathVariable UUID id,
            @RequestBody @Valid LinkComponentRequestDTO request) {
        linkAssetComponentUseCase.execute(id, request.componentId());
        return ResponseEntity.ok().build();
    }

    /**
     * Aloca um consumível ou periférico a um ativo principal, decrementando o estoque físico (FIFO).
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping("/{id}/allocations")
    public ResponseEntity<Void> allocateConsumable(
            @PathVariable UUID id,
            @RequestBody @Valid AllocateConsumableRequestDTO request) {
        allocateConsumableUseCase.execute(id, request.childItemId(), request.quantity(), request.ticketId());
        return ResponseEntity.ok().build();
    }

    /**
     * Retorna a lista de alocações vinculadas a este item de inventário.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @GetMapping("/{id}/allocations")
    public ResponseEntity<List<ItemAllocationResponseDTO>> listAllocations(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean asParent) {
        
        List<ItemAllocationEntity> allocations = asParent
                ? itemAllocationRepository.findByParentItemIdOrderByAllocatedAtDesc(id)
                : itemAllocationRepository.findByChildItemIdOrderByAllocatedAtDesc(id);

        List<ItemAllocationResponseDTO> dtos = allocations.stream()
                .map(ItemAllocationResponseDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}


