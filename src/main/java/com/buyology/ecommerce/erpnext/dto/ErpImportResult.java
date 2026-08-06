package com.buyology.ecommerce.erpnext.dto;

/**
 * Outcome of importing a single ERPNext Item into the ecommerce catalog.
 *
 * @param itemCode  ERPNext item_code
 * @param outcome   one of CREATED / UPDATED / SKIPPED / FAILED
 * @param productId ecommerce Product id (null when FAILED)
 * @param message   human-readable detail (failure reason, or a short note)
 */
public record ErpImportResult(
        String itemCode,
        String outcome,
        String productId,
        String message
) {
    public static ErpImportResult created(String itemCode, String productId) {
        return new ErpImportResult(itemCode, "CREATED", productId, "Created");
    }

    public static ErpImportResult updated(String itemCode, String productId, String message) {
        return new ErpImportResult(itemCode, "UPDATED", productId, message);
    }

    public static ErpImportResult skipped(String itemCode, String message) {
        return new ErpImportResult(itemCode, "SKIPPED", null, message);
    }

    public static ErpImportResult failed(String itemCode, String message) {
        return new ErpImportResult(itemCode, "FAILED", null, message);
    }
}
