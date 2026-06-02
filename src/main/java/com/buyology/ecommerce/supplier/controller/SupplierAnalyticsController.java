package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.revenue.dto.RevenueExportResponse;
import com.buyology.ecommerce.revenue.dto.RevenueReportResponse;
import com.buyology.ecommerce.revenue.enums.RevenueExportFormat;
import com.buyology.ecommerce.revenue.enums.RevenuePeriod;
import com.buyology.ecommerce.supplier.service.SupplierAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/supplier/analytics")
public class SupplierAnalyticsController {

    private final SupplierAnalyticsService analyticsService;

    public SupplierAnalyticsController(SupplierAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('supplier:analytics:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        return analyticsService.getSummary();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('supplier:analytics:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return analyticsService.getStats(fromDate, toDate);
    }

    /** Bucketed revenue (daily/weekly/monthly/yearly) for the current supplier. */
    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('supplier:analytics:read')")
    public ResponseEntity<ApiResponse<RevenueReportResponse>> getRevenue(
            @RequestParam(defaultValue = "MONTHLY") RevenuePeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.getRevenue(period, from, to);
    }

    /** Export the current supplier's revenue (CSV/XLSX) — archived in Contabo. */
    @PostMapping("/revenue/export")
    @PreAuthorize("hasAuthority('supplier:analytics:read')")
    public ResponseEntity<ApiResponse<RevenueExportResponse>> exportRevenue(
            @RequestParam(defaultValue = "CSV") RevenueExportFormat format,
            @RequestParam(defaultValue = "MONTHLY") RevenuePeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.exportRevenue(format, period, from, to);
    }
}
