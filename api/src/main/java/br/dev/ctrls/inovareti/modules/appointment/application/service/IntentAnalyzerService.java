package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.DoctorMatchDto;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisResultDto;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorCatalog;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Serviço de NLP leve responsável pela análise de mensagens de pacientes,
 * filtragem de stop-words/gírias, normalização de especialidades, desambiguação, paginação
 * e decisões de roteamento pós-busca de paciente (routeType, acaoSeguinte, selectedQueue).
 */
@Slf4j
@Service
@Observed
public class IntentAnalyzerService {

    private static final int PAGE_SIZE = 9; // 9 médicos + 1 botão sintético "Ver mais médicos..." (máx 10 itens WhatsApp)
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Set<String> STOP_WORDS = Set.of(
            "ola", "olaa", "oi", "bom", "dia", "boa", "tarde", "noite",
            "agendar", "agendamento", "consulta", "marcar", "com", "quero",
            "por", "favor", "gostaria", "de", "para", "o", "a", "os", "as",
            "um", "uma", "dr", "dra", "doutor", "doutora", "medico", "medica",
            "preciso", "ver", "passar", "clinica"
    );

    private static final Map<String, String> NICKNAME_SPECIALTY_MAP = Map.ofEntries(
            Map.entry("anestesia", "Anestesiologia"),
            Map.entry("anestesiologia", "Anestesiologia"),
            Map.entry("anestesista", "Anestesiologia"),
            Map.entry("gineco", "Ginecologia"),
            Map.entry("ginecologista", "Ginecologia"),
            Map.entry("oftalmo", "Oftalmologia"),
            Map.entry("oftalmologista", "Oftalmologia"),
            Map.entry("cardio", "Cardiologia"),
            Map.entry("cardiologista", "Cardiologia"),
            Map.entry("uro", "Urologia"),
            Map.entry("urologista", "Urologia"),
            Map.entry("pneumo", "Cirurgia Torácica"),
            Map.entry("pneumologista", "Cirurgia Torácica"),
            Map.entry("reumato", "Reumatologia"),
            Map.entry("reumatologista", "Reumatologia"),
            Map.entry("imuno", "Alergia e Imunologia"),
            Map.entry("alergista", "Alergia e Imunologia"),
            Map.entry("alergia", "Alergia e Imunologia"),
            Map.entry("digestivo", "Cirurgia do Aparelho Digestivo"),
            Map.entry("plastica", "Cirurgia Plástica"),
            Map.entry("vascular", "Cirurgia Vascular"),
            Map.entry("toracica", "Cirurgia Torácica"),
            Map.entry("dermato", "Dermatologia"),
            Map.entry("dermatologista", "Dermatologia"),
            Map.entry("orto", "Ortopedia"),
            Map.entry("ortopedista", "Ortopedia"),
            Map.entry("endocrino", "Endocrinologia"),
            Map.entry("endocrinologista", "Endocrinologia"),
            Map.entry("gastro", "Gastroenterologia"),
            Map.entry("gastroenterologista", "Gastroenterologia"),
            Map.entry("hepato", "Hepatologia"),
            Map.entry("hepatologista", "Hepatologia"),
            Map.entry("nefro", "Nefrologia"),
            Map.entry("nefrologista", "Nefrologia"),
            Map.entry("neuro", "Neurologia"),
            Map.entry("neurologista", "Neurologia"),
            Map.entry("nutri", "Nutrição"),
            Map.entry("nutricionista", "Nutrição"),
            Map.entry("odonto", "Odontologia"),
            Map.entry("dentista", "Odontologia"),
            Map.entry("fisio", "Fisioterapia"),
            Map.entry("fisioterapeuta", "Fisioterapia"),
            Map.entry("fono", "Fonoaudiologia"),
            Map.entry("fonoaudiologa", "Fonoaudiologia"),
            Map.entry("psico", "Psicologia"),
            Map.entry("psicologa", "Psicologia"),
            Map.entry("psiquiatra", "Psiquiatria"),
            Map.entry("pediatra", "Pediatria"),
            Map.entry("pediatria", "Pediatria")
    );

    /**
     * Analisa o texto bruto do paciente utilizando a página padrão (página 1).
     */
    public IntentAnalysisResultDto analyzeIntent(String rawInput) {
        return analyzeIntent(rawInput, 1);
    }

