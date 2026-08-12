package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.discord;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;

/**
 * Serviço responsável por montar e despachar notificações formatadas no Discord com suporte a Markdown e mídias anexas (Imagens/GIFs).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordNotificationService {

    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[.*?\\]\\((https?://.*?)\\)");
    private static final int SOLUTION_EMBED_COLOR = 0x22C55E; // Verde de sucesso para resolução

    private final ObjectProvider<JDA> jdaProvider;

    /**
     * Extrai a primeira URL de imagem ou GIF da marcação Markdown.
     * Exemplo: ![imagem](https://itsm-inovare.ctrls.dev.br/uploads/tickets/exemplo.gif) -> https://itsm-inovare.ctrls.dev.br/uploads/tickets/exemplo.gif
     *
     * @param markdown texto formatado em Markdown
     * @return a primeira URL de imagem encontrada ou null
     */
    public String extractFirstImageUrl(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown);
        if (matcher.find()) {
            String url = matcher.group(1).trim();
            if (url.contains("/uploads/tickets/") && !url.contains("/api/uploads/tickets/")) {
                url = url.replace("/uploads/tickets/", "/api/uploads/tickets/");
            }
            return url;
        }
        return null;
    }

    /**
     * Sanitiza o texto em Markdown para exibição em Embeds do Discord,
     * convertendo a sintaxe de imagem ![alt](url) em links clicáveis válidos [alt](url)
     * e garantindo que o prefixo /api/ esteja presente na URL.
     *
     * @param markdown texto formatado
     * @return texto sanitizado para o Discord
     */
    public String sanitizeMarkdownForDiscord(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        String sanitized = markdown.replaceAll("!\\[(.*?)\\]\\((https?://.*?)\\)", "[$1]($2)");
        if (sanitized.contains("/uploads/tickets/") && !sanitized.contains("/api/uploads/tickets/")) {
            sanitized = sanitized.replace("/uploads/tickets/", "/api/uploads/tickets/");
        }
        return sanitized;
    }

    /**
     * Monta e envia o Embed de Solução do Chamado via JDA para o usuário no Discord.
     * Caso o texto da solução contenha imagem/GIF em Markdown, a mídia é extraída e aplicada via `.setImage()`.
     *
     * @param ticket entidade do chamado
     * @param solutionMarkdown texto da nota de resolução em Markdown
     * @param discordUserId ID do usuário no Discord
     */
    public void sendTicketSolutionEmbed(Ticket ticket, String solutionMarkdown, String discordUserId) {
        if (ticket == null || discordUserId == null || discordUserId.isBlank()) {
            log.debug("[DISCORD-NOTIFICATION] Chamado ou ID de usuário Discord ausente. Notificação ignorada.");
            return;
        }

        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("[DISCORD-NOTIFICATION] JDA indisponível. Notificação de solução não enviada para o usuário {}", discordUserId);
            return;
        }

        String shortId = ticket.getId().toString().substring(0, 8).toUpperCase();
        String imageUrl = extractFirstImageUrl(solutionMarkdown);

        String textContent = (solutionMarkdown != null && !solutionMarkdown.isBlank())
                ? sanitizeMarkdownForDiscord(solutionMarkdown)
                : "Solução registrada com sucesso.";

        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setColor(SOLUTION_EMBED_COLOR)
                .setTitle("✅ Chamado #" + shortId + " - Solução do Chamado")
                .setDescription(textContent);

        if (imageUrl != null && !imageUrl.isBlank()) {
            embedBuilder.setImage(imageUrl);
            log.info("[DISCORD-NOTIFICATION] Imagem/GIF extraído da solução do chamado #{}: {}", shortId, imageUrl);
        }

        try {
            jda.retrieveUserById(java.util.Objects.requireNonNull(discordUserId.trim())).queue(
                user -> user.openPrivateChannel().queue(
                    channel -> channel.sendMessageEmbeds(embedBuilder.build()).queue(
                        success -> log.info("[DISCORD-NOTIFICATION] Embed de solução enviado para chamado #{} (user={})", shortId, discordUserId),
                        error -> log.warn("[DISCORD-NOTIFICATION] Falha ao enviar embed de solução para user {}: {}", discordUserId, error.getMessage())
                    ),
                    error -> log.warn("[DISCORD-NOTIFICATION] Falha ao abrir canal privado DM para user {}: {}", discordUserId, error.getMessage())
                ),
                error -> log.warn("[DISCORD-NOTIFICATION] Falha ao recuperar usuário Discord ID {}: {}", discordUserId, error.getMessage())
            );
        } catch (Exception ex) {
            log.error("[DISCORD-NOTIFICATION] Erro ao despachar notificação de solução para {}: {}", discordUserId, ex.getMessage(), ex);
        }
    }
}
