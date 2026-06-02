package com.buyology.ecommerce.supplier.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.revenue.dto.RevenueExportResponse;
import com.buyology.ecommerce.revenue.dto.RevenueReportResponse;
import com.buyology.ecommerce.revenue.enums.RevenueExportFormat;
import com.buyology.ecommerce.revenue.enums.RevenuePeriod;
import com.buyology.ecommerce.revenue.service.RevenueExportService;
import com.buyology.ecommerce.revenue.service.RevenueService;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupplierAnalyticsService {

    private final SupplierRepository supplierRepository;
    private final OrderItemRepository orderItemRepository;
    private final RevenueService revenueService;
    private final RevenueExportService revenueExportService;

    public SupplierAnalyticsService(
            SupplierRepository supplierRepository,
            OrderItemRepository orderItemRepository,
            RevenueService revenueService,
            RevenueExportService revenueExportService) {
        this.supplierRepository = supplierRepository;
        this.orderItemRepository = orderItemRepository;
        this.revenueService = revenueService;
        this.revenueExportService = revenueExportService;
    }

    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        long totalOrders = orderItemRepository.countBySupplierId(supplier.getId());
        BigDecimal totalRevenue = orderItemRepository.sumRevenueBySupplierId(supplier.getId());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders", totalOrders);
        summary.put("totalRevenue", totalRevenue);
        return ApiResponse.success(summary, "Analytics summary");
    }

    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(LocalDate fromDate, LocalDate toDate) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }

        Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Object[]> rows = orderItemRepository.monthlyStatsBySupplierId(supplier.getId(), from, to);

        List<Map<String, Object>> monthly = rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", row[0] != null ? row[0].toString() : null);
            m.put("orders", row[1]);
            m.put("revenue", row[2]);
            return m;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", fromDate.toString());
        result.put("to", toDate.toString());
        result.put("monthly", monthly);
        return ApiResponse.success(result, "Analytics");
    }

    /** Bucketed revenue report (daily/weekly/monthly/yearly) for the current supplier. */
    public ResponseEntity<ApiResponse<RevenueReportResponse>> getRevenue(
            RevenuePeriod period, LocalDate from, LocalDate to) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        return ApiResponse.success(
                revenueService.supplierReport(supplier.getId(), period, from, to), "Revenue");
    }

    /** Export the current supplier's revenue to Contabo and return a download link. */
    public ResponseEntity<ApiResponse<RevenueExportResponse>> exportRevenue(
            RevenueExportFormat format, RevenuePeriod period, LocalDate from, LocalDate to) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        return ApiResponse.created(
                revenueExportService.exportSupplier(supplier.getId(), format, period, from, to),
                "Revenue export generated");
    }

    private Supplier resolveCurrentSupplier() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) return null;
            return supplierRepository.findByUserId(userId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
