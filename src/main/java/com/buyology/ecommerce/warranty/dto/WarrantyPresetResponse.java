package com.buyology.ecommerce.warranty.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param productCount products currently following this preset (trashed products excluded), so the
 *                     admin can see what an edit or a delete will touch
 */
public record WarrantyPresetResponse(
        UUID id,
        String name,
        int durationMonths,
        long productCount,
        Instant createdAt,
        Instant updatedAt) {
}
