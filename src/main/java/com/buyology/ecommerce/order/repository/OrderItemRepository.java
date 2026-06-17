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

    /** Buyology's own revenue: order items for platform-owned products (supplier_id IS NULL).
     *  Optional store filter (pass null storeId for all stores). */
    @Query(value = """
            SELECT date_trunc(:bucket, oi.created_at) AS period,
                   o.currency AS currency,
                   COUNT(DISTINCT oi.order_id) AS orders,
                   COALESCE(SUM(oi.total_price), 0) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.supplier_id IS NULL
              AND oi.created_at >= :from
              AND oi.created_at < :to
              AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'FAILED')
              AND (CAST(:storeId AS uuid) IS NULL OR oi.store_id = CAST(:storeId AS uuid))
            GROUP BY 1, o.currency
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> platformRevenueBuckets(
            @Param("bucket") String bucket,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("storeId") String storeId);

    /** A single supplier's revenue, bucketed by the given unit. */
    @Query(value = """
            SELECT date_trunc(:bucket, oi.created_at) AS period,
                   o.currency AS currency,
                   COUNT(DISTINCT oi.order_id) AS orders,
                   COALESCE(SUM(oi.total_price), 0) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.supplier_id = :supplierId
              AND oi.created_at >= :from
              AND oi.created_at < :to
              AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'FAILED')
            GROUP BY 1, o.currency
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
                   o.currency AS currency,
                   COUNT(DISTINCT oi.order_id) AS orders,
                   COALESCE(SUM(oi.total_price), 0) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.supplier_id IS NOT NULL
              AND oi.created_at >= :from
              AND oi.created_at < :to
              AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'FAILED')
            GROUP BY oi.supplier_id, o.currency
            ORDER BY 4 DESC
            """, nativeQuery = true)
    List<Object[]> supplierRevenueTotals(
            @Param("from") Instant from,
            @Param("to") Instant to);

    // ── Refunds netted against revenue ───────────────────────────────────────
    // Refunds are counted from payment_refunds with status='SUCCESS' (money actually
    // returned via Paymob). This captures BOTH customer refund requests AND order-
    // cancellation auto-refunds (which create a PaymentRefund but no RefundRequest) —
    // a PENDING Paymob refund is not yet counted. A scope's (platform or one supplier)
    // share is allocated proportionally to its items' value: refund_amount *
    // scope_item_total / order_total, bucketed by order item created_at.

    /** Platform (supplier_id IS NULL) refund allocation, bucketed. Optional store filter. */
    @Query(value = """
            SELECT date_trunc(:bucket, oi.created_at) AS period,
                   COALESCE(SUM(pr.amount * oi.total_price / NULLIF(o.total_amount, 0)), 0) AS refunded
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN payment_transactions pt ON pt.app_order_id = o.id
            JOIN payment_refunds pr ON pr.transaction_id = pt.id AND pr.status = 'SUCCESS'
            WHERE oi.supplier_id IS NULL
              AND oi.created_at >= :from
              AND oi.created_at < :to
              AND (CAST(:storeId AS uuid) IS NULL OR oi.store_id = CAST(:storeId AS uuid))
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> platformRefundBuckets(
            @Param("bucket") String bucket,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("storeId") String storeId);

    /** A single supplier's refund allocation, bucketed. */
    @Query(value = """
            SELECT date_trunc(:bucket, oi.created_at) AS period,
                   COALESCE(SUM(pr.amount * oi.total_price / NULLIF(o.total_amount, 0)), 0) AS refunded
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN payment_transactions pt ON pt.app_order_id = o.id
            JOIN payment_refunds pr ON pr.transaction_id = pt.id AND pr.status = 'SUCCESS'
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
                   COALESCE(SUM(pr.amount * oi.total_price / NULLIF(o.total_amount, 0)), 0) AS refunded
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            JOIN payment_transactions pt ON pt.app_order_id = o.id
            JOIN payment_refunds pr ON pr.transaction_id = pt.id AND pr.status = 'SUCCESS'
            WHERE oi.supplier_id IS NOT NULL
              AND oi.created_at >= :from
              AND oi.created_at < :to
            GROUP BY oi.supplier_id
            """, nativeQuery = true)
    List<Object[]> supplierRefundTotals(
            @Param("from") Instant from,
            @Param("to") Instant to);

    // ── Order-level revenue rows (one row per order, not bucketed) ───────────
    // gross = scope's item total on the order; refunded = order's PAID refunds
    // allocated to the scope by item-value share; net is computed in Java.

    /** Platform (supplier_id IS NULL) revenue, one row per order. Optional store filter. */
    @Query(value = """
            SELECT CAST(o.id AS text) AS order_id,
                   MIN(oi.created_at) AS created_at,
                   o.currency AS currency,
                   SUM(oi.total_price) AS gross,
                   COALESCE((SELECT COALESCE(SUM(pr.amount), 0)
                             FROM payment_refunds pr
                             JOIN payment_transactions pt ON pt.id = pr.transaction_id
                             WHERE pt.app_order_id = o.id AND pr.status = 'SUCCESS'), 0)
                       * SUM(oi.total_price) / NULLIF(o.total_amount, 0) AS refunded
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.supplier_id IS NULL
              AND oi.created_at >= :from
              AND oi.created_at < :to
              AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'FAILED')
              AND (CAST(:storeId AS uuid) IS NULL OR oi.store_id = CAST(:storeId AS uuid))
            GROUP BY o.id, o.total_amount, o.currency
            ORDER BY MIN(oi.created_at) DESC
            """, nativeQuery = true)
    List<Object[]> platformOrderRows(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("storeId") String storeId);

    /** A single supplier's revenue, one row per order. */
    @Query(value = """
            SELECT CAST(o.id AS text) AS order_id,
                   MIN(oi.created_at) AS created_at,
                   o.currency AS currency,
                   SUM(oi.total_price) AS gross,
                   COALESCE((SELECT COALESCE(SUM(pr.amount), 0)
                             FROM payment_refunds pr
                             JOIN payment_transactions pt ON pt.id = pr.transaction_id
                             WHERE pt.app_order_id = o.id AND pr.status = 'SUCCESS'), 0)
                       * SUM(oi.total_price) / NULLIF(o.total_amount, 0) AS refunded
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE oi.supplier_id = :supplierId
              AND oi.created_at >= :from
              AND oi.created_at < :to
              AND o.status NOT IN ('PENDING_PAYMENT', 'CANCELLED', 'FAILED')
            GROUP BY o.id, o.total_amount, o.currency
            ORDER BY MIN(oi.created_at) DESC
            """, nativeQuery = true)
    List<Object[]> supplierOrderRows(
            @Param("supplierId") UUID supplierId,
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