    /**
     * Analisa o texto bruto do paciente com suporte a paginação de resultados e decisão de roteamento.
     *
     * @param rawInput Mensagem digitada pelo paciente no WhatsApp
     * @param page Número da página solicitada (1-based)
     * @return IntentAnalysisResultDto contendo intenção, especialidade, médicos, decisão de roteamento e metadados de paginação
     */
    public IntentAnalysisResultDto analyzeIntent(String rawInput, int page) {
        if (rawInput == null || rawInput.isBlank()) {
            return IntentAnalysisResultDto.builder()
                    .rawInput(rawInput)
                    .cleanedInput("")
                    .intent("NAO_RECONHECIDO")
                    .routeType("MENU_POS_CPF")
                    .acaoSeguinte("EXIBIR_MENU_POS_CPF")
                    .selectedQueue(null)
                    .hasAmbiguity(false)
                    .page(1)
                    .pageSize(PAGE_SIZE)
                    .totalMatches(0)
                    .totalPages(0)
                    .hasNextPage(false)
                    .matches(Collections.emptyList())
                    .build();
        }

        String normalized = stripAccents(rawInput.toLowerCase().trim());

        // 1. Verificação de solicitação explícita de Atendimento Humano ou Trigger ITSM
        boolean isExplicitHuman = isExplicitHumanSupportRequest(normalized);

        List<String> tokens = Arrays.stream(normalized.split("[^a-zA-Z0-9]+"))
                .filter(t -> !t.isBlank())
                .filter(t -> !STOP_WORDS.contains(t))
                .collect(Collectors.toList());

        String cleanedText = String.join(" ", tokens);
        log.info("[INTENT-ANALYZER] Entrada: '{}' | Higienizado: '{}' | Página: {}", rawInput, cleanedText, page);

        // Mapear gírias/apelidos de especialidade
        String mappedSpecialty = resolveSpecialtyNickname(tokens);

        // Buscar médicos correspondentes no DoctorCatalog
        List<DoctorCatalog> matchedCatalogs = findMatchingDoctorCatalogs(tokens, mappedSpecialty);

        List<DoctorMatchDto> allMatchDtos = matchedCatalogs.stream()
                .map(this::toDoctorMatchDto)
                .collect(Collectors.toList());

        int totalMatches = allMatchDtos.size();
        boolean hasAmbiguity = totalMatches > 1;

        String intent;
        String routeType;
        String acaoSeguinte;
        String selectedQueue;

        if (isExplicitHuman) {
            intent = "ATENDIMENTO_HUMANO";
            routeType = "INTERNAL";
            acaoSeguinte = "REDIRECIONAR_DESK";
            selectedQueue = "Atendimento Geral";
        } else if (totalMatches == 0) {
            intent = "NAO_RECONHECIDO";
            routeType = "MENU_POS_CPF";
            acaoSeguinte = "EXIBIR_MENU_POS_CPF";
            selectedQueue = null;
        } else if (hasAmbiguity) {
            intent = "DESAMBIGUACAO";
            routeType = "DESAMBIGUACAO";
            acaoSeguinte = "EXIBIR_LISTA_DESAMBIGUACAO";
            selectedQueue = null;
        } else {
            intent = "AGENDAMENTO";
            DoctorMatchDto bestMatch = allMatchDtos.get(0);
            boolean isInternal = Boolean.TRUE.equals(bestMatch.getIsInternal());
            if (isInternal) {
                String queue = bestMatch.getQueue();
                if (queue != null && !queue.isBlank() && !"Atendimento Geral".equalsIgnoreCase(queue.trim())) {
                    routeType = "INTERNAL";
                    acaoSeguinte = "REDIRECIONAR_DESK";
                    selectedQueue = queue.trim();
                } else {
                    routeType = "MENU_POS_CPF";
                    acaoSeguinte = "EXIBIR_MENU_POS_CPF";
                    selectedQueue = null;
                }
            } else {
                routeType = "EXTERNAL";
                acaoSeguinte = "EXIBIR_LINK_EXTERNO";
                selectedQueue = null;
            }
        }

        // SALVAGUARDA ESTRITA: Reservar routeType: "INTERNAL" com selectedQueue: "Atendimento Geral"
        // APENAS se a intenção for explicitamente solicitação de atendente/suporte humano.
        if (!isExplicitHuman && "INTERNAL".equalsIgnoreCase(routeType) && ("Atendimento Geral".equalsIgnoreCase(selectedQueue) || selectedQueue == null)) {
            routeType = "MENU_POS_CPF";
            acaoSeguinte = "EXIBIR_MENU_POS_CPF";
            selectedQueue = null;
        }

        // Se a intenção for NAO_RECONHECIDO ou se routeType for MENU_POS_CPF, garante retorno sanitizado
        if ("NAO_RECONHECIDO".equalsIgnoreCase(intent) || "MENU_POS_CPF".equalsIgnoreCase(routeType)) {
            routeType = "MENU_POS_CPF";
            acaoSeguinte = "EXIBIR_MENU_POS_CPF";
            selectedQueue = null;
        }

        // Lógica de Paginação (Limite de 10 itens do WhatsApp Interactive List)
        int targetPage = Math.max(1, page);
        int totalPages = totalMatches <= 10 ? 1 : (int) Math.ceil((double) totalMatches / PAGE_SIZE);
        int currentPage = Math.min(targetPage, Math.max(1, totalPages));

        List<DoctorMatchDto> paginatedMatches;
        boolean hasNextPage;

        if (totalMatches <= 10) {
            paginatedMatches = new ArrayList<>(allMatchDtos);
            hasNextPage = false;
        } else {
            int startIndex = (currentPage - 1) * PAGE_SIZE;
            int endIndex = Math.min(startIndex + PAGE_SIZE, totalMatches);
            paginatedMatches = new ArrayList<>(allMatchDtos.subList(startIndex, endIndex));
            hasNextPage = currentPage < totalPages;

            if (hasNextPage) {
                // Injeta o 10º item sintético "Ver mais médicos..."
                paginatedMatches.add(DoctorMatchDto.builder()
                        .doctorName("Ver mais médicos...")
                        .specialty(mappedSpecialty != null ? mappedSpecialty : "Mais opções")
                        .isInternal(true)
                        .route("PAGINATION")
                        .isSynthetic(true)
                        .nextPage(currentPage + 1)
                        .build());
            }
        }

        Map<String, Object> interactiveList = null;
        String formattedText = null;
        String externalRedirectMessage = null;
        String modo = "TEXTO";

        if (hasAmbiguity && !paginatedMatches.isEmpty()) {
            modo = "INTERATIVO";
            List<Map<String, String>> rows = new ArrayList<>();
            StringBuilder textSb = new StringBuilder("👨‍⚕️ *Encontramos os seguintes especialistas:*\n\n");

            for (int i = 0; i < paginatedMatches.size(); i++) {
                DoctorMatchDto doc = paginatedMatches.get(i);
                String name = doc.getDoctorName() != null ? doc.getDoctorName().trim() : "Especialista";
                String spec = doc.getSpecialty() != null ? doc.getSpecialty().trim() : "";
                String rowId = String.valueOf(i + 1);

                String title = name.length() > 24 ? name.substring(0, 21) + "..." : name;
                String desc = spec.length() > 72 ? spec.substring(0, 69) + "..." : spec;

                rows.add(Map.of(
                        "id", rowId,
                        "title", title,
                        "description", desc
                ));

                textSb.append(String.format("%d️⃣ *%s* (%s)\n", i + 1, name, spec));
            }

            textSb.append(String.format("\n👉 *Digite o número (1 a %d) ou o nome desejado:*", paginatedMatches.size()));
            formattedText = textSb.toString();

            interactiveList = Map.of(
                    "recipient_type", "individual",
                    "type", "interactive",
                    "interactive", Map.of(
                            "type", "list",
                            "header", Map.of("type", "text", "text", "Especialistas Encontrados"),
                            "body", Map.of("text", "Encontramos mais de um especialista para sua busca. Selecione o médico desejado abaixo:"),
                            "footer", Map.of("text", "Clínica Inovare"),
                            "action", Map.of(
                                    "button", "Ver Médicos",
                                    "sections", List.of(
                                            Map.of(
                                                    "title", "Médicos Disponíveis",
                                                    "rows", rows
                                            )
                                    )
                            )
                    )
            );
        } else if ("EXTERNAL".equalsIgnoreCase(routeType) && !allMatchDtos.isEmpty()) {
            DoctorMatchDto singleDoc = allMatchDtos.get(0);
            String docName = singleDoc.getDoctorName() != null ? singleDoc.getDoctorName().trim() : "Especialista";
            String spec = singleDoc.getSpecialty() != null ? singleDoc.getSpecialty().trim() : "";
            String link = singleDoc.getExternalLink() != null ? singleDoc.getExternalLink().trim() : "";

            if (!link.isBlank()) {
                externalRedirectMessage = String.format(
                        "Para agendar com *%s (%s)*, clique no link abaixo para falar diretamente no WhatsApp:\n\n👉 %s",
                        docName, spec, link
                );
            }
        }

        return IntentAnalysisResultDto.builder()
                .rawInput(rawInput)
                .cleanedInput(cleanedText)
                .intent(intent)
                .extractedSpecialty(mappedSpecialty)
                .hasAmbiguity(hasAmbiguity)
                .routeType(routeType)
                .acaoSeguinte(acaoSeguinte)
                .selectedQueue(selectedQueue)
                .page(currentPage)
                .pageSize(PAGE_SIZE)
                .totalMatches(totalMatches)
                .totalPages(totalPages)
                .hasNextPage(hasNextPage)
                .matches(paginatedMatches)
                .modo(modo)
                .interactiveList(interactiveList)
                .formattedText(formattedText)
                .externalRedirectMessage(externalRedirectMessage)
                .build();
    }

