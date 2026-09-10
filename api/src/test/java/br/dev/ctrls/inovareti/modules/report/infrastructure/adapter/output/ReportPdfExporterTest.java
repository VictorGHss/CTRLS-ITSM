package br.dev.ctrls.inovareti.modules.report.infrastructure.adapter.output;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.dev.ctrls.inovareti.modules.report.application.dto.OutflowReportRowDTO;

class ReportPdfExporterTest {

    private final ReportPdfExporter exporter = new ReportPdfExporter();

    @Test
    @DisplayName("Deve gerar PDF em formato Paisagem sem erros")
    void shouldExportInventoryExitsToPdfSuccessfully() {
        List<OutflowReportRowDTO> rows = List.of(
                new OutflowReportRowDTO(
                        "Consumíveis",
                        "Tinta GT53 Preta",
                        1,
                        "Anne Chrystine Ribeiro",
                        "Matriz",
                        "Recepção",
                        new BigDecimal("75.00"),
                        LocalDateTime.of(2026, 8, 10, 8, 19)
                ),
                new OutflowReportRowDTO(
                        "Consumíveis",
                        "Senhas de Internet",
                        100,
                        "Victor Gabriel Hass",
                        "Sede",
                        "TI",
                        new BigDecimal("10.00"),
                        LocalDateTime.of(2026, 8, 13, 10, 10)
                ),
                new OutflowReportRowDTO(
                        "Consumíveis",
                        "Toner TN-1060",
                        1,
                        "Orientação Oftalmologia (Laís)",
                        "2° Andar Direita",
                        "Oftalmologia",
                        new BigDecimal("49.00"),
                        LocalDateTime.of(2026, 8, 20, 16, 57)
                ),
                new OutflowReportRowDTO(
                        "Consumíveis",
                        "Senhas de Internet",
                        100,
                        "Gabriela Alves dos Santos",
                        "3° Andar Ginecologia",
                        "R-3° Andar-Ginecologia",
                        new BigDecimal("10.00"),
                        LocalDateTime.of(2026, 8, 28, 11, 28)
                )
        );

        byte[] pdfBytes = exporter.exportInventoryExitsToPdf(rows);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        // Assinatura do cabeçalho PDF (%PDF-)
        assertEquals("%PDF-", new String(pdfBytes, 0, 5));
    }

    private void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
