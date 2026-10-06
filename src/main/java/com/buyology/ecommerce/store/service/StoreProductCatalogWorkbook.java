package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.store.dto.StoreProductExport;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A store's products as an .xlsx file with three sheets:
 * <ul>
 *   <li><b>Products</b> — one row per product, with its thumbnail and the rest of its images;</li>
 *   <li><b>Images</b> — one row per image, each marked Thumbnail or Gallery, every link clickable;</li>
 *   <li><b>Variants</b> — the store's price and stock for each variant.</li>
 * </ul>
 *
 * <p>Column widths are fixed rather than auto-sized: auto-sizing measures text through AWT fonts,
 * which a headless server may not have. Every text value is written as a string cell, never a
 * formula, so a description starting with "=" stays text when the file is opened.
 */
public final class StoreProductCatalogWorkbook {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final String NOT_TRACKED = "Not tracked";

    static final String[] PRODUCT_HEADERS = {
            "#", "Product name", "SKU", "Brand", "Category", "Description",
            "Price", "Sale price", "Discount", "Currency",
            "Availability", "Available quantity", "Condition", "Store status", "Channels",
            "Product URL", "Thumbnail image URL", "Other image URLs", "Last updated (UTC)"
    };
    private static final int[] PRODUCT_WIDTHS = {6, 36, 22, 16, 20, 60, 12, 12, 16, 10, 14, 12, 18, 12, 12, 48, 60, 60, 18};

    static final String[] IMAGE_HEADERS = {"Product name", "SKU", "Image #", "Type", "Image URL"};
    private static final int[] IMAGE_WIDTHS = {36, 22, 9, 12, 70};

    static final String[] VARIANT_HEADERS = {"Product name", "Product SKU", "Variant SKU", "Price", "Currency", "Stock", "Status"};
    private static final int[] VARIANT_WIDTHS = {36, 22, 26, 12, 10, 10, 12};

    // Excel's hard limit on the text in one cell.
    private static final int MAX_CELL_TEXT = 32_767;
    private static final DateTimeFormatter UPDATED =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private StoreProductCatalogWorkbook() {
    }

