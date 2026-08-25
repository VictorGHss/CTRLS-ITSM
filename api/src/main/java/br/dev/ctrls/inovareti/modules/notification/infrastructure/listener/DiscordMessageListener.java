package br.dev.ctrls.inovareti.modules.notification.infrastructure.listener;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.infrastructure.shared.storage.LocalFileStorageService;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCommentRequestDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.AddTicketCommentUseCase;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketAttachment;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketAttachmentRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Listener responsável por interceptar mensagens enviadas em canais de chamados no Discord
 * e integrá-las automaticamente como comentários no chamado no sistema.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordMessageListener extends ListenerAdapter {

    private final TicketRepositoryPort ticketRepository;
    private final UserRepositoryPort userRepository;
    private final TicketAttachmentRepositoryPort attachmentRepository;
    private final LocalFileStorageService fileStorageService;
    private final AddTicketCommentUseCase addTicketCommentUseCase;

    @Override
    public void onMessageReceived(@javax.annotation.Nonnull MessageReceivedEvent event) {
        // Ignora mensagens enviadas por bots para evitar loops de mensagens
        if (event.getAuthor().isBot()) {
            return;
        }

        // Processa apenas canais de texto
        if (!event.isFromType(ChannelType.TEXT)) {
            return;
        }

        final Ticket ticket = resolveTicketFromChannel(event.getChannel().asTextChannel());
        if (ticket == null) {
            return;
        }

        String channelName = event.getChannel().getName();
        log.info("[DISCORD-TICKET] Nova mensagem recebida no canal {} para chamado #{}: {}",
                channelName, ticket.getNumber(), event.getMessage().getContentDisplay());

        // Resolve o autor do comentário a partir do Discord ID do remetente
        String authorDiscordId = event.getAuthor().getId();
        User author = userRepository.findByDiscordUserId(authorDiscordId)
                .orElseGet(() -> {
                    log.warn("[DISCORD-TICKET] Usuário do Discord {} não está vinculado ao sistema. Usando fallback.", authorDiscordId);
                    if (ticket.getAssignedTo() != null) {
                        return ticket.getAssignedTo();
                    }
                    return ticket.getRequester();
                });

        // Configura temporariamente o contexto de segurança do Spring para a execução do caso de uso
        String principal = author.getId().toString();
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, author.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            // Processa anexos e capturas de tela enviados junto com a mensagem, se houver
            List<TicketAttachment> savedAttachments = new ArrayList<>();
            for (var attachment : event.getMessage().getAttachments()) {
                try {
                    byte[] content;
                    try (InputStream is = java.net.URI.create(attachment.getUrl()).toURL().openStream()) {
                        content = is.readAllBytes();
                    }

                    String originalFilename = attachment.getFileName();
                    String contentType = attachment.getContentType();
                    if (contentType == null) {
                        contentType = "application/octet-stream";
                    }

                    ByteArrayMultipartFile multipartFile = new ByteArrayMultipartFile(content, originalFilename, contentType);
                    String storedFilename = fileStorageService.store(multipartFile);

                    TicketAttachment ticketAttachment = TicketAttachment.builder()
                            .originalFilename(originalFilename)
                            .storedFilename(storedFilename)
                            .fileType(contentType)
                            .ticket(ticket)
                            .uploadedAt(LocalDateTime.now())
                            .build();

                    TicketAttachment saved = attachmentRepository.save(ticketAttachment);
                    savedAttachments.add(saved);
                    log.info("[DISCORD-TICKET] Anexo {} salvo com sucesso no storage e banco de dados.", originalFilename);
                } catch (Exception ex) {
                    log.error("[DISCORD-TICKET] Falha ao processar anexo {} para o chamado #{}", attachment.getFileName(), ticket.getNumber(), ex);
                }
            }

            // Constrói o texto do comentário com suporte a visualização Markdown
            String messageText = event.getMessage().getContentDisplay();
            StringBuilder commentContent = new StringBuilder(messageText);

            // Adiciona imagens em formato Markdown ![imagem](url) para renderização inline
            if (!event.getMessage().getAttachments().isEmpty()) {
                for (var attachment : event.getMessage().getAttachments()) {
                    String contentType = attachment.getContentType();
                    if (attachment.isImage() || (contentType != null && contentType.startsWith("image/"))) {
                        commentContent.append("\n\n![imagem](").append(attachment.getUrl()).append(")");
                    }
                }
            }

            if (!savedAttachments.isEmpty()) {
                if (commentContent.length() > 0) {
                    commentContent.append("\n\n");
                }
                commentContent.append("**Anexos recebidos via Discord:**");
                for (var att : savedAttachments) {
                    commentContent.append("\n- ").append(att.getOriginalFilename());
                }
            }

            if (commentContent.length() == 0) {
                commentContent.append("Enviou anexo(s) via Discord.");
            }

            TicketCommentRequestDTO commentRequest = new TicketCommentRequestDTO(commentContent.toString());
            addTicketCommentUseCase.execute(ticket.getId(), commentRequest);

            log.info("[DISCORD-TICKET] Comentário integrado com sucesso para o chamado #{}", ticket.getNumber());
        } catch (Exception ex) {
            log.error("[DISCORD-TICKET] Erro inesperado ao integrar mensagem no chamado #{}", ticket.getNumber(), ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Localiza a entidade Ticket correspondente a partir do canal do Discord.
     * Suporta identificação pelo tópico (ID: <uuid>), prefixo legado (ticket-<shortId>) ou sufixo (<titulo>-<shortId>).
     */
    private Ticket resolveTicketFromChannel(net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel) {
        String channelName = channel.getName();
        String topic = channel.getTopic();

        // 1. Tenta identificar pelo UUID contido no tópico do canal (ID: <uuid>)
        if (topic != null && topic.contains("ID: ")) {
            try {
                int idIdx = topic.indexOf("ID: ");
                String uuidStr = topic.substring(idIdx + 4).trim().split("[\\s|]+")[0];
                Ticket found = ticketRepository.findById(UUID.fromString(uuidStr)).orElse(null);
                if (found != null) {
                    return found;
                }
            } catch (Exception ignored) {}
        }

        // 2. Se não encontrou no tópico, tenta pelo formato legado (ticket-<shortId>)
        if (channelName.startsWith("ticket-")) {
            String shortId = channelName.substring("ticket-".length());
            if (shortId.length() >= 8) {
                shortId = shortId.substring(0, 8);
            }
            Ticket found = ticketRepository.findByShortIdStartingWith(shortId).stream().findFirst().orElse(null);
            if (found != null) {
                return found;
            }
        }

        // 3. Tenta pelo novo formato com sufixo de ID curto (<titulo>-<shortId>)
        String[] parts = channelName.split("-");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            if (lastPart.length() >= 4) {
                return ticketRepository.findByShortIdStartingWith(lastPart).stream().findFirst().orElse(null);
            }
        }

        return null;
    }

    /**
     * Implementação auxiliar de MultipartFile para enviar arquivos em array de bytes para o LocalFileStorageService.
     */
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String filename;
        private final String contentType;

        public ByteArrayMultipartFile(byte[] content, String filename, String contentType) {
            this.content = content;
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return filename;
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }
}
