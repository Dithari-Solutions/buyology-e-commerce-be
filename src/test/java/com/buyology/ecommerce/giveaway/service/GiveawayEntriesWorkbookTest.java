package com.buyology.ecommerce.giveaway.service;

import com.buyology.ecommerce.giveaway.domain.GiveawayEntry;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The export is what the winner is drawn from, so it must carry every entry, in the columns the
 * live-draw page reads, with nothing turned into a formula.
 */
class GiveawayEntriesWorkbookTest {

    private static GiveawayEntry entry(String handle, String raw, String email, String phone) {
        GiveawayEntry e = new GiveawayEntry();
        e.setUserId(UUID.randomUUID());
        e.setInstagramHandle(handle);
        e.setInstagramHandleRaw(raw);
        e.setContactEmail(email);
        e.setContactPhone(phone);
        return e;
    }

    private static Sheet read(byte[] file) throws Exception {
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(file));
        return wb.getSheetAt(0);
    }

    @Test
    void everyEntryIsARowUnderTheHeadersThePageReads() throws Exception {
        byte[] file = GiveawayEntriesWorkbook.write(List.of(
                entry("john_doe", "@John_Doe", "john@example.com", "+971501234567"),
                entry("jane.smith", "https://instagram.com/jane.smith", null, null)));

        Sheet sheet = read(file);
        Row header = sheet.getRow(0);
        assertEquals("Instagram handle", header.getCell(1).getStringCellValue());
        assertEquals("Handle as typed", header.getCell(2).getStringCellValue());
        assertEquals("Email", header.getCell(3).getStringCellValue());
        assertEquals("Phone", header.getCell(4).getStringCellValue());
        assertEquals(2, sheet.getLastRowNum());

        Row first = sheet.getRow(1);
        assertEquals(1, (int) first.getCell(0).getNumericCellValue());
        assertEquals("john_doe", first.getCell(1).getStringCellValue());
        assertEquals("@John_Doe", first.getCell(2).getStringCellValue());
        assertEquals("john@example.com", first.getCell(3).getStringCellValue());
        assertEquals("+971501234567", first.getCell(4).getStringCellValue());
        assertEquals("", first.getCell(5).getStringCellValue(), "not persisted yet, so no entry time");

        Row second = sheet.getRow(2);
        assertEquals("jane.smith", second.getCell(1).getStringCellValue());
        assertEquals("", second.getCell(3).getStringCellValue(), "a missing email is an empty cell, not \"null\"");
    }

    @Test
    void valuesThatLookLikeFormulasStayText() throws Exception {
        byte[] file = GiveawayEntriesWorkbook.write(List.of(entry("x", "=HYPERLINK(\"http://evil\")", null, "+1")));
        Row row = read(file).getRow(1);
        assertEquals(CellType.STRING, row.getCell(2).getCellType());
        assertEquals("=HYPERLINK(\"http://evil\")", row.getCell(2).getStringCellValue());
    }

    @Test
    void noEntriesStillGivesAWorkbookWithTheHeaderRow() throws Exception {
        Sheet sheet = read(GiveawayEntriesWorkbook.write(List.of()));
        assertEquals(0, sheet.getLastRowNum());
        assertEquals("#", sheet.getRow(0).getCell(0).getStringCellValue());
    }
}
