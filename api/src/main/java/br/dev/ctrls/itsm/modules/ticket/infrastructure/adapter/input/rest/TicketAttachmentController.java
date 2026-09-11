package br.dev.ctrls.itsm.modules.ticket.infrastructure.adapter.input.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.micrometer.observation.annotation.Observed;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller responsável pelo upload de mídias (imagens, GIFs, PDFs) anexadas via Editor Markdown na solução de chamados.
 */
@Slf4j
@RestController
@RequestMapping({
    "/tickets/attachments",
    "/v1/tickets/attachments",
    "/api/tickets/attachments",
    "/api/v1/tickets/attachments"
})
@Observed
public class TicketAttachmentController {

    private static final String DEFAULT_SERVER_HOST = "itsm.ctrls.dev.br";
    
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf"
    );

    /**
     * Endpoint para upload de arquivos de imagem/GIF colados ou arrastados no editor Markdown.
     *
     * @param file arquivo multipart enviado
     * @param request HttpServletRequest para resolução da URL pública
     * @return JSON { "url": "https://itsm.ctrls.dev.br/uploads/tickets/uuid.ext" }
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Arquivo inválido ou vazio."));
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }

        if (extension.isBlank() || !ALLOWED_EXTENSIONS.contains(extension)) {
            log.warn("[TICKET-ATTACHMENT] Tentativa de upload com extensão não permitida: '{}'", extension);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "Extensão de arquivo não permitida. Permitidos: " + String.join(", ", ALLOWED_EXTENSIONS)));
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            log.warn("[TICKET-ATTACHMENT] Tentativa de upload com MIME type não permitido: '{}'", contentType);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "Tipo MIME de arquivo não permitido: " + contentType));
        }

        // Determina o diretório de destino: /mnt/data/uploads/tickets se acessível, senão ./uploads/tickets
        Path uploadPath = Paths.get("/mnt/data/uploads/tickets");
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (Exception e) {
            uploadPath = Paths.get("uploads/tickets");
            try {
                Files.createDirectories(uploadPath);
            } catch (IOException ex) {
                log.error("[TICKET-ATTACHMENT] Erro ao criar diretório de upload: {}", ex.getMessage(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Não foi possível criar o diretório de mídias."));
            }
        }

        String savedFilename = UUID.randomUUID().toString() + extension;
        Path destinationFile = uploadPath.resolve(savedFilename);

        try {
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("[TICKET-ATTACHMENT] Mídia de Markdown salva com sucesso: {}", destinationFile.toAbsolutePath());
        } catch (IOException e) {
            log.error("[TICKET-ATTACHMENT] Falha ao salvar mídia em {}: {}", destinationFile, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Erro ao salvar o arquivo no servidor."));
        }

        // Constrói a URL pública acessível
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            host = DEFAULT_SERVER_HOST;
        }

        String fileUrl = scheme + "://" + host + "/api/uploads/tickets/" + savedFilename;
        log.info("[TICKET-ATTACHMENT] URL de mídia gerada: {}", fileUrl);

        return ResponseEntity.ok(Map.of("url", fileUrl));
    }
}
