package com.buyology.ecommerce.erpnext.dto;

/**
 * A product mapped from an ERPNext {@code Item} document.
 *
 * <p>Testing-stage projection — a curated subset of Item fields relayed to the admin UI.
 * Not persisted; not tied to our own {@code Product} entity.
 *
 * @param name         Frappe primary key of the Item (usually equals the item code)
 * @param itemCode     item_code
 * @param itemName     item_name (human-readable)
 * @param description  description (may contain HTML)
 * @param itemGroup    item_group (ERPNext category)
 * @param brand        brand
 * @param standardRate standard_rate (list/selling price)
 * @param stockUom     stock_uom (unit of measure)
 * @param image        absolute image URL (relative Frappe paths are prefixed with the base URL)
 * @param disabled     whether the Item is disabled in ERPNext
 */
public record ErpProduct(
        String name,
        String itemCode,
        String itemName,
        String description,
        String itemGroup,
        String brand,
        Double standardRate,
        String stockUom,
        String image,
        Boolean disabled
) {}
