package com.chatbot.chatbot_backend.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;

import java.io.IOException;
import java.util.Iterator;

@Slf4j
@Service
public class DocumentService {

    public DocumentResponse processFile(MultipartFile file) {
        String extension = FilenameUtils.getExtension(file.getOriginalFilename()).toLowerCase();
        log.debug("Processing file: {} with extension: {}", file.getOriginalFilename(), extension);

        return switch (extension) {
            case "pdf"  -> processPdf(file);
            case "xlsx" -> processExcel(file, false);
            case "xls"  -> processExcel(file, true);
            default     -> throw new IllegalArgumentException(
                    "Formato file non supportato: " + extension + ". Usa PDF, XLSX o XLS."
            );
        };
    }

    private DocumentResponse processPdf(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            int totalPages = document.getNumberOfPages();

            log.debug("PDF processed: {} pages extracted", totalPages);

            return new DocumentResponse(
                    file.getOriginalFilename(),
                    "PDF",
                    text.trim(),
                    totalPages,
                    true
            );
        } catch (IOException e) {
            log.error("Error processing PDF: {}", e.getMessage());
            throw new IllegalStateException("Errore durante la lettura del PDF: " + e.getMessage());
        }
    }

    private DocumentResponse processExcel(MultipartFile file, boolean isLegacy) {
        try (Workbook workbook = isLegacy
                ? new HSSFWorkbook(file.getInputStream())
                : new XSSFWorkbook(file.getInputStream())) {

            StringBuilder extractedText = new StringBuilder();
            int totalSheets = workbook.getNumberOfSheets();

            for (int i = 0; i < totalSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                extractedText.append("=== Foglio: ").append(sheet.getSheetName()).append(" ===\n");

                for (Row row : sheet) {
                    Iterator<Cell> cellIterator = row.cellIterator();
                    StringBuilder rowText = new StringBuilder();

                    while (cellIterator.hasNext()) {
                        Cell cell = cellIterator.next();
                        String cellValue = switch (cell.getCellType()) {
                            case STRING  -> cell.getStringCellValue();
                            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                            case FORMULA -> cell.getCellFormula();
                            default      -> "";
                        };

                        if (!cellValue.isBlank()) {
                            rowText.append(cellValue).append("\t");
                        }
                    }

                    if (!rowText.isEmpty()) {
                        extractedText.append(rowText.toString().trim()).append("\n");
                    }
                }
                extractedText.append("\n");
            }

            log.debug("Excel processed: {} sheets extracted", totalSheets);

            return new DocumentResponse(
                    file.getOriginalFilename(),
                    isLegacy ? "XLS" : "XLSX",
                    extractedText.toString().trim(),
                    totalSheets,
                    true
            );
        } catch (IOException e) {
            log.error("Error processing Excel: {}", e.getMessage());
            throw new IllegalStateException("Errore durante la lettura del file Excel: " + e.getMessage());
        }
    }
}