    private boolean isExplicitHumanSupportRequest(String normalizedInput) {
        if (normalizedInput == null || normalizedInput.isBlank()) {
            return false;
        }
        String input = normalizedInput.toLowerCase();
        return input.contains("atendente")
                || input.contains("atendimento humano")
                || input.contains("falar com atendente")
                || input.contains("falar com a secretaria")
                || input.contains("secretaria")
                || input.contains("falar com recepcao")
                || input.contains("recepcao")
                || input.contains("humano")
                || input.contains("transbordo")
                || input.startsWith("confirm_")
                || input.startsWith("alter_")
                || input.startsWith("ver_agenda_");
    }

    private String resolveSpecialtyNickname(List<String> tokens) {
        for (String token : tokens) {
            if (NICKNAME_SPECIALTY_MAP.containsKey(token)) {
                return NICKNAME_SPECIALTY_MAP.get(token);
            }
        }
        return null;
    }

    private List<DoctorCatalog> findMatchingDoctorCatalogs(List<String> tokens, String mappedSpecialty) {
        Set<DoctorCatalog> matches = new LinkedHashSet<>();

        for (DoctorCatalog catalog : DoctorCatalog.values()) {
            // Se especialidade foi mapeada, checa igualdade
            if (mappedSpecialty != null && catalog.getSpecialty().equalsIgnoreCase(mappedSpecialty)) {
                matches.add(catalog);
                continue;
            }

            // Checa interseção de tokens
            for (String token : tokens) {
                if (token.length() >= 3 && catalog.getTokens().contains(token)) {
                    matches.add(catalog);
                }
            }
        }

        return new ArrayList<>(matches);
    }

