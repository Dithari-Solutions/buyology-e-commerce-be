package com.buyology.ecommerce.revenue.enums;

/**
 * Granularity for revenue reports. Maps directly to the Postgres
 * {@code date_trunc} unit used to bucket order items.
 */
public enum RevenuePeriod {
    DAILY("day"),
    WEEKLY("week"),
    MONTHLY("month"),
    YEARLY("year");

    private final String truncUnit;

    RevenuePeriod(String truncUnit) {
        this.truncUnit = truncUnit;
    }

    public String getTruncUnit() {
        return truncUnit;
    }
}
