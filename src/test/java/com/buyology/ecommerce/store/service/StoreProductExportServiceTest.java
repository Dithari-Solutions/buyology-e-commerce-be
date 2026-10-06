package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductMedia;
import com.buyology.ecommerce.product.domain.ProductSpecOption;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.domain.StoreProductVariant;
import com.buyology.ecommerce.store.dto.StoreProductExport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two numbers an export reader acts on — how many units are available, and which image is the
 * thumbnail — must match what the shop actually does.
 */
class StoreProductExportServiceTest {

    private final StoreProductExportService service = new StoreProductExportService(
            null, null, null, null, null, null, null,
            "https://buyology.online/", "https://api.buyology.online/");

    private static StoreProductVariant variant(int stock, boolean active) {
        StoreProductVariant v = new StoreProductVariant();
        v.setStock(stock);
        v.setIsActive(active);
        return v;
    }

    private static ProductMedia media(UUID id, ProductMedia.MediaType type, boolean primary, int order, boolean colorSpecific) {
        ProductMedia m = new ProductMedia();
        m.setId(id);
        m.setMediaType(type);
        m.setIsPrimary(primary);
        m.setOrderIndex(order);
        if (colorSpecific) m.setColorOption(new ProductSpecOption());
        return m;
    }

    @Test
    void untrackedStockIsReportedAsNoLimitNeverZero() {
        assertNull(StoreProductExportService.availableQuantity(null, List.of()));
    }

    @Test
    void theProductCountAppliesWhenTheStoreHasNoVariants() {
        assertEquals(7, StoreProductExportService.availableQuantity(7, List.of()));
    }

    @Test
    void activeVariantStockIsSummedAndInactiveVariantsDoNotCount() {
        assertEquals(5, StoreProductExportService.availableQuantity(null,
                List.of(variant(2, true), variant(3, true), variant(40, false))));
    }

    @Test
    void theSmallerOfStoreStockAndProductCountIsWhatCanBeSold() {
        assertEquals(3, StoreProductExportService.availableQuantity(3, List.of(variant(10, true))));
        assertEquals(4, StoreProductExportService.availableQuantity(9, List.of(variant(4, true))));
    }

    @Test
    void negativeCountsReadAsZero() {
        assertEquals(0, StoreProductExportService.availableQuantity(-2, List.of()));
        assertEquals(0, StoreProductExportService.availableQuantity(null, List.of(variant(-1, true))));
    }

    @Test
    void thePrimaryImageIsTheThumbnailAndComesFirstAsAPermanentLink() {
        UUID first = UUID.randomUUID();
        UUID primary = UUID.randomUUID();
        UUID video = UUID.randomUUID();
        List<StoreProductExport.Image> images = service.images(List.of(
                media(first, ProductMedia.MediaType.IMAGE, false, 0, false),
                media(video, ProductMedia.MediaType.VIDEO, false, 1, false),
                media(primary, ProductMedia.MediaType.IMAGE, true, 2, false)));

        assertEquals(2, images.size(), "videos are not images");
        assertTrue(images.get(0).thumbnail());
        assertEquals("https://api.buyology.online/api/product/media/" + primary, images.get(0).url());
        assertFalse(images.get(1).thumbnail());
        assertEquals("https://api.buyology.online/api/product/media/" + first, images.get(1).url());
    }

    @Test
    void withNoPrimaryTheFirstProductLevelImageIsTheThumbnail() {
        UUID colorFirst = UUID.randomUUID();
        UUID productLevel = UUID.randomUUID();
        List<StoreProductExport.Image> images = service.images(List.of(
                media(colorFirst, ProductMedia.MediaType.IMAGE, false, 0, true),
                media(productLevel, ProductMedia.MediaType.IMAGE, false, 3, false)));

        assertEquals(List.of(true, false), images.stream().map(StoreProductExport.Image::thumbnail).toList());
        assertTrue(images.get(0).url().endsWith(productLevel.toString()));
    }

    @Test
    void theDiscountLabelSaysWhenTheSaleRuns() {
        Instant now = Instant.parse("2026-10-06T12:00:00Z");
        StoreProduct sp = new StoreProduct();
        sp.setStorePrice(new BigDecimal("100.00"));
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("10.00"));
        assertEquals("10% off", StoreProductExportService.discountLabel(sp, now), "no end date: a standing markdown");

        sp.setDiscountEndsAt(Instant.parse("2026-10-12T19:59:59Z"));   // 23:59:59 in Dubai
        assertEquals("10% off until 2026-10-12", StoreProductExportService.discountLabel(sp, now));

        sp.setDiscountEndsAt(Instant.parse("2026-10-01T19:59:59Z"));
        assertEquals("10% off (ended 2026-10-01)", StoreProductExportService.discountLabel(sp, now));

        sp.setDiscountEndsAt(null);
        sp.setDiscountStartsAt(Instant.parse("2026-10-09T20:00:00Z"));  // midnight 10 Oct in Dubai
        assertEquals("10% off (scheduled from 2026-10-10)", StoreProductExportService.discountLabel(sp, now));

        sp.setDiscountType(null);
        assertEquals("", StoreProductExportService.discountLabel(sp, now));
    }

    @Test
    void aProductWithoutImagesHasNoThumbnail() {
        assertTrue(service.images(List.of()).isEmpty());
    }
}
