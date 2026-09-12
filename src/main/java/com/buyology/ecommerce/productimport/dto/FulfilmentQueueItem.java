package com.buyology.ecommerce.productimport.dto;

import com.buyology.ecommerce.product.domain.Product;

import java.time.Instant;
import java.util.UUID;

/**
 * One imported product still waiting on a person.
 *
 * <p>Scalar fields only, and that is deliberate rather than minimalism: {@code Product.category}
 * and {@code Product.brand} are {@code LAZY}, and {@code spring.jpa.open-in-view} is false in
 * production, so touching either while serialising a response throws
 * {@code LazyInitializationException}. That is exactly how the product listing broke. Nothing here
 * reads an association.
 */
public record FulfilmentQueueItem(
        UUID id,
        String sku,
        String status,
        Integer stockQuantity,
        String availabilityStatus,
        /** What the extraction was unsure about, plus the original spreadsheet line. */
        String importNotes,
        UUID importJobId,
        Instant createdAt
) {
    public static FulfilmentQueueItem from(Product p) {
        return new FulfilmentQueueItem(
                p.getId(),
                p.getSku(),
                p.getStatus(),
                p.getStockQuantity(),
                p.getAvailabilityStatus() == null ? null : p.getAvailabilityStatus().name(),
                p.getImportNotes(),
                p.getImportJobId(),
                p.getCreatedAt());
    }
}
