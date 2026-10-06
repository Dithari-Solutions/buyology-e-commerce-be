package com.buyology.ecommerce.store.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Everything a store product export prints, already resolved to display values — the writers
 * format it and never touch an entity or a repository.
 *
 * @param currency ISO 4217 code of the store's country; every price in the export is in it
 */
public record StoreProductExport(String storeName, String currency, Instant generatedAt, List<Row> rows) {

    /**
     * One store listing.
     *
     * @param salePrice         the discounted price, or null when no discount lowers the price
     * @param availableQuantity units that can still be ordered from this store, or null when stock is
     *                          not tracked — null is "no limit", never zero
     * @param images            thumbnail first (when there are any images), then gallery order
     */
    public record Row(
            String name,
            String sku,
            String brand,
            String category,
            String description,
            BigDecimal price,
            BigDecimal salePrice,
            String discount,
            String availability,
            Integer availableQuantity,
            String condition,
            boolean active,
            String channels,
            String productUrl,
            List<Image> images,
            List<Variant> variants,
            Instant updatedAt) {

        public Optional<Image> thumbnail() {
            return images.stream().filter(Image::thumbnail).findFirst();
        }

        public List<Image> otherImages() {
            return images.stream().filter(i -> !i.thumbnail()).toList();
        }
    }

    public record Image(String url, boolean thumbnail) {
    }

    public record Variant(String sku, BigDecimal price, Integer stock, boolean active) {
    }
}
