package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.access.infrastructure.config.GerAcessoProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Optional;

/**
 * Adaptador de infraestrutura GerAcessoRestClientAdapter.
 * Realiza as requisições HTTP locais para a API local da GerAcesso física.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GerAcessoRestClientAdapter implements GerAcessoClientPort {

    private final GerAcessoProperties gerAcessoProperties;
    private RestClient restClient;

    @PostConstruct
    public void init() {
        log.info("[GerAcesso-Adapter] Inicializando cliente RestClient apontando para: {}", gerAcessoProperties.getUrl());
        
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
                
        org.springframework.http.client.JdkClientHttpRequestFactory factory = 
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(gerAcessoProperties.getUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public Optional<GerAcessoResponse> registerAccess(GerAcessoRequest request) {
        log.info("[GerAcesso-Adapter] Realizando requisição POST para cadastrar visitante CPF: {}", maskCpf(request.cpf()));
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String token = gerAcessoProperties.getToken();
                // Garante o formato 'Bearer ' caso já não tenha sido inserido na variável
                String authHeader = (token != null && token.trim().startsWith("Bearer ")) 
                        ? token.trim() 
                        : "Bearer " + (token != null ? token.trim() : "");

                ResponseEntity<GerAcessoResponse> response = restClient.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authHeader)
                        .body(request)
                        .retrieve()
                        .toEntity(GerAcessoResponse.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.info("[GerAcesso-Adapter] Cadastro concluído (tentativa {}). Resposta: status={}, mensagem={}, credencial={}, localizador={}", 
                            attempt, response.getBody().status(), response.getBody().message(), response.getBody().credential(), response.getBody().locator());
                    return Optional.of(response.getBody());
                }

                log.warn("[GerAcesso-Adapter] Servidor GerAcesso retornou status HTTP: {} (tentativa {}/{})", 
                        response.getStatusCode(), attempt, maxAttempts);
            } catch (org.springframework.web.client.RestClientResponseException rre) {
                log.error("[GerAcesso-Adapter] Erro HTTP {} na requisição para GerAcesso (tentativa {}/{}). CPF={}. Body da resposta: {}", 
                        rre.getStatusCode(), attempt, maxAttempts, maskCpf(request.cpf()), rre.getResponseBodyAsString());
            } catch (Exception ex) {
                log.error("[GerAcesso-Adapter] Erro na requisição HTTP para GerAcesso (tentativa {}/{}). CPF={}. Erro: {}", 
                        attempt, maxAttempts, maskCpf(request.cpf()), ex.getMessage());
            }

            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private String maskCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return "N/A";
        }
        String clean = cpf.replaceAll("\\D", "");
        if (clean.length() == 11) {
            return "***." + clean.substring(3, 6) + ".***-" + clean.substring(9, 11);
        }
        return "***";
    }
}
