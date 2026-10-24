package com.buyology.ecommerce.warranty.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A named warranty length, e.g. Laptop = 12 months")
public record WarrantyPresetRequest(
        @Schema(description = "Shown in the product form's warranty dropdown", example = "Laptop")
        String name,
        @Schema(description = "Whole months, 1-120. Send 24 for two years.", example = "12")
        Integer durationMonths) {
}
