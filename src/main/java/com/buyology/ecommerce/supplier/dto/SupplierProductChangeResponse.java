package com.buyology.ecommerce.supplier.dto;

import com.buyology.ecommerce.supplier.domain.SupplierProductChangeRequest;

import java.time.Instant;
import java.util.UUID;

/** View of a supplier product change request for both supplier and admin dashboards. */
public record SupplierProductChangeResponse(
        UUID id,
        UUID productId,
        UUID supplierId,
        String action,
        String status,
        String payload,
        String rejectionReason,
        Instant requestedAt,
        Instant reviewedAt,
        UUID reviewedBy) {

    public static SupplierProductChangeResponse from(SupplierProductChangeRequest r) {
        return new SupplierProductChangeResponse(
                r.getId(),
                r.getProductId(),
                r.getSupplierId(),
                r.getAction().name(),
                r.getStatus().name(),
                r.getPayload(),
                r.getRejectionReason(),
                r.getRequestedAt(),
                r.getReviewedAt(),
                r.getReviewedBy());
    }
}
