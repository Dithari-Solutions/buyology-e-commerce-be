package com.buyology.ecommerce.order.repository;

import com.buyology.ecommerce.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findAllByOrderId(UUID orderId);

    @Query("SELECT COUNT(i) FROM OrderItem i WHERE i.supplierId = :supplierId")
    long countBySupplierId(@Param("supplierId") UUID supplierId);

    @Query("SELECT COALESCE(SUM(i.totalPrice), 0) FROM OrderItem i WHERE i.supplierId = :supplierId")
    BigDecimal sumRevenueBySupplierId(@Param("supplierId") UUID supplierId);

    @Query("""
            SELECT COUNT(i), COALESCE(SUM(i.totalPrice), 0)
            FROM OrderItem i
            WHERE i.supplierId = :supplierId
              AND i.createdAt >= :from
              AND i.createdAt < :to
            """)
    List<Object[]> countAndRevenueBySupplierId(
            @Param("supplierId") UUID supplierId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT FUNCTION('date_trunc', 'month', i.createdAt), COUNT(i), COALESCE(SUM(i.totalPrice), 0)
            FROM OrderItem i
            WHERE i.supplierId = :supplierId
              AND i.createdAt >= :from
              AND i.createdAt < :to
            GROUP BY FUNCTION('date_trunc', 'month', i.createdAt)
            ORDER BY FUNCTION('date_trunc', 'month', i.createdAt)
            """)
    List<Object[]> monthlyStatsBySupplierId(
            @Param("supplierId") UUID supplierId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    // ── Revenue reporting (bucketed by day/week/month/year) ──────────────────
    // "Revenue-counting" orders = paid orders that were not cancelled/failed,
    // i.e. status NOT IN (PENDING_PAYMENT, CANCELLED, FAILED). The bucket unit
    // ('day'|'week'|'month'|'year') is passed to Postgres date_trunc.
    //
    // These are native queries: date_trunc's unit is referenced once and the
    // result is grouped by ordinal position (GROUP BY 1). A JPQL FUNCTION()
    // form would emit the unit param twice (SELECT + GROUP BY) as two distinct
    // placeholders, which Postgres rejects as a non-matching GROUP BY expression.

    /** Buyology's own revenue: order items for platform-owned products (supplier_id IS NULL). */
    @Query(value = """
            SELECT date_trunc(:bucket, oi.created_at) AS period,
                   COUNT(DISTINCT oi.order_id) AS orders,
                   COALESCE(SUM(oi.total_price), 0) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.supplier_id IS NULL
              AND oi.created_at >= :from
              AND oi.created_at < :to
              AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'FAILED')
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> platformRevenueBuckets(
            @Param("bucket") String bucket,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** A single supplier's revenue, bucketed by the given unit. */
    @Query(value = """
            SELECT date_trunc(:bucket, oi.created_at) AS period,
                   COUNT(DISTINCT oi.order_id) AS orders,
                   COALESCE(SUM(oi.total_price), 0) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.supplier_id = :supplierId
              AND oi.created_at >= :from
              AND oi.created_at < :to
              AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'FAILED')
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> supplierRevenueBuckets(
            @Param("supplierId") UUID supplierId,
            @Param("bucket") String bucket,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Per-supplier revenue totals over a window — powers the all-suppliers overview. */
    @Query(value = """
            SELECT oi.supplier_id AS supplier_id,
                   COUNT(DISTINCT oi.order_id) AS orders,
                   COALESCE(SUM(oi.total_price), 0) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.supplier_id IS NOT NULL
              AND oi.created_at >= :from
              AND oi.created_at < :to
              AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'FAILED')
            GROUP BY oi.supplier_id
            ORDER BY 3 DESC
            """, nativeQuery = true)
    List<Object[]> supplierRevenueTotals(
            @Param("from") Instant from,
            @Param("to") Instant to);

    // ── Refunds netted against revenue ───────────────────────────────────────
    // Refunds are per-order (RefundRequest.refundAmount is the whole-order total)
    // and only count once money is actually returned (status = 'PAID'). A scope's
    // (platform or one supplier) share of a refund is allocated proportionally to
    // its items' value: refund_amount * scope_item_total / order_total. Bucketed
    // by order item created_at so a refund reduces the period the revenue was booked.

    /** Platform (supplier_id IS NULL) refund allocation, bucketed. */
    @Query(value = """
            SELECT date_trunc(:bucket, oi.created_at) AS period,
                   COALESCE(SUM(rr.refund_amount * oi.total_price / NULLIF(o.total_amount, 0)), 0) AS refunded
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN refund_requests rr ON rr.order_id = o.id AND rr.status = 'PAID'
            WHERE oi.supplier_id IS NULL
              AND oi.created_at >= :from
              AND oi.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> platformRefundBuckets(
            @Param("bucket") String bucket,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** A single supplier's refund allocation, bucketed. */
    @Query(value = """
            SELECT date_trunc(:bucket, oi.created_at) AS period,
                   COALESCE(SUM(rr.refund_amount * oi.total_price / NULLIF(o.total_amount, 0)), 0) AS refunded
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN refund_requests rr ON rr.order_id = o.id AND rr.status = 'PAID'
            WHERE oi.supplier_id = :supplierId
              AND oi.created_at >= :from
              AND oi.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> supplierRefundBuckets(
            @Param("supplierId") UUID supplierId,
            @Param("bucket") String bucket,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Per-supplier refund allocation totals over a window — for the all-suppliers overview. */
    @Query(value = """
            SELECT oi.supplier_id AS supplier_id,
                   COALESCE(SUM(rr.refund_amount * oi.total_price / NULLIF(o.total_amount, 0)), 0) AS refunded
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN refund_requests rr ON rr.order_id = o.id AND rr.status = 'PAID'
            WHERE oi.supplier_id IS NOT NULL
              AND oi.created_at >= :from
              AND oi.created_at < :to
            GROUP BY oi.supplier_id
            """, nativeQuery = true)
    List<Object[]> supplierRefundTotals(
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Items owed to a supplier that are not yet locked into a non-rejected payout
     * request and whose order has been delivered and is not under an active refund
     * request. Used to compute the supplier's currently-owed payout amount.
     */
    @Query("""
            SELECT i FROM OrderItem i JOIN i.order o
            WHERE i.supplierId = :supplierId
              AND o.status = com.buyology.ecommerce.order.domain.enums.OrderStatus.DELIVERED
              AND NOT EXISTS (
                  SELECT 1 FROM com.buyology.ecommerce.payout.domain.PayoutRequestOrderItem proi,
                         com.buyology.ecommerce.payout.domain.PayoutRequest pr
                  WHERE proi.orderItemId = i.id
                    AND proi.payoutRequestId = pr.id
                    AND pr.status <> com.buyology.ecommerce.payout.enums.PayoutRequestStatus.REJECTED)
              AND NOT EXISTS (
                  SELECT 1 FROM com.buyology.ecommerce.refund.domain.RefundRequest rr
                  WHERE rr.orderId = o.id
                    AND rr.status <> com.buyology.ecommerce.refund.enums.RefundRequestStatus.REJECTED
                    AND rr.status <> com.buyology.ecommerce.refund.enums.RefundRequestStatus.FAILED)
            """)
    List<OrderItem> findPayoutEligibleBySupplierId(@Param("supplierId") UUID supplierId);
}
