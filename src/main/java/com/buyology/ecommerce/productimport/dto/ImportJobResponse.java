package com.buyology.ecommerce.productimport.dto;

import com.buyology.ecommerce.productimport.domain.ProductImportJob;

import java.time.Instant;
import java.util.UUID;

public record ImportJobResponse(
        UUID id,
        String fileName,
        String status,
        Integer totalRows,
        Integer extractedRows,
        Integer failedRows,
        Integer importedRows,
        UUID defaultCategoryId,
        UUID defaultBrandId,
        String errorMessage,
        String createdByAdminName,
        Instant createdAt,
        Instant completedAt
) {
    public static ImportJobResponse from(ProductImportJob j) {
        return new ImportJobResponse(
                j.getId(),
                j.getFileName(),
                j.getStatus() == null ? null : j.getStatus().name(),
                j.getTotalRows(),
                j.getExtractedRows(),
                j.getFailedRows(),
                j.getImportedRows(),
                j.getDefaultCategoryId(),
                j.getDefaultBrandId(),
                j.getErrorMessage(),
                j.getCreatedByAdminName(),
                j.getCreatedAt(),
                j.getCompletedAt());
    }
}
