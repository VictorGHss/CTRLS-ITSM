package br.dev.ctrls.inovareti.modules.access.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de configuração do módulo de controle de acesso do paciente.
 * Comentários em PT-BR pelas Regras de Ouro.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.access")
public class AccessProperties {

    /**
     * Chave secreta HMAC para geração e validação dos Magic Tokens dos pacientes.
     */
    private String magicTokenSecret = "inovare_magic_access_token_secret_key_2026";
}
