package com.buyology.ecommerce.productimport.dto;

import com.buyology.ecommerce.productimport.domain.ProductImportRow;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One row on the review screen: what the sheet said, next to what it was understood to mean.
 *
 * <p>Both are sent, always. The reviewer's job is to compare them, and a screen that shows only
 * the tidy extraction is asking them to approve something they cannot check.
 */
public record ImportRowResponse(
        UUID id,
        Integer rowNumber,
        String rawCode,
        String rawText,
        Integer rawQuantity,
        String status,

        String brand,
        String model,
        String deviceType,
        String processor,
        Integer processorGen,
        Integer ramGb,
        Integer storageGb,
        String storageType,
        BigDecimal screenInches,
        String operatingSystem,
        Integer gpuGb,
        Boolean touchscreen,
        String colour,

        String titleEn,
        String titleAz,
        String titleAr,
        String descriptionEn,
        String descriptionAz,
        String descriptionAr,

        String confidence,
        List<String> needsReview,
        UUID productId,
        String errorMessage
) {
    public static ImportRowResponse from(ProductImportRow r) {
        return new ImportRowResponse(
                r.getId(),
                r.getRowNumber(),
                r.getRawCode(),
                r.getRawText(),
                r.getRawQuantity(),
                r.getStatus() == null ? null : r.getStatus().name(),
                r.getBrand(),
                r.getModel(),
                r.getDeviceType(),
                r.getProcessor(),
                r.getProcessorGen(),
                r.getRamGb(),
                r.getStorageGb(),
                r.getStorageType(),
                r.getScreenInches(),
                r.getOperatingSystem(),
                r.getGpuGb(),
                r.getTouchscreen(),
                r.getColour(),
                r.getTitleEn(),
                r.getTitleAz(),
                r.getTitleAr(),
                r.getDescriptionEn(),
                r.getDescriptionAz(),
                r.getDescriptionAr(),
                r.getConfidence(),
                splitNeedsReview(r.getNeedsReview()),
                r.getProductId(),
                r.getErrorMessage());
    }

    private static List<String> splitNeedsReview(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return List.of(stored.split("\n"));
    }
}
