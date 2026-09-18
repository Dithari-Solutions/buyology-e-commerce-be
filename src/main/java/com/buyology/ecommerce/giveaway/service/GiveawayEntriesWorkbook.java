package com.buyology.ecommerce.giveaway.service;

import com.buyology.ecommerce.giveaway.domain.GiveawayEntry;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The giveaway entries as an .xlsx file: one sheet, one row per entry, oldest first.
 *
 * <p>The header names are what the live-draw page looks for ("Instagram handle", "Handle as typed",
 * "Email", "Phone", "Entered"), so renaming a column here means checking that page still reads it.
 *
 * <p>Column widths are fixed rather than auto-sized: auto-sizing measures text through AWT fonts,
 * which a headless server may not have, and a failed export on draw day is the one outcome that
 * matters here.
 */
public final class GiveawayEntriesWorkbook {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final String[] HEADERS = {
            "#", "Instagram handle", "Handle as typed", "Email", "Phone", "Entered (UTC)", "User ID"
    };
    private static final int[] WIDTHS = {6, 30, 30, 34, 18, 20, 38};
    private static final DateTimeFormatter ENTERED =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private GiveawayEntriesWorkbook() {
    }

    public static byte[] write(List<GiveawayEntry> entries) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Entries");

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                header.createCell(c).setCellValue(HEADERS[c]);
                header.getCell(c).setCellStyle(headerStyle);
                sheet.setColumnWidth(c, WIDTHS[c] * 256);
            }
            sheet.createFreezePane(0, 1);

            int r = 1;
            for (GiveawayEntry e : entries) {
                Row row = sheet.createRow(r);
                row.createCell(0).setCellValue(r);
                // Every value is written as a string cell, never a formula — a handle typed as
                // "=something" stays text when the file is opened.
                row.createCell(1).setCellValue(text(e.getInstagramHandle()));
                row.createCell(2).setCellValue(text(e.getInstagramHandleRaw()));
                row.createCell(3).setCellValue(text(e.getContactEmail()));
                row.createCell(4).setCellValue(text(e.getContactPhone()));
                row.createCell(5).setCellValue(e.getCreatedAt() == null ? "" : ENTERED.format(e.getCreatedAt()));
                row.createCell(6).setCellValue(e.getUserId() == null ? "" : e.getUserId().toString());
                r++;
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not build the giveaway entries workbook", ex);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
