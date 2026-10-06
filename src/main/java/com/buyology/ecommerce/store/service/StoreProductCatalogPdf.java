package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.store.dto.StoreProductExport;
import com.lowagie.text.Anchor;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A store's products as a PDF catalogue: Buyology logo header, then one card per product with its
 * key facts, description, product page link and image links — the thumbnail labelled as such.
 *
 * <p>Landscape A4 so the links fit on one line. Images are linked, not embedded: embedding would mean
 * fetching every image from storage while the admin waits.
 */
public final class StoreProductCatalogPdf {

    public static final String CONTENT_TYPE = "application/pdf";

    private static final Color INK = new Color(0x11, 0x18, 0x27);
    private static final Color MUTED = new Color(0x55, 0x5e, 0x6b);
    private static final Color LINK = new Color(0x1d, 0x4e, 0xd8);
    private static final Color RULE = new Color(0xe5, 0xe7, 0xeb);
    private static final Color LABEL_BG = new Color(0xf7, 0xf8, 0xfa);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, INK);
    private static final Font META = FontFactory.getFont(FontFactory.HELVETICA, 10, MUTED);
    private static final Font NAME = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, INK);
    private static final Font SUB = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, MUTED);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, MUTED);
    private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, INK);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
    private static final Font URL = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.UNDERLINE, LINK);
    private static final Font FOOTER = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, new Color(0x99, 0x99, 0x99));

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final int COLUMNS = 6;

    private StoreProductCatalogPdf() {
    }

    public static byte[] write(StoreProductExport export) {
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            HeaderFooter footer = new HeaderFooter(new Phrase("Buyology — " + export.storeName() + " products · Page ", FOOTER), true);
            footer.setAlignment(Element.ALIGN_RIGHT);
            footer.setBorder(Rectangle.NO_BORDER);
            doc.setFooter(footer);
            doc.open();

            try (InputStream is = new ClassPathResource("report/logo.png").getInputStream()) {
                Image logo = Image.getInstance(is.readAllBytes());
                logo.scaleToFit(120, 60);
                logo.setAlignment(Element.ALIGN_LEFT);
                doc.add(logo);
            } catch (Exception ignored) {
                // the logo is decorative — the catalogue is still complete without it
            }

            Paragraph title = new Paragraph(export.storeName() + " — Products", TITLE);
            title.setSpacingBefore(10);
            doc.add(title);

            int count = export.rows().size();
            Paragraph meta = new Paragraph(
                    count + (count == 1 ? " product" : " products")
                            + (export.currency() != null ? " · Prices in " + export.currency() : "")
                            + " · Generated " + TIMESTAMP.format(export.generatedAt()),
                    META);
            meta.setSpacingAfter(14);
            doc.add(meta);

            if (count == 0) {
                doc.add(new Paragraph("This store has no products.", BODY));
            }
            DecimalFormat money = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
            for (int i = 0; i < count; i++) {
                doc.add(card(i + 1, export.rows().get(i), export.currency(), money));
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            if (doc.isOpen()) doc.close();
            throw new IllegalStateException("Could not build the store products PDF", e);
        }
    }

    private static PdfPTable card(int index, StoreProductExport.Row p, String currency, DecimalFormat money) {
        PdfPTable card = new PdfPTable(COLUMNS);
        card.setWidthPercentage(100);
        card.setKeepTogether(true);   // start a product on a fresh page rather than strand its name
        card.setSplitLate(false);     // ...but let a long description break across pages
        card.setSpacingAfter(14);

        Paragraph subtitle = new Paragraph(joinPresent(" · ", "SKU " + p.sku(), p.brand(), p.category(), p.condition()), SUB);
        subtitle.setSpacingBefore(2);
        card.addCell(span(block(new Paragraph(index + ". " + p.name(), NAME), subtitle), COLUMNS));

        String[] labels = {"Price", "Sale price", "Availability", "Available quantity", "Store status", "Channels"};
        String[] values = {
                price(p.price(), currency, money),
                p.salePrice() == null ? "—" : price(p.salePrice(), currency, money) + (p.discount().isEmpty() ? "" : " (" + p.discount() + ")"),
                p.availability().isEmpty() ? "—" : p.availability(),
                p.availableQuantity() == null ? StoreProductCatalogWorkbook.NOT_TRACKED : String.valueOf(p.availableQuantity()),
                p.active() ? "Active" : "Inactive",
                p.channels()
        };
        for (String label : labels) {
            PdfPCell c = cell(new Phrase(label.toUpperCase(Locale.ROOT), LABEL));
            c.setBackgroundColor(LABEL_BG);
            card.addCell(c);
        }
        for (String value : values) {
            card.addCell(cell(new Phrase(value, VALUE)));
        }

        Paragraph description = new Paragraph(p.description().isEmpty() ? "—" : p.description(), BODY);
        description.setSpacingBefore(3);
        card.addCell(span(block(new Paragraph("DESCRIPTION", LABEL), description), COLUMNS));

        detail(card, "Product URL", link(p.productUrl()));
        List<StoreProductExport.Image> images = p.images();
        if (images.isEmpty()) {
            detail(card, "Images", new Phrase("No images", BODY));
        }
        for (int i = 0; i < images.size(); i++) {
            StoreProductExport.Image image = images.get(i);
            detail(card, image.thumbnail() ? "Thumbnail image" : "Image " + (i + 1), link(image.url()));
        }
        if (!p.variants().isEmpty()) {
            String variants = p.variants().stream()
                    .map(v -> v.sku() + "  ·  " + price(v.price(), currency, money)
                            + "  ·  Stock " + (v.stock() == null ? 0 : v.stock())
                            + (v.active() ? "" : "  ·  Inactive"))
                    .collect(Collectors.joining("\n"));
            detail(card, "Variants", new Phrase(variants, BODY));
        }
        if (p.updatedAt() != null) {
            detail(card, "Last updated", new Phrase(TIMESTAMP.format(p.updatedAt()), BODY));
        }
        return card;
    }

    private static void detail(PdfPTable card, String label, Phrase value) {
        PdfPCell labelCell = cell(new Phrase(label.toUpperCase(Locale.ROOT), LABEL));
        labelCell.setBackgroundColor(LABEL_BG);
        card.addCell(labelCell);
        card.addCell(span(cell(value), COLUMNS - 1));
    }

    private static Phrase link(String url) {
        if (url == null || url.isBlank()) return new Phrase("—", BODY);
        Anchor anchor = new Anchor(url, URL);
        anchor.setReference(url);
        return anchor;
    }

    private static PdfPCell cell(Phrase content) {
        PdfPCell c = new PdfPCell(content);
        c.setPadding(6);
        c.setBorderColor(RULE);
        c.setBorderWidth(0.5f);
        return c;
    }

    /** A cell of stacked paragraphs. Unlike a text cell, it honours each paragraph's spacing. */
    private static PdfPCell block(Element... elements) {
        PdfPCell c = new PdfPCell();
        for (Element e : elements) c.addElement(e);
        c.setPadding(6);
        c.setPaddingTop(2);
        c.setPaddingBottom(8);
        c.setBorderColor(RULE);
        c.setBorderWidth(0.5f);
        return c;
    }

    private static PdfPCell span(PdfPCell c, int columns) {
        c.setColspan(columns);
        return c;
    }

    private static String price(BigDecimal value, String currency, DecimalFormat money) {
        if (value == null) return "—";
        return (currency == null ? "" : currency + " ") + money.format(value);
    }

    private static String joinPresent(String separator, String... parts) {
        return Stream.of(parts).filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(separator));
    }
}
