package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.gestaods.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Propriedades de configuração para integração com o software médico Gestão DS.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.gestaods")
public class GestaoDsProperties {

    /**
     * URL base da API do Gestão DS (padrão: https://apidev.gestaods.com.br).
     */
    private String baseUrl = "https://apidev.gestaods.com.br";

    /**
     * Token de integração da clínica gerado no Gestão DS.
     */
    private String token;

    /**
     * Flag para habilitar ou desabilitar a integração.
     */
    private boolean enabled = true;

    /**
     * Flag indicando se deve utilizar os endpoints com prefixo de desenvolvimento (/api/dev-...).
     */
    private boolean devMode = false;

    /**
     * Timeout de conexão em milissegundos (padrão: 5000ms).
     */
    private int connectTimeoutMs = 5000;

    /**
     * Timeout de leitura em milissegundos (padrão: 10000ms).
     */
    private int readTimeoutMs = 10000;
}
