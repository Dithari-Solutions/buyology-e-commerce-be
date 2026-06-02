package com.buyology.ecommerce.revenue.enums;

/** What a revenue export captures. */
public enum RevenueExportType {
    /** Buyology's own revenue (platform-owned products). */
    PLATFORM,
    /** All suppliers' revenue totals (admin overview). */
    SUPPLIER_ALL,
    /** A single supplier's own revenue (supplier self-export). */
    SUPPLIER
}
