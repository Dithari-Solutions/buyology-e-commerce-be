package com.buyology.ecommerce.supplier.dto;

import java.util.UUID;
import com.buyology.ecommerce.product.domain.Product;

public record SupplierProductSummary(
        UUID id,
        String sku,
        String status,
        String supplierStatus,
        String supplierRejectionReason,
        UUID supplierId,
        UUID categoryId) {

    public static SupplierProductSummary from(Product p) {
        return new SupplierProductSummary(
                p.getId(),
                p.getSku(),
                p.getStatus(),
                p.getSupplierStatus() != null ? p.getSupplierStatus().name() : null,
                p.getSupplierRejectionReason(),
                p.getSupplierId(),
                p.getCategory() != null ? p.getCategory().getId() : null);
    }
}
