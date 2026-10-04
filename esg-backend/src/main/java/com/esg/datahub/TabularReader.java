package com.esg.datahub;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Reads the first sheet of an .xlsx file (Apache POI) or a .csv file (OpenCSV) as rows of strings (TRD §8.1).
 * Excel dates become ISO dates and numbers keep their full precision, so both formats parse the same way.
 */
public final class TabularReader {

    public static final int MAX_ROWS = 5000;

    private TabularReader() {}

    public static List<List<String>> read(InputStream in, String format) throws IOException {
        return "xlsx".equals(format) ? readXlsx(in) : readCsv(in);
    }

    private static List<List<String>> readCsv(InputStream in) throws IOException {
        try (CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<List<String>> rows = new ArrayList<>();
            for (String[] line : reader.readAll()) {
                List<String> row = new ArrayList<>();
                for (String cell : line) {
                    row.add(cell == null ? "" : cell.replace("﻿", "").trim());
                }
                rows.add(row);
                if (rows.size() > MAX_ROWS + 1) {
                    break;
                }
            }
            return rows;
        } catch (CsvException e) {
            throw new IOException("Unreadable CSV: " + e.getMessage(), e);
        }
    }

    private static List<List<String>> readXlsx(InputStream in) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            int width = 0;
            for (Row row : sheet) {
                width = Math.max(width, row.getLastCellNum());
            }
            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum() && rows.size() <= MAX_ROWS + 1; r++) {
                Row row = sheet.getRow(r);
                List<String> values = new ArrayList<>();
                for (int c = 0; c < width; c++) {
                    values.add(row == null ? "" : text(row.getCell(c)));
                }
                rows.add(values);
            }
            return rows;
        } catch (RuntimeException e) {
            throw new IOException("Unreadable Excel file: " + e.getMessage(), e);
        }
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : new BigDecimal(String.valueOf(cell.getNumericCellValue())).stripTrailingZeros().toPlainString();
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
