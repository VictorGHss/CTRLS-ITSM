package br.dev.ctrls.inovareti.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TemporaryFilesCleanupSchedulerTest {

    @Test
    @DisplayName("Deve remover arquivos antigos com mais de 24h e preservar arquivos recentes")
    void shouldDeleteOldFilesAndKeepNewFiles(@TempDir Path tempDir) throws IOException {
        TemporaryFilesCleanupScheduler scheduler = new TemporaryFilesCleanupScheduler();
        ReflectionTestUtils.setField(scheduler, "tempRetentionHours", 24);
        ReflectionTestUtils.setField(scheduler, "orphanRetentionDays", 30);
        ReflectionTestUtils.setField(scheduler, "backupTempDir", tempDir.toString());
        ReflectionTestUtils.setField(scheduler, "uploadDir", tempDir.toString());

        // Cria arquivo antigo (48 horas atrás)
        Path oldFile = tempDir.resolve("backup_old.sql");
        Files.writeString(oldFile, "DUMP SQL ANTIGO");
        Files.setLastModifiedTime(oldFile, FileTime.from(Instant.now().minus(48, ChronoUnit.HOURS)));

        // Cria arquivo recente (1 hora atrás)
        Path newFile = tempDir.resolve("backup_recent.sql");
        Files.writeString(newFile, "DUMP SQL RECENTE");
        Files.setLastModifiedTime(newFile, FileTime.from(Instant.now().minus(1, ChronoUnit.HOURS)));

        // Executa a limpeza
        scheduler.executePreventiveCleanup();

        // Validações
        assertFalse(Files.exists(oldFile), "O arquivo antigo com mais de 24h deveria ter sido deletado");
        assertTrue(Files.exists(newFile), "O arquivo recente deveria ter sido preservado");
    }

    @Test
    @DisplayName("Deve lidar graciosamente quando os diretórios configurados não existem")
    void shouldHandleNonExistentDirectoriesGracefully() {
        TemporaryFilesCleanupScheduler scheduler = new TemporaryFilesCleanupScheduler();
        ReflectionTestUtils.setField(scheduler, "tempRetentionHours", 24);
        ReflectionTestUtils.setField(scheduler, "orphanRetentionDays", 30);
        ReflectionTestUtils.setField(scheduler, "backupTempDir", "/caminho/inexistente/backup/12345");
        ReflectionTestUtils.setField(scheduler, "uploadDir", "/caminho/inexistente/upload/12345");

        assertDoesNotThrow(scheduler::executePreventiveCleanup,
                "A rotina de limpeza não deve lançar exceções para diretórios inexistentes");
    }
}
