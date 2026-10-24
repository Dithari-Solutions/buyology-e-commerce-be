package com.buyology.ecommerce.warranty.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * What anyone holding a serial number may see: the product and its cover. Nothing about the
 * customer or the order — the serial is the only key, and it is printed on a card that changes
 * hands with the device.
 */
@Schema(description = "A warranty card as shown on Dithari's warranty checker")
public record PublicWarrantyResponse(
        @Schema(example = "DTH-7K4Q-2M9X-QH3T") String serialNumber,
        @Schema(description = "Computed on the day of the request",
                allowableValues = {"ACTIVE", "EXPIRING_SOON", "EXPIRED", "VOID"}) String status,
        int durationMonths,
        @Schema(description = "ISO date, first day of cover", example = "2026-06-24") String startDate,
        @Schema(description = "ISO date, last day of cover", example = "2027-06-24") String endDate,
        @Schema(example = "Buyology") String soldBy,
        @Schema(description = "What the warranty covers; absent until configured") String coverage,
        Product product) {

    public record Product(
            String name,
            String brand,
            String category,
            @Schema(example = "Refurbished · Grade A") String condition,
            List<WarrantySpecLine> specs) {
    }
}
