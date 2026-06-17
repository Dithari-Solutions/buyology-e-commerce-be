package com.buyology.ecommerce.revenue.service;

import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.buyology.ecommerce.revenue.dto.RevenueBucketRow;
import com.buyology.ecommerce.revenue.dto.RevenueOrderRow;
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
    private final PaymentTransactionRepository paymentTransactionRepository;

    public RevenueService(OrderItemRepository orderItemRepository, SupplierRepository supplierRepository,
                          PaymentTransactionRepository paymentTransactionRepository) {
        this.orderItemRepository = orderItemRepository;
        this.supplierRepository = supplierRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
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
        List<RevenueOrderRow> orders = orderRows(orderItemRepository.platformOrderRows(fromI, toI, store));
        // Courier return-pickup fees are platform-level delivery revenue (settlement currency).
        Map<String, BigDecimal> deliveryFees = bucketMap(
                paymentTransactionRepository.courierFeeRevenueBuckets(period.getTruncUnit(), fromI, toI));
        return buildReport(period, window[0], window[1], "Buyology", rows, refunds, orders, deliveryFees);
    }

    public RevenueReportResponse supplierReport(UUID supplierId, RevenuePeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolveWindow(period, from, to);
        Instant fromI = startInstant(window[0]);
        Instant toI = endInstant(window[1]);
        List<Object[]> rows = orderItemRepository.supplierRevenueBuckets(supplierId, period.getTruncUnit(), fromI, toI);
        Map<String, BigDecimal> refunds = refundMap(
                orderItemRepository.supplierRefundBuckets(supplierId, period.getTruncUnit(), fromI, toI));
        List<RevenueOrderRow> orders = orderRows(orderItemRepository.supplierOrderRows(supplierId, fromI, toI));
        String label = supplierRepository.findById(supplierId)
                .map(Supplier::getBusinessName)
                .orElse(supplierId.toString());
        // Delivery (courier return-pickup) fees are platform revenue, never a supplier's.
        return buildReport(period, window[0], window[1], label, rows, refunds, orders, Map.of());
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
            List<Object[]> rows, Map<String, BigDecimal> refunds, List<RevenueOrderRow> orders,
            Map<String, BigDecimal> deliveryFees) {
        List<RevenueBucketRow> buckets = new ArrayList<>();
        long totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        BigDecimal totalDeliveryFee = BigDecimal.ZERO;
        Set<String> seenPeriods = new LinkedHashSet<>();
        for (Object[] row : rows) {
            String period0 = periodLabel(row[0]);
            long bucketOrders = ((Number) row[1]).longValue();
            BigDecimal revenue = (BigDecimal) row[2];
            BigDecimal refunded = refunds.getOrDefault(period0, BigDecimal.ZERO);
            BigDecimal deliveryFee = deliveryFees.getOrDefault(period0, BigDecimal.ZERO);
            buckets.add(new RevenueBucketRow(period0, bucketOrders, revenue, refunded, revenue.subtract(refunded), deliveryFee));
            seenPeriods.add(period0);
            totalOrders += bucketOrders;
            totalRevenue = totalRevenue.add(revenue);
            totalRefunded = totalRefunded.add(refunded);
            totalDeliveryFee = totalDeliveryFee.add(deliveryFee);
        }
        // Periods that had refunds and/or delivery fees but no gross product revenue (rare):
        // surface them so the bucket list and totals stay complete.
        Set<String> extraPeriods = new LinkedHashSet<>();
        refunds.keySet().forEach(p -> { if (!seenPeriods.contains(p)) extraPeriods.add(p); });
        deliveryFees.keySet().forEach(p -> { if (!seenPeriods.contains(p)) extraPeriods.add(p); });
        for (String p : extraPeriods) {
            BigDecimal refunded = refunds.getOrDefault(p, BigDecimal.ZERO);
            BigDecimal deliveryFee = deliveryFees.getOrDefault(p, BigDecimal.ZERO);
            buckets.add(new RevenueBucketRow(p, 0, BigDecimal.ZERO, refunded, refunded.negate(), deliveryFee));
            totalRefunded = totalRefunded.add(refunded);
            totalDeliveryFee = totalDeliveryFee.add(deliveryFee);
        }
        return new RevenueReportResponse(period, from, to, label,
                totalRevenue, totalRefunded, totalRevenue.subtract(totalRefunded), totalOrders,
                buckets, orders == null ? List.of() : orders, totalDeliveryFee);
    }

    /** period label -> allocated refund amount, from a [period, refunded] row list. */
    private Map<String, BigDecimal> refundMap(List<Object[]> rows) {
        return bucketMap(rows);
    }

    /** period label -> amount, from a [period, amount] row list. */
    private Map<String, BigDecimal> bucketMap(List<Object[]> rows) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put(periodLabel(r[0]), (BigDecimal) r[1]);
        }
        return map;
    }

    /** [order_id, created_at, gross, refunded] rows -> per-order revenue rows. */
    private List<RevenueOrderRow> orderRows(List<Object[]> rows) {
        List<RevenueOrderRow> result = new ArrayList<>();
        for (Object[] r : rows) {
            String orderId = r[0] != null ? r[0].toString() : null;
            String createdAt = instantString(r[1]);
            BigDecimal gross = r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO;
            BigDecimal refunded = r[3] != null ? (BigDecimal) r[3] : BigDecimal.ZERO;
            result.add(new RevenueOrderRow(orderId, createdAt, gross, refunded, gross.subtract(refunded)));
        }
        return result;
    }

    /** Render a JDBC timestamp/instant column as an ISO-8601 string. */
    private String instantString(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.sql.Timestamp ts) return ts.toInstant().toString();
        if (raw instanceof Instant i) return i.toString();
        return raw.toString();
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
