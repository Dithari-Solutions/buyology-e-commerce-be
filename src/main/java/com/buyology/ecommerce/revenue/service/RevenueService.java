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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return platformReport(period, from, to, null);
    }

    public RevenueReportResponse platformReport(RevenuePeriod period, LocalDate from, LocalDate to, UUID storeId) {
        LocalDate[] window = resolveWindow(period, from, to);
        Instant fromI = startInstant(window[0]);
        Instant toI = endInstant(window[1]);
        String store = storeId != null ? storeId.toString() : null;
        List<Object[]> rows = orderItemRepository.platformRevenueBuckets(period.getTruncUnit(), fromI, toI, store);
        Map<String, BigDecimal> refunds = refundMap(
                orderItemRepository.platformRefundBuckets(period.getTruncUnit(), fromI, toI, store));
        return buildReport(period, window[0], window[1], "Buyology", rows, refunds);
    }

    public RevenueReportResponse supplierReport(UUID supplierId, RevenuePeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolveWindow(period, from, to);
        Instant fromI = startInstant(window[0]);
        Instant toI = endInstant(window[1]);
        List<Object[]> rows = orderItemRepository.supplierRevenueBuckets(supplierId, period.getTruncUnit(), fromI, toI);
        Map<String, BigDecimal> refunds = refundMap(
                orderItemRepository.supplierRefundBuckets(supplierId, period.getTruncUnit(), fromI, toI));
        String label = supplierRepository.findById(supplierId)
                .map(Supplier::getBusinessName)
                .orElse(supplierId.toString());
        return buildReport(period, window[0], window[1], label, rows, refunds);
    }

    public SupplierRevenueOverviewResponse supplierOverview(RevenuePeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolveWindow(period, from, to);
        Instant fromI = startInstant(window[0]);
        Instant toI = endInstant(window[1]);
        List<Object[]> rows = orderItemRepository.supplierRevenueTotals(fromI, toI);

        // supplierId -> allocated refund total
        Map<UUID, BigDecimal> refundBySupplier = new HashMap<>();
        for (Object[] r : orderItemRepository.supplierRefundTotals(fromI, toI)) {
            refundBySupplier.put((UUID) r[0], (BigDecimal) r[1]);
        }

        Map<UUID, String> names = supplierRepository.findAll().stream()
                .collect(Collectors.toMap(Supplier::getId, s ->
                        s.getBusinessName() != null ? s.getBusinessName() : s.getId().toString(),
                        (a, b) -> a));

        List<SupplierRevenueRow> suppliers = new ArrayList<>();
        long totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        for (Object[] row : rows) {
            UUID supplierId = (UUID) row[0];
            long orders = ((Number) row[1]).longValue();
            BigDecimal revenue = (BigDecimal) row[2];
            BigDecimal refunded = refundBySupplier.getOrDefault(supplierId, BigDecimal.ZERO);
            suppliers.add(new SupplierRevenueRow(
                    supplierId, names.getOrDefault(supplierId, supplierId.toString()),
                    orders, revenue, refunded, revenue.subtract(refunded)));
            totalOrders += orders;
            totalRevenue = totalRevenue.add(revenue);
            totalRefunded = totalRefunded.add(refunded);
        }
        return new SupplierRevenueOverviewResponse(period, window[0], window[1],
                totalRevenue, totalRefunded, totalRevenue.subtract(totalRefunded), totalOrders, suppliers);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RevenueReportResponse buildReport(
            RevenuePeriod period, LocalDate from, LocalDate to, String label,
            List<Object[]> rows, Map<String, BigDecimal> refunds) {
        List<RevenueBucketRow> buckets = new ArrayList<>();
        long totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        Set<String> seenPeriods = new LinkedHashSet<>();
        for (Object[] row : rows) {
            String period0 = periodLabel(row[0]);
            long orders = ((Number) row[1]).longValue();
            BigDecimal revenue = (BigDecimal) row[2];
            BigDecimal refunded = refunds.getOrDefault(period0, BigDecimal.ZERO);
            buckets.add(new RevenueBucketRow(period0, orders, revenue, refunded, revenue.subtract(refunded)));
            seenPeriods.add(period0);
            totalOrders += orders;
            totalRevenue = totalRevenue.add(revenue);
            totalRefunded = totalRefunded.add(refunded);
        }
        // Periods that had refunds but no gross revenue (rare) — surface as negative net.
        for (Map.Entry<String, BigDecimal> e : refunds.entrySet()) {
            if (!seenPeriods.contains(e.getKey())) {
                buckets.add(new RevenueBucketRow(e.getKey(), 0, BigDecimal.ZERO, e.getValue(), e.getValue().negate()));
                totalRefunded = totalRefunded.add(e.getValue());
            }
        }
        return new RevenueReportResponse(period, from, to, label,
                totalRevenue, totalRefunded, totalRevenue.subtract(totalRefunded), totalOrders, buckets);
    }

    /** period label -> allocated refund amount, from a [period, refunded] row list. */
    private Map<String, BigDecimal> refundMap(List<Object[]> rows) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put(periodLabel(r[0]), (BigDecimal) r[1]);
        }
        return map;
    }

    /** date_trunc returns a timestamp; keep just the date portion as the bucket label. */
    private String periodLabel(Object raw) {
        if (raw == null) return null;
        String s = raw.toString();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    /**
     * Default window when no dates are supplied — each granularity defaults to the
     * current calendar unit it lives in (overridable via the date filter):
     *   DAILY   → current week (days of this week)
     *   WEEKLY  → current month (weeks of this month)
     *   MONTHLY → current year (months of this year)
     *   YEARLY  → current year (this year's total)
     */
    private LocalDate[] resolveWindow(RevenuePeriod period, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate end = to != null ? to : today;
        LocalDate start = from;
        if (start == null) {
            start = switch (period) {
                case DAILY -> today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                case WEEKLY -> today.withDayOfMonth(1);
                case MONTHLY -> today.withDayOfYear(1);
                case YEARLY -> today.withDayOfYear(1);
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
