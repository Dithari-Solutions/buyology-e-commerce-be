package com.buyology.ecommerce.erpnext.dto;

/**
 * One row in the ERPNext → catalog import preview.
 *
 * @param itemCode        ERPNext item_code (becomes the ecommerce Product SKU)
 * @param itemName        ERPNext item_name
 * @param itemGroup       ERPNext item_group (maps to a ProductCategory)
 * @param brand           ERPNext brand, may be null
 * @param standardRate    ERPNext standard_rate, may be null
 * @param image           absolute image URL, may be null
 * @param stock           on-hand qty summed across ERP warehouses
 * @param alreadyImported whether a Product with this SKU already exists locally
 */
public record ErpImportPreviewRow(
        String itemCode,
        String itemName,
        String itemGroup,
        String brand,
        Double standardRate,
        String image,
        double stock,
        boolean alreadyImported
) {}
