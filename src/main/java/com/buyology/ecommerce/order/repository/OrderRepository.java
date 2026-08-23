package com.buyology.ecommerce.order.repository;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.time.Instant;
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findAllByUserId(UUID userId, Pageable pageable);

    Page<Order> findAll(Pageable pageable);

    Page<Order> findAllByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findAllByDeliveryMethod(DeliveryMethod deliveryMethod, Pageable pageable);

    Page<Order> findAllByStatusAndDeliveryMethod(OrderStatus status, DeliveryMethod deliveryMethod, Pageable pageable);

    List<Order> findAllByCourierUserIdAndDeliveryMethod(UUID courierUserId, DeliveryMethod deliveryMethod);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    Optional<Order> findByDeliveryOrderId(UUID deliveryOrderId);

    // Idempotency for createOrder: detect an order already produced from this cart so a
    // re-entered checkout (e.g. app killed mid-payment, then re-paid) reuses it instead
    // of creating a duplicate order + charge.
    Optional<Order> findFirstByCartIdAndStatusIn(UUID cartId, List<OrderStatus> statuses);

    /**
     * Every order this cart has produced that is still payable or paid, newest first.
     *
     * <p>createOrder classifies ALL of them, not just the newest: a bug or a race that left two
     * PENDING_PAYMENT orders on one cart would otherwise keep the older one forever invisible,
     * holding its stock and its promo claim.
     */
    List<Order> findAllByCartIdAndStatusInOrderByCreatedAtDesc(UUID cartId, List<OrderStatus> statuses);

    List<Order> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, OrderStatus status);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i " +
           "WHERE (:status IS NULL OR o.status = :status) " +
           "AND (:deliveryMethod IS NULL OR o.deliveryMethod = :deliveryMethod) " +
           "AND (:storeId IS NULL OR i.storeId = :storeId) " +
           "AND (:supplierId IS NULL OR i.supplierId = :supplierId)")
    Page<Order> findAllWithFilters(@Param("status") OrderStatus status,
                                   @Param("deliveryMethod") DeliveryMethod deliveryMethod,
                                   @Param("storeId") UUID storeId,
                                   @Param("supplierId") UUID supplierId,
                                   Pageable pageable);

    /** Orders that contain at least one item belonging to the given supplier. */
    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i " +
           "WHERE i.supplierId = :supplierId " +
           "AND (:status IS NULL OR o.status = :status)")
    Page<Order> findBySupplierId(@Param("supplierId") UUID supplierId,
                                 @Param("status") OrderStatus status,
                                 Pageable pageable);

    // ── Quiqup delivery dispatch ─────────────────────────────────────────────

    /**
     * Paid orders Quiqup never accepted: no quiqup_order_id, paid long enough ago that an in-flight
     * attempt has had its chance, and recent enough to still be worth delivering.
     *
     * <p>The horizon matters. Without it a permanently unmappable order — one spanning two stores,
     * say — is retried every five minutes forever, and the log noise buries the orders a retry
     * could actually save.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.quiqupOrderId IS NULL
              AND o.status IN (com.buyology.ecommerce.order.domain.enums.OrderStatus.PAID,
                               com.buyology.ecommerce.order.domain.enums.OrderStatus.PACKAGING)
              AND o.deliveryMethod = com.buyology.ecommerce.order.domain.enums.DeliveryMethod.REGULAR
              AND o.createdAt < :olderThan
              AND o.createdAt > :horizon
            ORDER BY o.createdAt ASC
            """)
    List<Order> findUndispatchedQuiqupOrders(@Param("olderThan") Instant olderThan,
                                             @Param("horizon") Instant horizon,
                                             org.springframework.data.domain.Pageable pageable);

    /**
     * Claims one order for dispatch, atomically. Returns 1 to the single winner, 0 to everyone else.
     *
     * <p>This is the whole cluster-safety story for dispatch. The condition and the write happen in
     * one statement, so two replicas racing the same order cannot both pass it — the database
     * serialises them and the loser sees 0 rows affected and stops. Checking a field and then
     * writing it would leave exactly the gap this closes, and that gap is the width of an HTTP call
     * to Quiqup.
     *
     * <p>A claim older than {@code staleBefore} is reclaimable so an instance that died mid-call
     * does not strand the order; the window must exceed the Quiqup request timeout, or a slow call
     * could be dispatched twice by the very mechanism meant to prevent it.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Order o SET o.quiqupDispatchClaimedAt = :now
            WHERE o.id = :orderId
              AND o.quiqupOrderId IS NULL
              AND (o.quiqupDispatchClaimedAt IS NULL OR o.quiqupDispatchClaimedAt < :staleBefore)
            """)
    int claimForQuiqupDispatch(@Param("orderId") UUID orderId,
                               @Param("now") Instant now,
                               @Param("staleBefore") Instant staleBefore);

    /**
     * Releases a claim after a failed attempt, so the retry job can pick the order up immediately
     * rather than waiting out the stale window.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.quiqupDispatchClaimedAt = NULL WHERE o.id = :orderId AND o.quiqupOrderId IS NULL")
    int releaseQuiqupDispatchClaim(@Param("orderId") UUID orderId);

    // ── Quiqup cancel ────────────────────────────────────────────────────────

    /**
     * Wins the right to talk to Quiqup about cancelling this order's job.
     *
     * <p>Condition and write are one statement, so two replicas cannot both pass it — the same
     * shape as {@link #claimForQuiqupDispatch} and for the same reason. {@code staleBefore} must
     * comfortably exceed the Quiqup request timeout: a claim expiring while the call is still in
     * flight would let the retry fire a second cancel, which is harmless at Quiqup but races the
     * refund gate.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Order o SET o.quiqupCancelClaimedAt = :now
            WHERE o.id = :orderId
              AND o.quiqupOrderId IS NOT NULL
              AND (o.quiqupCancelStatus IS NULL OR o.quiqupCancelStatus IN ('PENDING', 'UNCONFIRMED'))
              AND (o.quiqupCancelClaimedAt IS NULL OR o.quiqupCancelClaimedAt < :staleBefore)
            """)
    int claimForQuiqupCancel(@Param("orderId") UUID orderId,
                             @Param("now") Instant now,
                             @Param("staleBefore") Instant staleBefore);

    /**
     * Cancelled orders whose Quiqup job is not yet confirmed stopped — the retry job's worklist.
     *
     * <p>Bounded by {@code horizon} (quiqup.cancel.deadline-minutes): past it a courier is not
     * waiting on us any more and the case has already been escalated to a human.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.status = com.buyology.ecommerce.order.domain.enums.OrderStatus.CANCELLED
              AND o.quiqupOrderId IS NOT NULL
              AND o.quiqupCancelStatus IN ('PENDING', 'UNCONFIRMED')
              AND o.cancelledAt > :horizon
            ORDER BY o.cancelledAt ASC
            """)
    List<Order> findOrdersNeedingQuiqupCancel(@Param("horizon") Instant horizon, Pageable pageable);

    /** Hands the cancel claim back after an attempt, so a retry need not wait out the stale window. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.quiqupCancelClaimedAt = NULL WHERE o.id = :orderId")
    int releaseQuiqupCancelClaim(@Param("orderId") UUID orderId);

    /** Resolve an inbound Quiqup webhook back to the order it concerns. */
    Optional<Order> findByQuiqupOrderId(String quiqupOrderId);

    /**
     * Orders whose id starts with the given lowercase hex prefix.
     *
     * <p>Only for resolving the "BUY-XXXXXXXX" reference we hand Quiqup, which carries the first
     * eight characters of the order id. Native because the cast is Postgres-specific, and returning
     * a list rather than one row on purpose: the caller treats an ambiguous prefix as no match
     * instead of moving whichever order happened to sort first.
     */
    @Query(value = "SELECT * FROM orders WHERE CAST(id AS text) LIKE :prefix || '%'", nativeQuery = true)
    List<Order> findByIdPrefix(@Param("prefix") String prefix);
}
