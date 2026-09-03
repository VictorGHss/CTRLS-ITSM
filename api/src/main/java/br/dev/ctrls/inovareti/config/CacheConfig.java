package br.dev.ctrls.inovareti.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Configuração de Cache Local de Alta Performance com Caffeine.
 *
 * <p>Define o {@link CaffeineCacheManager} como CacheManager primário da aplicação
 * com política de expiração após escrita de 30 minutos (TTL=30m), capacidade máxima
 * de 5.000 entradas por cache e coleta de métricas (recordStats) para observabilidade.</p>
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_DOCTOR_CATALOG_ANALYSIS = "doctorCatalogAnalysis";
    public static final String CACHE_INTENT_ANALYSIS_PROCESS = "intentAnalysisProcess";
    public static final String CACHE_DOCTOR_TOP_MATCHES = "doctorTopMatches";
    public static final String CACHE_DOCTOR_MAPPING_BY_PROFISSIONAL_ID = "doctorMappingByProfissionalId";
    public static final String CACHE_DOCTOR_MAPPINGS_LIST = "doctorMappingsList";
    public static final String CACHE_FEEGOW_PROFESSIONALS_LIST = "feegowProfessionalsList";
    public static final String CACHE_FEEGOW_PROFESSIONAL_NAME = "feegowProfessionalName";
    public static final String CACHE_CONTA_AZUL_SUMMARY = "contaAzulSummary";
    public static final String CACHE_FEEGOW_LOCKS = "feegow-locks";
    public static final String CACHE_PATIENT_ACCESS_INFO = "patientAccessInfo";

    @Bean
    @Primary
    public CacheManager cacheManager() {
        log.info("[CACHE] Inicializando CaffeineCacheManager local (TTL=30min, maxSize=5000, recordStats=true)");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(5000)
                .recordStats());
        return cacheManager;
    }
}
