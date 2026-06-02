package com.buyology.ecommerce.revenue.dto;

import com.buyology.ecommerce.revenue.enums.RevenueExportFormat;
import com.buyology.ecommerce.revenue.enums.RevenueExportType;
import com.buyology.ecommerce.revenue.enums.RevenuePeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A revenue export record plus a freshly-presigned download URL. */
public record RevenueExportResponse(
        UUID id,
        RevenueExportType exportType,
        RevenueExportFormat format,
        RevenuePeriod period,
        LocalDate fromDate,
        LocalDate toDate,
        UUID supplierId,
        String fileName,
        long fileSize,
        UUID exportedByUserId,
        String exportedByEmail,
        String exportedByRole,
        Instant createdAt,
        String downloadUrl) {
}
