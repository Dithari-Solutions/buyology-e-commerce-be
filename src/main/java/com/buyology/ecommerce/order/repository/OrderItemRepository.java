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
