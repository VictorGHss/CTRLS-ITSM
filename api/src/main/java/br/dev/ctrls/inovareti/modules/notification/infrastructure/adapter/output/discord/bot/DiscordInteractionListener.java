package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

/**
 * Listener JDA responsável pelos eventos interativos do bot Discord:
 * <ul>
 *   <li><b>/ti status</b> — exibe métricas de infraestrutura do servidor</li>
 *   <li><b>/solicitar</b> — cria chamado de solicitação de insumo com autocomplete de itens</li>
 *   <li><b>Botão Assumir / Resolver</b> — técnico assume ou resolve o chamado diretamente no Discord</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordInteractionListener extends ListenerAdapter {

    /** Prefixo do Custom ID para o botão "Assumir chamado". */
    public static final String BOTAO_ASSUMIR_PREFIX = "ticket_accept:";

    /** Prefixo do Custom ID para o botão "Recusar chamado". */
    public static final String BOTAO_RECUSAR_PREFIX = "ticket_reject:";

    private final DiscordInfraStatusService infraStatusService;
    private final DiscordSolicitarService solicitarService;
    private final DiscordCommandService discordCommandService;
    private final br.dev.ctrls.inovareti.modules.ticket.domain.port.output.DiscordTicketPort discordTicketPort;
    private final br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort ticketRepository;

    @Value("${discord.bot.admin-ids:}")
    private String adminIdsRaw;

    @Value("${discord.admin-id:${DISCORD_ADMIN_ID:}}")
    private String adminDiscordId;

    @Qualifier("discordExecutor")
    private final Executor discordExecutor;

    @jakarta.annotation.PostConstruct
    public void validateAdminConfig() {
        if (adminDiscordId == null || adminDiscordId.isBlank()) {
            log.warn("[DISCORD] AVISO: A variável 'DISCORD_ADMIN_ID' não foi configurada no .env. Comandos restritos validarão apenas privilégios de técnicos no ITSM.");
        } else {
            log.info("[DISCORD] DISCORD_ADMIN_ID configurado com sucesso via .env.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SLASH COMMANDS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onSlashCommandInteraction(@javax.annotation.Nonnull SlashCommandInteractionEvent event) {
        String nome = event.getName();

        switch (nome) {
            case "ti"        -> handleTiStatus(event);
            case "solicitar" -> handleSolicitar(event);
        }
    }

    private void handleTiStatus(SlashCommandInteractionEvent event) {
        log.info("[DISCORD][/ti status] Solicitado por: {} ({})",
                event.getUser().getAsTag(), event.getUser().getId());

        event.deferReply().setEphemeral(true).queue();
        String discordId = event.getUser().getId();

        discordExecutor.execute(() -> {
            try {
                if (!isAdministrator(discordId)) {
                    event.getHook().sendMessage("🔒 Acesso negado. Este comando é restrito a administradores de TI validados por ID.").queue();
                    return;
                }
                User usuario = discordCommandService.resolverTecnico(discordId);
                if (usuario == null) {
                    event.getHook().sendMessage("🔒 Acesso negado. Este comando é restrito a técnicos e administradores de TI.").queue();
                    return;
                }

                MessageEmbed embed = Objects.requireNonNull(
                        infraStatusService.construirEmbedStatus(),
                        "construirEmbedStatus() retornou null");
                event.getHook().sendMessageEmbeds(embed).queue(
                        ok  -> log.info("[DISCORD][/ti status] Embed enviado com sucesso para {}", discordId),
                        err -> log.warn("[DISCORD][/ti status] Falha ao enviar embed: {}", err.getMessage())
                );
            } catch (Exception ex) {
                log.error("[DISCORD][/ti status] Erro inesperado ao processar comando: {}", ex.getMessage(), ex);
                event.getHook().sendMessage("❌ Erro ao coletar métricas de infraestrutura. Verifique os logs do servidor.").queue();
            }
        });
    }

    private void handleSolicitar(SlashCommandInteractionEvent event) {
        log.info("[DISCORD][/solicitar] Acionado por: {} ({})",
                event.getUser().getAsTag(), event.getUser().getId());

        var optItem1 = event.getOption("item1");
        var optQtd1  = event.getOption("qtd1");
        var optItem2 = event.getOption("item2");
        var optQtd2  = event.getOption("qtd2");
        var optItem3 = event.getOption("item3");
        var optQtd3  = event.getOption("qtd3");
        var optObs   = event.getOption("observacao");

        // Suporte legado a chamados únicos
        var optItensLegacy = event.getOption("itens");
        var optItemLegacy  = event.getOption("item");
        var optQtdLegacy   = event.getOption("quantidade");

        List<DiscordSolicitarService.ItemRequestOptionDTO> itemsRequested = new ArrayList<>();

        if (optItem1 != null && !optItem1.getAsString().isBlank()) {
            int q1 = optQtd1 != null ? Math.max(1, optQtd1.getAsInt()) : 1;
            itemsRequested.add(new DiscordSolicitarService.ItemRequestOptionDTO(sanitizeInput(optItem1.getAsString()), q1));
        }

        if (optItem2 != null && !optItem2.getAsString().isBlank()) {
            int q2 = optQtd2 != null ? Math.max(1, optQtd2.getAsInt()) : 1;
            itemsRequested.add(new DiscordSolicitarService.ItemRequestOptionDTO(sanitizeInput(optItem2.getAsString()), q2));
        }

        if (optItem3 != null && !optItem3.getAsString().isBlank()) {
            int q3 = optQtd3 != null ? Math.max(1, optQtd3.getAsInt()) : 1;
            itemsRequested.add(new DiscordSolicitarService.ItemRequestOptionDTO(sanitizeInput(optItem3.getAsString()), q3));
        }

        if (itemsRequested.isEmpty()) {
            if (optItensLegacy != null && !optItensLegacy.getAsString().isBlank()) {
                int qLeg = optQtdLegacy != null ? Math.max(1, optQtdLegacy.getAsInt()) : 1;
                itemsRequested.add(new DiscordSolicitarService.ItemRequestOptionDTO(sanitizeInput(optItensLegacy.getAsString()), qLeg));
            } else if (optItemLegacy != null && !optItemLegacy.getAsString().isBlank()) {
                int qLeg = optQtdLegacy != null ? Math.max(1, optQtdLegacy.getAsInt()) : 1;
                itemsRequested.add(new DiscordSolicitarService.ItemRequestOptionDTO(sanitizeInput(optItemLegacy.getAsString()), qLeg));
            }
        }

        if (itemsRequested.isEmpty()) {
            event.reply("❌ Informe ao menos um item a ser solicitado.").setEphemeral(true).queue();
            return;
        }

        String observacao = optObs != null ? optObs.getAsString().trim() : null;
        String discordUserId = event.getUser().getId();

        event.deferReply().queue();

        discordExecutor.execute(() -> {
            try {
                String resposta = solicitarService.criarTicketDeSolicitacaoEstruturada(
                        discordUserId, itemsRequested, observacao);
                if (resposta == null) {
                    resposta = "❌ Erro inesperado ao registrar sua solicitação.";
                }
                event.getHook().sendMessage(resposta).queue();
            } catch (Exception ex) {
                log.error("[DISCORD][/solicitar] Erro ao criar chamado de solicitação: {}", ex.getMessage(), ex);
                event.getHook().sendMessage("❌ Erro ao registrar sua solicitação. Tente novamente ou contate a TI.").queue();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUTOCOMPLETE
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("null")
    public void onCommandAutoCompleteInteraction(
            @javax.annotation.Nonnull CommandAutoCompleteInteractionEvent event) {

        if (!"solicitar".equals(event.getName())) {
            return;
        }

        var opt = event.getFocusedOption();
        String optionName = opt.getName();
        if (!"item1".equals(optionName) && !"item2".equals(optionName) && !"item3".equals(optionName)
                && !"item".equals(optionName) && !"itens".equals(optionName)) {
            return;
        }

        String textoDigitado = Objects.requireNonNull(opt.getValue(), "textoDigitado");
        log.debug("[DISCORD][autocomplete] '/solicitar {}' — filtro: '{}'", optionName, textoDigitado);

        try {
            List<Command.Choice> opcoes = List.copyOf(
                    solicitarService.buscarOpcoesAutocomplete(textoDigitado));
            event.replyChoices(opcoes).queue();
        } catch (Exception ex) {
            log.warn("[DISCORD][autocomplete] Erro ao buscar opções: {}", ex.getMessage());
            event.replyChoices(
                    new Command.Choice(
                            DiscordSolicitarService.ITEM_FORA_DE_ESTOQUE_ID,
                            "Outros / Fora de Estoque"))
                    .queue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BOTÕES E MODAIS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onButtonInteraction(@javax.annotation.Nonnull ButtonInteractionEvent event) {
        String customId = event.getComponentId();
        log.info("[DISCORD][botão] Clique recebido. CustomId='{}', Usuário='{}'",
                customId, event.getUser().getAsTag());

        if (customId.startsWith("ticket:resolver:")) {
            String ticketIdStr = customId.substring("ticket:resolver:".length());
            handleBotaoResolverModal(event, ticketIdStr);
            return;
        }

        event.deferEdit().queue();

        discordExecutor.execute(() -> {
            if (customId.startsWith("ticket:reabrir:")) {
                handleBotaoReabrir(event, customId.substring("ticket:reabrir:".length()));
            } else if (customId.startsWith("ticket:assumir:")) {
                handleBotaoAssumirDirect(event, customId.substring("ticket:assumir:".length()));
            } else if (customId.startsWith(BOTAO_ASSUMIR_PREFIX)) {
                handleBotaoAssumir(event, customId.substring(BOTAO_ASSUMIR_PREFIX.length()));
            } else if (customId.startsWith(BOTAO_RECUSAR_PREFIX)) {
                handleBotaoRecusar(event, customId.substring(BOTAO_RECUSAR_PREFIX.length()));
            }
        });
    }

    @Override
    public void onModalInteraction(@javax.annotation.Nonnull ModalInteractionEvent event) {
        String modalId = event.getModalId();
        log.info("[DISCORD][modal] Submissão recebida. ModalId='{}', Usuário='{}'",
                modalId, event.getUser().getAsTag());

        if (modalId.startsWith("modal:resolver:")) {
            String ticketIdStr = modalId.substring("modal:resolver:".length());
            var solutionVal = event.getValue("solution_text");
            String solutionText = solutionVal != null ? solutionVal.getAsString() : "";

            event.deferReply().queue();

            discordExecutor.execute(() -> {
                try {
                    String result = discordCommandService.resolverChamadoComNota(event.getUser().getId(), ticketIdStr, solutionText);
                    String safeResult = (result != null && !result.isBlank()) ? result : "✅ Chamado processado.";
                    event.getHook().sendMessage(safeResult).queue();
                } catch (Exception ex) {
                    log.error("[DISCORD][modal] Erro ao resolver chamado via modal: {}", ex.getMessage(), ex);
                    event.getHook().sendMessage("❌ Erro ao registrar solução do chamado: " + ex.getMessage()).queue();
                }
            });
        }
    }

    private void handleBotaoReabrir(ButtonInteractionEvent event, String ticketIdStr) {
        String discordUserId = event.getUser().getId();
        try {
            UUID ticketId = UUID.fromString(ticketIdStr);
            br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
            if (ticket != null) {
                String result = discordCommandService.reabrirChamado(discordUserId, ticketIdStr);
                discordTicketPort.reopenTicketChannel(ticket);
                String messageText = (result != null && !result.isBlank()) ? result : "🔄 Chamado Reaberto!";
                event.getHook().sendMessage(messageText).queue();
            } else {
                event.getHook().sendMessage("❌ Chamado não encontrado.").setEphemeral(true).queue();
            }
        } catch (Exception ex) {
            log.error("[DISCORD][botão] Erro ao reabrir chamado #{}", ticketIdStr, ex);
            event.getHook().sendMessage("❌ Erro ao reabrir o chamado: " + ex.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handleBotaoAssumirDirect(ButtonInteractionEvent event, String ticketIdStr) {
        String discordUserId = event.getUser().getId();
        if (!isAdministrator(discordUserId)) {
            event.getHook().sendMessage("🔒 Acesso negado. Apenas técnicos e administradores de TI podem assumir chamados.").setEphemeral(true).queue();
            return;
        }
        String resultMessage = discordCommandService.assumirChamado(discordUserId, ticketIdStr);
        if (resultMessage.startsWith("🔒") || resultMessage.startsWith("❌") || resultMessage.startsWith("⚠️")) {
            event.getHook().sendMessage(resultMessage).setEphemeral(true).queue();
            return;
        }
        event.getHook().sendMessage("👤 <@" + discordUserId + "> assumiu este chamado!").queue();
    }

    private void handleBotaoResolverModal(ButtonInteractionEvent event, String ticketIdStr) {
        String discordUserId = event.getUser().getId();
        if (!isAdministrator(discordUserId)) {
            event.reply("🔒 Acesso negado. Apenas técnicos e administradores de TI podem resolver chamados.").setEphemeral(true).queue();
            return;
        }

        String shortId = ticketIdStr.length() >= 8 ? ticketIdStr.substring(0, 8).toUpperCase() : ticketIdStr;

        TextInput solutionInput = TextInput.create(
                "solution_text", "Nota de Resolução (Markdown)",
                TextInputStyle.PARAGRAPH
        )
        .setPlaceholder("Descreva detalhadamente a solução aplicada ao chamado...")
        .setMinLength(5)
        .setMaxLength(2000)
        .setRequired(true)
        .build();

        Modal modal = Modal.create("modal:resolver:" + ticketIdStr, "Resolver Chamado #" + shortId)
                .addActionRow(solutionInput)
                .build();

        event.replyModal(modal).queue();
    }

    private void handleBotaoAssumir(ButtonInteractionEvent event, String ticketIdStr) {
        String discordUserId = event.getUser().getId();
        if (!isAdministrator(discordUserId)) {
            event.getHook().sendMessage("🔒 Acesso negado. Apenas administradores validados por ID podem interagir com chamados.").setEphemeral(true).queue();
            return;
        }
        String resultMessage = discordCommandService.assumirChamado(discordUserId, ticketIdStr);

        if (resultMessage.startsWith("🔒") || resultMessage.startsWith("❌") || resultMessage.startsWith("⚠️")) {
            event.getHook().sendMessage(resultMessage).setEphemeral(true).queue();
            return;
        }

        desabilitarBotoesDaMensagem(event, resultMessage);
    }

    private void handleBotaoRecusar(ButtonInteractionEvent event, String ticketIdStr) {
        String discordUserId = event.getUser().getId();
        if (!isAdministrator(discordUserId)) {
            event.getHook().sendMessage("🔒 Acesso negado. Apenas administradores validados por ID podem interagir com chamados.").setEphemeral(true).queue();
            return;
        }
        String resultMessage = discordCommandService.recusarChamado(discordUserId, ticketIdStr);

        if (resultMessage.startsWith("🔒") || resultMessage.startsWith("❌")) {
            event.getHook().sendMessage(resultMessage).setEphemeral(true).queue();
            return;
        }

        desabilitarBotoesDaMensagem(event, resultMessage);
    }

    @SuppressWarnings("null")
    private void desabilitarBotoesDaMensagem(ButtonInteractionEvent event, String rodape) {
        List<Button> botoesDessa = List.copyOf(
                event.getMessage().getButtons().stream()
                        .map(Button::asDisabled)
                        .toList());

        event.getHook().editOriginalComponents(ActionRow.of(botoesDessa))
                .setContent(event.getMessage().getContentRaw() + "\n\n" + rodape)
                .queue(
                        ok  -> log.info("[DISCORD][botão] Mensagem editada com botões desabilitados. Rodapé: '{}'", rodape),
                        err -> log.warn("[DISCORD][botão] Falha ao editar mensagem: {}", err.getMessage())
                );
    }

    public static ActionRow criarBotoesDeAcao(UUID ticketId) {
        String idStr = ticketId.toString();
        Button botaoAssumir = Button.primary(BOTAO_ASSUMIR_PREFIX + idStr, "✅ Assumir");
        Button botaoRecusar = Button.danger(BOTAO_RECUSAR_PREFIX + idStr, "❌ Recusar");
        return ActionRow.of(botaoAssumir, botaoRecusar);
    }

    private String sanitizeInput(String input) {
        if (input == null) return "";
        String clean = input.replaceAll("<[^>]*>", "");
        clean = clean.replaceAll("[\\r\\n\\t]", " ");
        if (clean.length() > 100) {
            clean = clean.substring(0, 100);
        }
        return clean.trim();
    }

    private boolean isAdministrator(String discordUserId) {
        if (discordUserId == null || discordUserId.isBlank()) {
            return false;
        }
        if (adminDiscordId != null && adminDiscordId.trim().equals(discordUserId.trim())) {
            return true;
        }
        if (adminIdsRaw != null && !adminIdsRaw.isBlank()) {
            boolean isMatch = java.util.Arrays.stream(adminIdsRaw.split(","))
                    .map(id -> id != null ? id.trim() : "")
                    .anyMatch(id -> id.equals(discordUserId.trim()));
            if (isMatch) return true;
        }
        User usuario = discordCommandService.resolverTecnico(discordUserId);
        return usuario != null;
    }
}