    public static byte[] write(StoreProductExport export) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            writeProducts(workbook.createSheet("Products"), export, styles);
            writeImages(workbook.createSheet("Images"), export, styles);
            writeVariants(workbook.createSheet("Variants"), export, styles);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not build the store products workbook", ex);
        }
    }

    private static void writeProducts(Sheet sheet, StoreProductExport export, Styles styles) {
        header(sheet, PRODUCT_HEADERS, PRODUCT_WIDTHS, styles);
        int r = 1;
        for (StoreProductExport.Row p : export.rows()) {
            Row row = sheet.createRow(r);
            int c = 0;
            number(row, c++, r, styles.text);
            text(row, c++, p.name(), styles.text);
            text(row, c++, p.sku(), styles.text);
            text(row, c++, p.brand(), styles.text);
            text(row, c++, p.category(), styles.text);
            text(row, c++, p.description(), styles.wrapped);
            money(row, c++, p.price(), styles.money);
            money(row, c++, p.salePrice(), styles.money);
            text(row, c++, p.discount(), styles.text);
            text(row, c++, export.currency(), styles.text);
            text(row, c++, p.availability(), styles.text);
            if (p.availableQuantity() != null) {
                number(row, c++, p.availableQuantity(), styles.text);
            } else {
                text(row, c++, NOT_TRACKED, styles.text);
            }
            text(row, c++, p.condition(), styles.text);
            text(row, c++, p.active() ? "Active" : "Inactive", styles.text);
            text(row, c++, p.channels(), styles.text);
            link(row, c++, p.productUrl(), styles);
            link(row, c++, p.thumbnail().map(StoreProductExport.Image::url).orElse(""), styles);
            text(row, c++, p.otherImages().stream().map(StoreProductExport.Image::url)
                    .collect(Collectors.joining("\n")), styles.wrapped);
            text(row, c, p.updatedAt() == null ? "" : UPDATED.format(p.updatedAt()), styles.text);
            r++;
        }
        finish(sheet, r, PRODUCT_HEADERS.length);
    }

    private static void writeImages(Sheet sheet, StoreProductExport export, Styles styles) {
        header(sheet, IMAGE_HEADERS, IMAGE_WIDTHS, styles);
        int r = 1;
        for (StoreProductExport.Row p : export.rows()) {
            List<StoreProductExport.Image> images = p.images();
            for (int i = 0; i < images.size(); i++) {
                Row row = sheet.createRow(r++);
                text(row, 0, p.name(), styles.text);
                text(row, 1, p.sku(), styles.text);
                number(row, 2, i + 1, styles.text);
                text(row, 3, images.get(i).thumbnail() ? "Thumbnail" : "Gallery", styles.text);
                link(row, 4, images.get(i).url(), styles);
            }
        }
        finish(sheet, r, IMAGE_HEADERS.length);
    }

    private static void writeVariants(Sheet sheet, StoreProductExport export, Styles styles) {
        header(sheet, VARIANT_HEADERS, VARIANT_WIDTHS, styles);
        int r = 1;
        for (StoreProductExport.Row p : export.rows()) {
            for (StoreProductExport.Variant v : p.variants()) {
                Row row = sheet.createRow(r++);
                text(row, 0, p.name(), styles.text);
                text(row, 1, p.sku(), styles.text);
                text(row, 2, v.sku(), styles.text);
                money(row, 3, v.price(), styles.money);
                text(row, 4, export.currency(), styles.text);
                number(row, 5, v.stock() == null ? 0 : v.stock(), styles.text);
                text(row, 6, v.active() ? "Active" : "Inactive", styles.text);
            }
        }
        finish(sheet, r, VARIANT_HEADERS.length);
    }

    private static void header(Sheet sheet, String[] headers, int[] widths, Styles styles) {
        Row header = sheet.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(styles.header);
            sheet.setColumnWidth(c, widths[c] * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private static void finish(Sheet sheet, int rowCount, int columnCount) {
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowCount - 1), 0, columnCount - 1));
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        String v = value == null ? "" : value;
        cell.setCellValue(v.length() > MAX_CELL_TEXT ? v.substring(0, MAX_CELL_TEXT - 1) + "…" : v);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int col, int value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void money(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    /** A clickable URL — or plain text, should a stored value not parse as one. */
    private static void link(Row row, int col, String url, Styles styles) {
        Cell cell = row.createCell(col);
        cell.setCellValue(url == null ? "" : url);
        cell.setCellStyle(styles.text);
        if (url == null || url.isBlank()) return;
        try {
            Hyperlink hyperlink = row.getSheet().getWorkbook().getCreationHelper().createHyperlink(HyperlinkType.URL);
            hyperlink.setAddress(url);
            cell.setHyperlink(hyperlink);
            cell.setCellStyle(styles.link);
        } catch (IllegalArgumentException notAUrl) {
            // keep the text; it is still the value that was stored
        }
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle text;
        final CellStyle wrapped;
        final CellStyle money;
        final CellStyle link;

        Styles(Workbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            header = workbook.createCellStyle();
            header.setFont(bold);

            text = workbook.createCellStyle();
            text.setVerticalAlignment(VerticalAlignment.TOP);

            wrapped = workbook.createCellStyle();
            wrapped.cloneStyleFrom(text);
            wrapped.setWrapText(true);

            money = workbook.createCellStyle();
            money.cloneStyleFrom(text);
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            Font linkFont = workbook.createFont();
            linkFont.setUnderline(Font.U_SINGLE);
            linkFont.setColor(IndexedColors.BLUE.getIndex());
            link = workbook.createCellStyle();
            link.cloneStyleFrom(text);
            link.setFont(linkFont);
        }
    }
}
