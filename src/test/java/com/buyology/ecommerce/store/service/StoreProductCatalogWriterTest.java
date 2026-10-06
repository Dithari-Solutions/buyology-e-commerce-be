package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.store.dto.StoreProductExport;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What the Excel and PDF exports carry, and that both survive awkward content. */
class StoreProductCatalogWriterTest {

    private static final String THUMB = "https://api.buyology.online/api/product/media/11111111-1111-1111-1111-111111111111";
    private static final String GALLERY = "https://api.buyology.online/api/product/media/22222222-2222-2222-2222-222222222222";

    private static StoreProductExport.Row phone() {
        return new StoreProductExport.Row(
                "iPhone 15 Pro", "IP15P-256", "Apple", "Smartphones", "A titanium phone.",
                new BigDecimal("4299.00"), new BigDecimal("3869.10"), "10% off",
                "In stock", 4, "New", true, "B2C, B2B",
                "https://buyology.online/en/shop/iphone-15-pro",
                List.of(new StoreProductExport.Image(THUMB, true), new StoreProductExport.Image(GALLERY, false)),
                List.of(new StoreProductExport.Variant("IP15P-256-BLK", new BigDecimal("4299.00"), 4, true)),
                Instant.parse("2026-10-01T09:30:00Z"));
    }

    private static StoreProductExport.Row untracked(String name, String description) {
        return new StoreProductExport.Row(
                name, "SKU-2", "", "", description,
                new BigDecimal("10.00"), null, "", "Pre-order", null, "Refurbished (Grade A)", false, "None",
                "", List.of(), List.of(), null);
    }

    private static StoreProductExport export(StoreProductExport.Row... rows) {
        return new StoreProductExport("Dubai Mall", "AED", Instant.parse("2026-10-06T12:00:00Z"), List.of(rows));
    }

    private static int col(String header) {
        return Arrays.asList(StoreProductCatalogWorkbook.PRODUCT_HEADERS).indexOf(header);
    }

    // ── Excel ──────────────────────────────────────────────────────────────────

    @Test
    void theProductsSheetCarriesEveryRequestedFieldWithClickableLinks() throws Exception {
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(StoreProductCatalogWorkbook.write(export(phone()))));
        Sheet sheet = wb.getSheet("Products");
        Row row = sheet.getRow(1);

        assertEquals("iPhone 15 Pro", row.getCell(col("Product name")).getStringCellValue());
        assertEquals("A titanium phone.", row.getCell(col("Description")).getStringCellValue());
        assertEquals(4299.00, row.getCell(col("Price")).getNumericCellValue());
        assertEquals(3869.10, row.getCell(col("Sale price")).getNumericCellValue());
        assertEquals("AED", row.getCell(col("Currency")).getStringCellValue());
        assertEquals("Apple", row.getCell(col("Brand")).getStringCellValue());
        assertEquals("In stock", row.getCell(col("Availability")).getStringCellValue());
        assertEquals(4, (int) row.getCell(col("Available quantity")).getNumericCellValue());

        var url = row.getCell(col("Product URL"));
        assertEquals("https://buyology.online/en/shop/iphone-15-pro", url.getStringCellValue());
        assertEquals(url.getStringCellValue(), url.getHyperlink().getAddress());

        var thumbnail = row.getCell(col("Thumbnail image URL"));
        assertEquals(THUMB, thumbnail.getStringCellValue());
        assertEquals(THUMB, thumbnail.getHyperlink().getAddress());
        assertEquals(GALLERY, row.getCell(col("Other image URLs")).getStringCellValue());
    }

    @Test
    void theImagesSheetMarksWhichImageIsTheThumbnail() throws Exception {
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(StoreProductCatalogWorkbook.write(export(phone()))));
        Sheet images = wb.getSheet("Images");

        assertEquals(2, images.getLastRowNum());
        assertEquals("Thumbnail", images.getRow(1).getCell(3).getStringCellValue());
        assertEquals(THUMB, images.getRow(1).getCell(4).getHyperlink().getAddress());
        assertEquals("Gallery", images.getRow(2).getCell(3).getStringCellValue());

        Sheet variants = wb.getSheet("Variants");
        assertEquals("IP15P-256-BLK", variants.getRow(1).getCell(2).getStringCellValue());
        assertEquals(4, (int) variants.getRow(1).getCell(5).getNumericCellValue());
    }

    @Test
    void untrackedStockSaysSoAndFormulaLookingTextStaysText() throws Exception {
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                StoreProductCatalogWorkbook.write(export(untracked("=HYPERLINK(\"x\")", "=1+1")))));
        Row row = wb.getSheet("Products").getRow(1);

        assertEquals(StoreProductCatalogWorkbook.NOT_TRACKED, row.getCell(col("Available quantity")).getStringCellValue());
        assertEquals(CellType.STRING, row.getCell(col("Product name")).getCellType());
        assertEquals(CellType.STRING, row.getCell(col("Description")).getCellType());
        assertNull(row.getCell(col("Product URL")).getHyperlink(), "no URL, no link");
        assertEquals("Inactive", row.getCell(col("Store status")).getStringCellValue());
    }

    @Test
    void anEmptyStoreStillGivesAWorkbookWithHeaders() throws Exception {
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(StoreProductCatalogWorkbook.write(export())));
        assertEquals(0, wb.getSheet("Products").getLastRowNum());
        assertEquals("Product name", wb.getSheet("Products").getRow(0).getCell(1).getStringCellValue());
    }

    // ── PDF ────────────────────────────────────────────────────────────────────

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder all = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            all.append(extractor.getTextFromPage(page)).append('\n');
        }
        return all.toString();
    }

    @Test
    void thePdfListsEachProductWithItsLinksAndLabelsTheThumbnail() throws Exception {
        String text = text(StoreProductCatalogPdf.write(export(phone(), untracked("Old laptop", ""))));

        assertTrue(text.contains("Dubai Mall"));
        assertTrue(text.contains("iPhone 15 Pro"));
        assertTrue(text.contains("AED 4,299.00"));
        assertTrue(text.contains("THUMBNAIL IMAGE"));
        assertTrue(text.contains(THUMB));
        assertTrue(text.contains("https://buyology.online/en/shop/iphone-15-pro"));
        assertTrue(text.contains(StoreProductCatalogWorkbook.NOT_TRACKED));
    }

    @Test
    void aDescriptionLongerThanAPageStillRenders() throws Exception {
        byte[] pdf = StoreProductCatalogPdf.write(export(untracked("Long one", "Lorem ipsum dolor sit amet. ".repeat(900))));
        assertTrue(new PdfReader(pdf).getNumberOfPages() > 1);
    }

    @Test
    void anEmptyStoreStillGivesAPdf() throws Exception {
        assertTrue(text(StoreProductCatalogPdf.write(export())).contains("This store has no products."));
    }
}