    private DoctorMatchDto toDoctorMatchDto(DoctorCatalog catalog) {
        boolean isInternal = "DESK".equalsIgnoreCase(catalog.getRoute());
        String externalLink = null;
        String externalPhone = null;

        if (!isInternal) {
            try {
                if (catalog == DoctorCatalog.EXAMES_IMAGEM) {
                    String message = "Olá! Gostaria de informações/agendamento sobre Exames de Imagem.";
                    String encodedMsg = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                    externalLink = "https://wa.me/554230262633?text=" + encodedMsg;
                    externalPhone = "(42) 3026-2633";
                } else {
                    String message = "Olá! Gostaria de agendar atendimento com " + catalog.getDoctorName() + " (" + catalog.getSpecialty() + ").";
                    String encodedMsg = java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                    externalLink = "https://wa.me/554230262600?text=" + encodedMsg;
                    externalPhone = "(42) 3026-2600";
                }
            } catch (Exception ignored) {}
        }

        return DoctorMatchDto.builder()
                .doctorName(catalog.getDoctorName())
                .specialty(catalog.getSpecialty())
                .route(catalog.getRoute())
                .queue(catalog.getQueue())
                .isInternal(isInternal)
                .externalLink(externalLink)
                .externalPhone(externalPhone)
                .isSynthetic(false)
                .build();
    }

    private String stripAccents(String src) {
        if (src == null) return "";
        String normalized = Normalizer.normalize(src, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
    }
}
