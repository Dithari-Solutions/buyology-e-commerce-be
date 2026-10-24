package com.buyology.ecommerce.warranty.dto;

/** One main spec on a warranty card, already formatted for display, e.g. ("Memory", "16 GB"). */
public record WarrantySpecLine(String label, String value) {
}
