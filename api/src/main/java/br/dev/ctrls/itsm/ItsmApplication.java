package br.dev.ctrls.itsm;

import java.util.TimeZone;
import jakarta.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.annotation.EnableRetry;

import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableRetry
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class ItsmApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItsmApplication.class, args);
    }

    @PostConstruct
    public void init() {
        // Força o fuso horário da aplicação para GMT-3 Brasília
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }
}