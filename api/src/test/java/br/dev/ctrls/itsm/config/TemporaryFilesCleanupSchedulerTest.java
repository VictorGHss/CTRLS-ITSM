package br.dev.ctrls.itsm.config;

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
        CleanupProperties cleanupProperties = new CleanupProperties();
        cleanupProperties.setTempRetentionHours(24);
        cleanupProperties.setOrphanRetentionDays(30);

        TemporaryFilesCleanupScheduler scheduler = new TemporaryFilesCleanupScheduler(cleanupProperties);
        ReflectionTestUtils.setField(scheduler, "backupTempDir", tempDir.toString());
        ReflectionTestUtils.setField(scheduler, "uploadDir", tempDir.toString());

        // Cria arquivo antigo (48 horas atrás)
        Path oldSql = tempDir.resolve("backup_old.sql");
        Files.writeString(oldSql, "DUMP SQL ANTIGO");
        Files.setLastModifiedTime(oldSql, FileTime.from(Instant.now().minus(48, ChronoUnit.HOURS)));

        // Cria arquivo recente (1 hora atrás)
        Path newSql = tempDir.resolve("backup_recent.sql");
        Files.writeString(newSql, "DUMP SQL RECENTE");
        Files.setLastModifiedTime(newSql, FileTime.from(Instant.now().minus(1, ChronoUnit.HOURS)));

        // Cria backup ZIP recente (5 dias atrás - deve ser preservado pela retenção de 30 dias)
        Path zipRecent = tempDir.resolve("backup_20260820_030000.zip");
        Files.writeString(zipRecent, "ZIP RECENTE");
        Files.setLastModifiedTime(zipRecent, FileTime.from(Instant.now().minus(5, ChronoUnit.DAYS)));

        // Cria backup ZIP muito antigo (45 dias atrás - deve ser deletado pela retenção de 30 dias)
        Path zipOld = tempDir.resolve("backup_20260710_030000.zip");
        Files.writeString(zipOld, "ZIP EXPIRADO");
        Files.setLastModifiedTime(zipOld, FileTime.from(Instant.now().minus(45, ChronoUnit.DAYS)));

        // Executa a limpeza
        scheduler.executePreventiveCleanup();

        // Validações
        assertFalse(Files.exists(oldSql), "O dump SQL temporário com mais de 24h deveria ter sido deletado");
        assertTrue(Files.exists(newSql), "O dump SQL recente deveria ter sido preservado");
        assertTrue(Files.exists(zipRecent), "O backup ZIP de 5 dias atrás deveria ter sido preservado pela regra de 30 dias");
        assertFalse(Files.exists(zipOld), "O backup ZIP expirado com mais de 30 dias deveria ter sido deletado");
    }

    @Test
    @DisplayName("Deve lidar graciosamente quando os diretórios configurados não existem")
    void shouldHandleNonExistentDirectoriesGracefully() {
        CleanupProperties cleanupProperties = new CleanupProperties();
        TemporaryFilesCleanupScheduler scheduler = new TemporaryFilesCleanupScheduler(cleanupProperties);
        ReflectionTestUtils.setField(scheduler, "backupTempDir", "/caminho/inexistente/backup/12345");
        ReflectionTestUtils.setField(scheduler, "uploadDir", "/caminho/inexistente/upload/12345");

        assertDoesNotThrow(scheduler::executePreventiveCleanup,
                "A rotina de limpeza não deve lançar exceções para diretórios inexistentes");
    }
}
