package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.supplier.service.SupplierAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
}
