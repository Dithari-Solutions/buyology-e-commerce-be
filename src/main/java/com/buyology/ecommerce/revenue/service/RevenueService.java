package com.buyology.ecommerce.revenue.service;

import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.revenue.dto.RevenueBucketRow;
import com.buyology.ecommerce.revenue.dto.RevenueReportResponse;
import com.buyology.ecommerce.revenue.dto.SupplierRevenueOverviewResponse;
import com.buyology.ecommerce.revenue.dto.SupplierRevenueRow;
import com.buyology.ecommerce.revenue.enums.RevenuePeriod;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds bucketed revenue reports from {@link OrderItemRepository} aggregates.
 * Revenue model (no commission): Buyology revenue = platform-owned products
 * (supplierId null); supplier revenue = items grouped by supplierId.
 */
@Service
public class RevenueService {

    private final OrderItemRepository orderItemRepository;
    private final SupplierRepository supplierRepository;

    public RevenueService(OrderItemRepository orderItemRepository, SupplierRepository supplierRepository) {
        this.orderItemRepository = orderItemRepository;
        this.supplierRepository = supplierRepository;
    }

    public RevenueReportResponse platformReport(RevenuePeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolveWindow(period, from, to);
        List<Object[]> rows = orderItemRepository.platformRevenueBuckets(
                period.getTruncUnit(), startInstant(window[0]), endInstant(window[1]));
        return buildReport(period, window[0], window[1], "Buyology", rows);
    }

    public RevenueReportResponse supplierReport(UUID supplierId, RevenuePeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolveWindow(period, from, to);
        List<Object[]> rows = orderItemRepository.supplierRevenueBuckets(
                supplierId, period.getTruncUnit(), startInstant(window[0]), endInstant(window[1]));
        String label = supplierRepository.findById(supplierId)
                .map(Supplier::getBusinessName)
                .orElse(supplierId.toString());
        return buildReport(period, window[0], window[1], label, rows);
    }

    public SupplierRevenueOverviewResponse supplierOverview(RevenuePeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolveWindow(period, from, to);
        List<Object[]> rows = orderItemRepository.supplierRevenueTotals(
                startInstant(window[0]), endInstant(window[1]));

        Map<UUID, String> names = supplierRepository.findAll().stream()
                .collect(Collectors.toMap(Supplier::getId, s ->
                        s.getBusinessName() != null ? s.getBusinessName() : s.getId().toString(),
                        (a, b) -> a));

        List<SupplierRevenueRow> suppliers = new ArrayList<>();
        long totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Object[] row : rows) {
            UUID supplierId = (UUID) row[0];
            long orders = ((Number) row[1]).longValue();
            BigDecimal revenue = (BigDecimal) row[2];
            suppliers.add(new SupplierRevenueRow(
                    supplierId, names.getOrDefault(supplierId, supplierId.toString()), orders, revenue));
            totalOrders += orders;
            totalRevenue = totalRevenue.add(revenue);
        }
        return new SupplierRevenueOverviewResponse(period, window[0], window[1], totalRevenue, totalOrders, suppliers);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RevenueReportResponse buildReport(
            RevenuePeriod period, LocalDate from, LocalDate to, String label, List<Object[]> rows) {
        List<RevenueBucketRow> buckets = new ArrayList<>();
        long totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Object[] row : rows) {
            long orders = ((Number) row[1]).longValue();
            BigDecimal revenue = (BigDecimal) row[2];
            buckets.add(new RevenueBucketRow(periodLabel(row[0]), orders, revenue));
            totalOrders += orders;
            totalRevenue = totalRevenue.add(revenue);
        }
        return new RevenueReportResponse(period, from, to, label, totalRevenue, totalOrders, buckets);
    }

    /** date_trunc returns a timestamp; keep just the date portion as the bucket label. */
    private String periodLabel(Object raw) {
        if (raw == null) return null;
        String s = raw.toString();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    /** Default window when none supplied: a sensible recent span per granularity. */
    private LocalDate[] resolveWindow(RevenuePeriod period, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from;
        if (start == null) {
            start = switch (period) {
                case DAILY -> end.minusDays(29);
                case WEEKLY -> end.minusWeeks(11);
                case MONTHLY -> end.minusMonths(11);
                case YEARLY -> end.minusYears(4);
            };
        }
        return new LocalDate[]{start, end};
    }

    private Instant startInstant(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Exclusive upper bound: end-of-day for the `to` date. */
    private Instant endInstant(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
