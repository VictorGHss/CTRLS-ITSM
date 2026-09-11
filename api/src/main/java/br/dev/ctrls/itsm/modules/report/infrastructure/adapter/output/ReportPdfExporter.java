package br.dev.ctrls.itsm.modules.report.infrastructure.adapter.output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import br.dev.ctrls.itsm.modules.report.application.dto.OutflowReportRowDTO;
import br.dev.ctrls.itsm.modules.report.domain.port.output.ReportPdfExporterPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de infraestrutura especializado em relatórios PDF físicos.
 * Implementa o contrato puro Java do domínio utilizando iText/OpenPDF.
 */
@Component
@Slf4j
public class ReportPdfExporter implements ReportPdfExporterPort {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    @Override
    public byte[] exportInventoryExitsToPdf(List<OutflowReportRowDTO> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Orientação em paisagem (Landscape) para acomodar as 8 colunas sem quebras indevidas
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            java.awt.Color inovareColor = new java.awt.Color(254, 181, 108);
            java.awt.Color borderColor = new java.awt.Color(226, 232, 240);
            java.awt.Color alternateRowColor = new java.awt.Color(249, 250, 251);

            addLogo(document);
            addTitle(document, inovareColor);

            String periodStr = resolvePeriodText(rows);

            com.lowagie.text.Font subFont = FontFactory.getFont(
                    FontFactory.HELVETICA,
                    9.5f,
                    com.lowagie.text.Font.NORMAL,
                    new java.awt.Color(71, 85, 105));

            Paragraph subtitle = new Paragraph(
                    "Relatório de Saídas - Período: " + safe(periodStr)
                            + "    |    Gerado em: " + DATE_FORMATTER.format(LocalDateTime.now()),
                    subFont);
            subtitle.setSpacingAfter(10f);
            document.add(subtitle);

            PdfPTable table = createTableWithHeader(inovareColor);

            com.lowagie.text.Font cellFont = FontFactory.getFont(
                    FontFactory.HELVETICA,
                    8.5f,
                    com.lowagie.text.Font.NORMAL,
                    java.awt.Color.BLACK);
            com.lowagie.text.Font cellFontBold = FontFactory.getFont(
                    FontFactory.HELVETICA_BOLD,
                    8.5f,
                    com.lowagie.text.Font.BOLD,
                    java.awt.Color.BLACK);

            BigDecimal tableTotalValue = BigDecimal.ZERO;
            int totalItems = 0;
            int rowIndex = 0;

            for (OutflowReportRowDTO row : rows) {
                java.awt.Color rowBg = (rowIndex % 2 == 1) ? alternateRowColor : java.awt.Color.WHITE;
                rowIndex++;

                String requester = sanitizeForPdf(row.requester());
                String sector = sanitizeForPdf(row.userSector());
                String location = sanitizeForPdf(row.userLocation());

                int qty = Optional.ofNullable(row.quantity()).orElse(0);
                qty = Math.abs(qty);
                BigDecimal totalPrice = Optional.ofNullable(row.totalPrice()).orElse(BigDecimal.ZERO);

                tableTotalValue = tableTotalValue.add(totalPrice);
                totalItems += qty;

                String tipo = sanitizeForPdf(row.itemType());
                String item = sanitizeForPdf(row.item());
                String qtd = String.valueOf(qty);
                String priceStr = sanitizeForPdf(CURRENCY_FORMATTER.format(totalPrice));
                String date = sanitizeForPdf(row.deliveryDate() != null ? row.deliveryDate().format(DATE_FORMATTER) : null);

                table.addCell(createCell(tipo, cellFont, Element.ALIGN_LEFT, rowBg, borderColor));
                table.addCell(createCell(item, cellFont, Element.ALIGN_LEFT, rowBg, borderColor));
                table.addCell(createCell(qtd, cellFont, Element.ALIGN_CENTER, rowBg, borderColor));
                table.addCell(createCell(requester, cellFont, Element.ALIGN_LEFT, rowBg, borderColor));
                table.addCell(createCell(location, cellFont, Element.ALIGN_LEFT, rowBg, borderColor));
                table.addCell(createCell(sector, cellFont, Element.ALIGN_LEFT, rowBg, borderColor));
                table.addCell(createCell(priceStr, cellFont, Element.ALIGN_RIGHT, rowBg, borderColor));

                PdfPCell c8 = createCell(date, cellFont, Element.ALIGN_CENTER, rowBg, borderColor);
                c8.setNoWrap(true);
                table.addCell(c8);
            }

            if (!rows.isEmpty()) {
                java.awt.Color totalBg = new java.awt.Color(243, 244, 246);

                PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", cellFontBold));
                totalLabel.setColspan(6);
                totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalLabel.setPadding(5f);
                totalLabel.setBackgroundColor(totalBg);
                totalLabel.setBorderColor(borderColor);
                totalLabel.setBorderWidthTop(1.5f);
                table.addCell(totalLabel);

                PdfPCell totalValueCell = new PdfPCell(new Phrase(CURRENCY_FORMATTER.format(tableTotalValue), cellFontBold));
                totalValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totalValueCell.setPadding(5f);
                totalValueCell.setBackgroundColor(totalBg);
                totalValueCell.setBorderColor(borderColor);
                totalValueCell.setBorderWidthTop(1.5f);
                table.addCell(totalValueCell);

                PdfPCell empty = new PdfPCell(new Phrase(""));
                empty.setPadding(5f);
                empty.setBackgroundColor(totalBg);
                empty.setBorderColor(borderColor);
                empty.setBorderWidthTop(1.5f);
                table.addCell(empty);
            }

            document.add(table);

            Paragraph resumoTitle = new Paragraph("Resumo do Período", cellFontBold);
            resumoTitle.setSpacingBefore(10f);
            document.add(resumoTitle);

            Paragraph resumo = new Paragraph(
                    "Total de Itens Baixados: " + totalItems
                            + "    |    Valor Total do Consumo: " + CURRENCY_FORMATTER.format(tableTotalValue),
                    cellFont);
            document.add(resumo);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            log.error("Erro ao gerar PDF profissional de saídas", e);
            if (document.isOpen()) {
                document.close();
            }
            throw new RuntimeException("Failed to generate professional PDF report", e);
        }
    }

    private void addLogo(Document document) {
        try {
            Image logo = null;
            try (java.io.InputStream logoStream = ReportPdfExporter.class.getResourceAsStream("/images/logo.png")) {
                if (logoStream != null) {
                    byte[] bytes = logoStream.readAllBytes();
                    logo = Image.getInstance(bytes);
                } else {
                    try {
                        java.net.URL url = java.net.URI
                                .create("https://inovare.med.br/wp-content/uploads/2023/01/Logo.png")
                                .toURL();
                        try (java.io.InputStream is = url.openStream()) {
                            byte[] bytes = is.readAllBytes();
                            logo = Image.getInstance(bytes);
                        }
                    } catch (IOException | com.lowagie.text.BadElementException ex) {
                        log.warn("Logo não encontrada em resources e falha ao buscar URL pública: {}", ex.getMessage());
                    }
                }
            }

            if (logo != null) {
                logo.scaleToFit(140f, 60f);
                logo.setAlignment(Image.ALIGN_LEFT);
                document.add(logo);
            }
        } catch (IOException | DocumentException e) {
            log.warn("Erro ao inserir logo no PDF: {}", e.getMessage());
        }
    }

    private void addTitle(Document document, java.awt.Color inovareColor) throws DocumentException {
        com.lowagie.text.Font titleFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD,
                16,
                com.lowagie.text.Font.BOLD,
                inovareColor);
        Paragraph title = new Paragraph("Inovare Serviços de Saúde", titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        document.add(title);
    }

    private String resolvePeriodText(List<OutflowReportRowDTO> rows) {
        LocalDate periodStart = null;
        LocalDate periodEnd = null;

        for (OutflowReportRowDTO row : rows) {
            if (row.deliveryDate() != null) {
                LocalDate date = row.deliveryDate().toLocalDate();
                if (periodStart == null || date.isBefore(periodStart)) {
                    periodStart = date;
                }
                if (periodEnd == null || date.isAfter(periodEnd)) {
                    periodEnd = date;
                }
            }
        }

        if (periodStart != null && periodEnd != null) {
            DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return dateOnly.format(periodStart) + " - " + dateOnly.format(periodEnd);
        }

        return "-";
    }

    private PdfPTable createTableWithHeader(java.awt.Color inovareColor) throws DocumentException {
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100f);
        // Distribuição otimizada das 8 colunas para orientação paisagem (A4 Landscape = 782pt imprimíveis)
        table.setWidths(new float[] { 9f, 22f, 7f, 16f, 12f, 12f, 10f, 12f });
        table.setSpacingBefore(6f);
        table.setHeaderRows(1);

        com.lowagie.text.Font headerFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD,
                9f,
                com.lowagie.text.Font.BOLD,
                java.awt.Color.WHITE);

        String[] headers = { "Tipo de Item", "Item", "Qtd Entregue", "Quem Solicitou", "Local do Usuário", "Setor do Usuário", "Preço Total", "Data da Entrega" };
        int[] headerAligns = {
            Element.ALIGN_LEFT,
            Element.ALIGN_LEFT,
            Element.ALIGN_CENTER,
            Element.ALIGN_LEFT,
            Element.ALIGN_LEFT,
            Element.ALIGN_LEFT,
            Element.ALIGN_RIGHT,
            Element.ALIGN_CENTER
        };

        for (int i = 0; i < headers.length; i++) {
            PdfPCell hd = new PdfPCell(new Phrase(headers[i], headerFont));
            hd.setBackgroundColor(inovareColor);
            hd.setBorderColor(new java.awt.Color(230, 150, 70));
            hd.setBorderWidth(0.5f);
            hd.setPaddingTop(5f);
            hd.setPaddingBottom(5f);
            hd.setPaddingLeft(4f);
            hd.setPaddingRight(4f);
            hd.setHorizontalAlignment(headerAligns[i]);
            table.addCell(hd);
        }

        return table;
    }

    private PdfPCell createCell(String text, com.lowagie.text.Font font, int alignment, java.awt.Color bg, java.awt.Color borderColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPaddingTop(4.5f);
        cell.setPaddingBottom(4.5f);
        cell.setPaddingLeft(4f);
        cell.setPaddingRight(4f);
        cell.setHorizontalAlignment(alignment);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(borderColor);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private String sanitizeForPdf(String value) {
        if (value == null) {
            return "-";
        }

        String out = value;
        out = out.replace("→", "->");
        out = out.replace("—", "-");
        out = out.replace("–", "-");
        out = out.replace("…", "...");
        out = out.replace("•", "-");
        out = out.replace("\u2018", "'");
        out = out.replace("\u2019", "'");
        out = out.replace("\u201C", "\"");
        out = out.replace("\u201D", "\"");
        return out;
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }
}
