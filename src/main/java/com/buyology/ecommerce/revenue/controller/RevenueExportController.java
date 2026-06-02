package com.buyology.ecommerce.revenue.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.revenue.dto.RevenueExportResponse;
import com.buyology.ecommerce.revenue.enums.RevenueExportFormat;
import com.buyology.ecommerce.revenue.enums.RevenueExportType;
import com.buyology.ecommerce.revenue.enums.RevenuePeriod;
import com.buyology.ecommerce.revenue.service.RevenueExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Revenue export generation + history. Restricted to SUPERADMIN — only the
 * super admin may export the revenue tables and view who exported what.
 */
@RestController
@RequestMapping("/api/admin/revenue/exports")
@PreAuthorize("hasRole('SUPERADMIN')")
public class RevenueExportController {

    private final RevenueExportService exportService;

    public RevenueExportController(RevenueExportService exportService) {
        this.exportService = exportService;
    }

    /** Generate a revenue export (PLATFORM or SUPPLIER_ALL), archive it, return its download URL. */
    @PostMapping
    public ResponseEntity<ApiResponse<RevenueExportResponse>> create(
            @RequestParam(defaultValue = "PLATFORM") RevenueExportType type,
            @RequestParam(defaultValue = "CSV") RevenueExportFormat format,
            @RequestParam(defaultValue = "MONTHLY") RevenuePeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) java.util.UUID storeId) {
        RevenueExportResponse result = switch (type) {
            case PLATFORM -> exportService.exportPlatform(format, period, from, to, storeId);
            case SUPPLIER_ALL -> exportService.exportSupplierOverview(format, period, from, to);
            case SUPPLIER -> null;
        };
        if (result == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "Use the supplier portal export for a single supplier");
        }
        return ApiResponse.created(result, "Revenue export generated");
    }

    /** Export history — who exported what, with fresh download links. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RevenueExportResponse>>> list() {
        return ApiResponse.success(exportService.listExports(), "Revenue exports");
    }
}
