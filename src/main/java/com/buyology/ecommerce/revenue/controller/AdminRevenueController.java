package com.buyology.ecommerce.revenue.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.revenue.dto.RevenueReportResponse;
import com.buyology.ecommerce.revenue.dto.SupplierRevenueOverviewResponse;
import com.buyology.ecommerce.revenue.enums.RevenuePeriod;
import com.buyology.ecommerce.revenue.service.RevenueService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Admin revenue reports. Viewable by any admin-side role; exporting the tables
 * is restricted to SUPERADMIN (see {@code RevenueExportController}).
 */
@RestController
@RequestMapping("/api/admin/revenue")
public class AdminRevenueController {

    private final RevenueService revenueService;

    public AdminRevenueController(RevenueService revenueService) {
        this.revenueService = revenueService;
    }

    /** Buyology's own revenue (platform-owned products), optionally filtered to one store. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('revenue:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/platform")
    public ResponseEntity<ApiResponse<RevenueReportResponse>> platform(
            @RequestParam(defaultValue = "MONTHLY") RevenuePeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID storeId) {
        return ApiResponse.success(revenueService.platformReport(period, from, to, storeId), "Platform revenue");
    }

    /** All suppliers' revenue totals. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('revenue:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/suppliers")
    public ResponseEntity<ApiResponse<SupplierRevenueOverviewResponse>> suppliers(
            @RequestParam(defaultValue = "MONTHLY") RevenuePeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(revenueService.supplierOverview(period, from, to), "Supplier revenue overview");
    }

    /** A single supplier's revenue, bucketed. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('revenue:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/suppliers/{supplierId}")
    public ResponseEntity<ApiResponse<RevenueReportResponse>> supplier(
            @PathVariable UUID supplierId,
            @RequestParam(defaultValue = "MONTHLY") RevenuePeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(revenueService.supplierReport(supplierId, period, from, to), "Supplier revenue");
    }
}
