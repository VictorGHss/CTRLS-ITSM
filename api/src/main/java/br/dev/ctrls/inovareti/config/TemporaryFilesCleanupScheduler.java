package br.dev.ctrls.inovareti.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agendador responsável pela limpeza preventiva de arquivos temporários, órfãos e dumps de backup
 * para evitar o esgotamento de espaço em disco (SSD/HDD) no ambiente do servidor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class TemporaryFilesCleanupScheduler {

    private final CleanupProperties cleanupProperties;

    @Value("${app.backup.temp-dir:/app/backups/temp}")
    private String backupTempDir;

    @Value("${file.upload-dir:/app/uploads}")
    private String uploadDir;

    /**
     * Executa a rotina de limpeza preventiva todos os dias às 03:30 da madrugada.
     */
    @Scheduled(cron = "${app.cleanup.cron:0 30 3 * * ?}")
    public void executePreventiveCleanup() {
        log.info("[CLEANUP-STORAGE] Iniciando rotina preventiva diária de limpeza de arquivos temporários e órfãos.");

        AtomicInteger totalDeletedFiles = new AtomicInteger(0);
        AtomicLong totalFreedBytes = new AtomicLong(0L);

        List<Path> targetDirectories = resolveTargetDirectories();

        int retentionHours = cleanupProperties != null ? cleanupProperties.getTempRetentionHours() : 24;

        for (Path directory : targetDirectories) {
            if (Files.exists(directory) && Files.isDirectory(directory)) {
                cleanDirectory(directory, Duration.ofHours(retentionHours), totalDeletedFiles, totalFreedBytes);
            }
        }

        // Limpeza de arquivos temporários do sistema no /tmp ou java.io.tmpdir
        cleanSystemTempDirectory(totalDeletedFiles, totalFreedBytes);

        double freedMegabytes = totalFreedBytes.get() / (1024.0 * 1024.0);
        log.info("[CLEANUP-STORAGE] Rotina de limpeza finalizada com sucesso. Arquivos expurgados: {}, Espaço liberado: {:.2f} MB.",
                totalDeletedFiles.get(), freedMegabytes);
    }

    private List<Path> resolveTargetDirectories() {
        List<Path> dirs = new ArrayList<>();

        if (backupTempDir != null && !backupTempDir.isBlank()) {
            dirs.add(Paths.get(backupTempDir));
        }

        // Diretórios de uploads e subdiretórios temporários
        if (uploadDir != null && !uploadDir.isBlank()) {
            Path baseUpload = Paths.get(uploadDir);
            dirs.add(baseUpload.resolve("temp"));
            dirs.add(baseUpload.resolve("tmp"));
            dirs.add(baseUpload.resolve("chunks"));
        }

        // Diretórios alternativos comuns em produção e desenvolvimento
        dirs.add(Paths.get("/mnt/data/uploads/temp"));
        dirs.add(Paths.get("/mnt/data/backups/temp"));
        dirs.add(Paths.get("uploads/temp"));
        dirs.add(Paths.get("target/test-backups"));

        return dirs;
    }

    private void cleanDirectory(Path directory, Duration maxAge, AtomicInteger deletedCount, AtomicLong freedBytes) {
        log.info("[CLEANUP-STORAGE] Verificando diretório: {}", directory.toAbsolutePath());
        Instant cutoffTime = Instant.now().minus(maxAge);

        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                  .forEach(filePath -> {
                      try {
                          BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
                          Instant lastModified = attrs.lastModifiedTime().toInstant();

                          if (lastModified.isBefore(cutoffTime)) {
                              long fileSize = attrs.size();
                              if (Files.deleteIfExists(filePath)) {
                                  deletedCount.incrementAndGet();
                                  freedBytes.addAndGet(fileSize);
                                  log.debug("[CLEANUP-STORAGE] Arquivo temporário removido: {} ({} bytes)", filePath.getFileName(), fileSize);
                              }
                          }
                      } catch (IOException e) {
                          log.warn("[CLEANUP-STORAGE] Falha não-bloqueante ao verificar/excluir arquivo {}: {}", filePath, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            log.warn("[CLEANUP-STORAGE] Erro ao iterar arquivos no diretório {}: {}", directory, e.getMessage());
        }
    }

    private void cleanSystemTempDirectory(AtomicInteger deletedCount, AtomicLong freedBytes) {
        String sysTemp = System.getProperty("java.io.tmpdir");
        if (sysTemp == null || sysTemp.isBlank()) {
            return;
        }

        Path tempPath = Paths.get(sysTemp);
        if (!Files.exists(tempPath) || !Files.isDirectory(tempPath)) {
            return;
        }

        int retentionHours = cleanupProperties != null ? cleanupProperties.getTempRetentionHours() : 24;
        Instant cutoffTime = Instant.now().minus(Duration.ofHours(retentionHours));

        try (Stream<Path> stream = Files.list(tempPath)) {
            stream.filter(Files::isRegularFile)
                  .filter(this::isApplicationTempFile)
                  .forEach(filePath -> {
                      try {
                          BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
                          if (attrs.lastModifiedTime().toInstant().isBefore(cutoffTime)) {
                              long size = attrs.size();
                              if (Files.deleteIfExists(filePath)) {
                                  deletedCount.incrementAndGet();
                                  freedBytes.addAndGet(size);
                                  log.debug("[CLEANUP-STORAGE] Arquivo temporário de sistema removido: {}", filePath.getFileName());
                              }
                          }
                      } catch (IOException e) {
                          log.warn("[CLEANUP-STORAGE] Não foi possível remover temp de sistema {}: {}", filePath, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            log.warn("[CLEANUP-STORAGE] Erro ao listar diretório temp de sistema: {}", e.getMessage());
        }
    }

    private boolean isApplicationTempFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.startsWith("inovare_")
                || name.startsWith("backup_")
                || name.startsWith("report_")
                || name.endsWith(".part")
                || (name.endsWith(".tmp") && (name.contains("pg") || name.contains("sql") || name.contains("zip")));
    }
}
