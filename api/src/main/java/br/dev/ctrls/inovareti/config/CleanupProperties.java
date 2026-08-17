package br.dev.ctrls.inovareti.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Propriedades de configuração para rotinas preventivas de limpeza de arquivos temporários e órfãos.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cleanup")
public class CleanupProperties {

    private boolean enabled = true;
    private String cron = "0 30 3 * * ?";
    private int tempRetentionHours = 24;
    private int orphanRetentionDays = 30;
}
