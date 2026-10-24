package com.buyology.ecommerce.warranty.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Why a warranty card is being withdrawn. Kept for staff; never shown publicly.")
public record VoidWarrantyRequest(
        @Schema(example = "Unit returned and refunded") String reason) {
}
