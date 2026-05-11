package com.buyology.ecommerce.supplier.dto;

import java.time.Instant;
import java.util.UUID;
import com.buyology.ecommerce.review.domain.ProductReview;

public record SupplierReviewSummary(
        UUID id,
        UUID productId,
        UUID userId,
        Short rating,
        String body,
        Boolean isVerifiedPurchase,
        String status,
        Instant createdAt) {

    public static SupplierReviewSummary from(ProductReview r) {
        return new SupplierReviewSummary(
                r.getId(),
                r.getProduct() != null ? r.getProduct().getId() : null,
                r.getUser() != null ? r.getUser().getId() : null,
                r.getRating(),
                r.getBody(),
                r.getIsVerifiedPurchase(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getCreatedAt());
    }
}
