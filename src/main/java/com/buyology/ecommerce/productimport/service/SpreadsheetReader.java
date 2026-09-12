package com.buyology.ecommerce.productimport.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns an uploaded .xlsx into raw rows, and does nothing clever with them.
 *
 * <p>Deliberately dumb. Everything this class knows how to do is find which column holds the
 * description, which holds a code and which holds a quantity — the interpretation of the
 * description is Claude's job, and splitting those two responsibilities is what keeps the parsing
 * honest. A regex that tries to pull "16GB RAM" out of a cell will also confidently pull it out of
 * a cell that says something else, and it will do so silently.
 *
 * <p>Header detection scans the first fifteen rows rather than assuming row 0, because supplier
 * sheets routinely open with a merged title, a logo row, or a blank line or two.
 */
@Component
public class SpreadsheetReader {

    private static final Logger log = LoggerFactory.getLogger(SpreadsheetReader.class);

    /** Beyond this an import is almost certainly a mistake, and it is a lot of money in tokens. */
    public static final int MAX_ROWS = 500;

    private static final int HEADER_SCAN_DEPTH = 15;

    /** What a row looked like in the file. No interpretation. */
    public record RawRow(int rowNumber, String code, String text, Integer quantity) {}

    public record Parsed(List<RawRow> rows, String detectedHeader) {}

    /**
     * Reads every data row of the first sheet.
     *
     * @throws IllegalArgumentException when the file is unreadable, empty, has no recognisable
     *                                  description column, or exceeds {@link #MAX_ROWS}
     */
    public Parsed read(InputStream in, String fileName) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("That file has no sheets in it.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            HeaderMap header = findHeader(sheet, formatter);
            if (header == null) {
                throw new IllegalArgumentException(
                        "Could not find a column of product descriptions in " + fileName
                                + ". The sheet needs a header row with a column named something like "
                                + "\"Model & Specifications\", \"Description\" or \"Product\".");
            }

            List<RawRow> rows = new ArrayList<>();
            for (int r = header.rowIndex() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String text = cell(formatter, row, header.textCol());
                if (text == null || text.isBlank()) continue;

                // A trailing "TOTAL / 82" line is a summary, not a product. Rows like it are
                // common at the bottom of supplier sheets and would otherwise be imported as a
                // product literally called "Total".
                String flat = text.trim().toLowerCase(Locale.ROOT);
                if (flat.equals("total") || flat.startsWith("total ") || flat.startsWith("grand total")) {
                    continue;
                }

                rows.add(new RawRow(
                        r + 1,                                        // 1-based, as Excel shows it
                        cell(formatter, row, header.codeCol()),
                        text.trim(),
                        intCell(row, header.qtyCol(), formatter)));

                if (rows.size() > MAX_ROWS) {
                    throw new IllegalArgumentException(
                            "That sheet has more than " + MAX_ROWS + " product rows. Split it into "
                                    + "smaller files — this keeps one mistake from becoming " + MAX_ROWS
                                    + " products.");
                }
            }

            if (rows.isEmpty()) {
                throw new IllegalArgumentException(
                        "No product rows found under the header in " + fileName + ".");
            }

            log.info("[IMPORT] Parsed {} row(s) from {} (header row {})",
                    rows.size(), fileName, header.rowIndex() + 1);
            return new Parsed(rows, header.label());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // POI throws a wide variety for a file that is not really a workbook — a .csv renamed,
            // an .xls, a corrupted upload. The admin gets one clear sentence rather than the
            // class name of whatever POI happened to throw.
            log.warn("[IMPORT] Could not read {}: {}", fileName, e.toString());
            throw new IllegalArgumentException(
                    "Could not read " + fileName + " as an Excel file. Save it as .xlsx and try again.");
        }
    }

    private record HeaderMap(int rowIndex, int textCol, int codeCol, int qtyCol, String label) {}

    /**
     * Finds the header row by looking for a column whose name reads like a product description.
     *
     * <p>The description column is the only one that must exist: a sheet with no code and no
     * quantity is still importable (quantity then defaults to 1 and the SKU is generated), but a
     * sheet with no descriptions has nothing to import.
     */
    private HeaderMap findHeader(Sheet sheet, DataFormatter formatter) {
        int depth = Math.min(HEADER_SCAN_DEPTH, sheet.getLastRowNum());
        for (int r = sheet.getFirstRowNum(); r <= depth; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            int textCol = -1, codeCol = -1, qtyCol = -1;
            String label = null;

            for (int c = row.getFirstCellNum(); c >= 0 && c < row.getLastCellNum(); c++) {
                String h = cell(formatter, row, c);
                if (h == null || h.isBlank()) continue;
                String k = h.trim().toLowerCase(Locale.ROOT);

                if (textCol < 0 && (k.contains("specification") || k.contains("description")
                        || k.contains("model") || k.contains("product") || k.contains("item")
                        || k.contains("detail"))) {
                    textCol = c;
                    label = h.trim();
                } else if (codeCol < 0 && (k.contains("code") || k.contains("sku")
                        || k.equals("ref") || k.contains("reference"))) {
                    codeCol = c;
                } else if (qtyCol < 0 && (k.contains("quantity") || k.equals("qty")
                        || k.startsWith("qty") || k.contains("stock") || k.contains("units"))) {
                    qtyCol = c;
                }
            }

            if (textCol >= 0) {
                return new HeaderMap(r, textCol, codeCol, qtyCol, label);
            }
        }
        return null;
    }

    private static String cell(DataFormatter formatter, Row row, int col) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        String v = formatter.formatCellValue(cell);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /**
     * Reads a quantity that may have been typed as a number or as text ("2 pcs", "1").
     *
     * <p>Returns null rather than 0 when the cell says nothing — null means "the sheet did not say"
     * and lets the caller default, whereas 0 would mean "none in stock" and would import a product
     * nobody can buy.
     */
    private static Integer intCell(Row row, int col, DataFormatter formatter) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) Math.round(cell.getNumericCellValue());
            }
            String raw = formatter.formatCellValue(cell);
            if (raw == null || raw.isBlank()) return null;
            String digits = raw.replaceAll("[^0-9]", "");
            return digits.isBlank() ? null : Integer.valueOf(digits);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
