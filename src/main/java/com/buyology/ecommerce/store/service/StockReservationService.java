package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Puts back everything an order took out of stock when it was created.
 *
 * <p>createOrder decrements a store listing's stock the moment an order is built, and nothing ever
 * put it back — not on a declined card, not on a customer, admin or partner cancellation, not on
 * createOrder's own cancellation of a stale prior order. Three declined attempts on a five-unit
 * variant left it at two with nothing sold, and the shop then refused sales for inventory sitting
 * on the shelf.
 */
@Service
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final OrderItemRepository orderItemRepo;
    private final StoreProductVariantRepository variantRepo;
    private final ProductRepository productRepo;

    public StockReservationService(OrderItemRepository orderItemRepo,
                                   StoreProductVariantRepository variantRepo,
                                   ProductRepository productRepo) {
        this.orderItemRepo = orderItemRepo;
        this.variantRepo = variantRepo;
        this.productRepo = productRepo;
    }

    /**
     * Returns the units an order is holding.
     *
     * <p><strong>REQUIRED, and that is the entire safety argument — this must never be made
     * REQUIRES_NEW.</strong> Two reasons, both of which have already shipped here as bugs.
     *
     * <p>First: every caller has just written the order's new status and therefore holds a lock on
     * that {@code orders} row. A REQUIRES_NEW transaction runs on a second connection, so updating
     * the same row would wait on a lock its own caller holds while the caller waits for it to
     * return — and Postgres cannot see that as a deadlock, because the caller is idle in
     * transaction rather than waiting on a lock. It hangs until something times out. That is
     * exactly how every refund in this system stopped working.
     *
     * <p>Second: joining the caller is what makes this exactly-once. The stamp lands on the very
     * row whose {@code @Version} guards it, so when two replicas cancel the same order at once,
     * one commits and the other's transaction — increments included — rolls back whole. Split
     * them apart and the increments commit independently of the version check, and the units come
     * back twice.
     *
     * <p>Idempotent on {@code stockRestoredAt}, the same shape as
     * {@code CreditReturnService.returnForCancelledOrder}: a re-cancellation, a redelivered
     * webhook or a retry finds it stamped and does nothing.
     *
     * @param order the MANAGED order entity — never a detached copy, since the stamp is flushed by
     *              the caller's transaction
     * @return true when units were actually returned
     */
    @Transactional
    public boolean releaseForOrder(Order order) {
        if (order == null || order.getId() == null) {
            return false;
        }
        if (order.getStockReservedAt() == null) {
            return false;   // never took anything — a B2B quote order, or one predating V35
        }
        if (order.getStockRestoredAt() != null) {
            return false;   // already gave it back
        }

        int variantLines = 0;
        // Sorted by variant id so that two cancellations sharing variant rows always take those
        // row locks in the same order. Unsorted, two orders holding the same two variants in
        // opposite sequence deadlock, and cancellation is about to become a routine operation.
        var items = orderItemRepo.findAllByOrderId(order.getId()).stream()
                .sorted(java.util.Comparator.comparing(
                        i -> i.getVariantId() == null ? "" : i.getVariantId().toString()))
                .toList();

        for (OrderItem item : items) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            if (qty <= 0) {
                continue;
            }

            // Real inventory. Only variant lines ever had any — this mirrors the condition in
            // OrderService.createOrder that took it.
            if (item.getVariantId() != null && item.getStoreId() != null) {
                int restored = variantRepo.incrementStock(
                        item.getStoreId(), item.getProductId(), item.getVariantId(), qty);
                if (restored == 1) {
                    variantLines++;
                } else {
                    // The listing is gone — delisted since the order was placed. Log it and carry
                    // on: one missing listing must not block a cancellation the customer has
                    // already been told about.
                    log.error("[STOCK] Order {}: could not return {} unit(s) to store {} / product "
                                    + "{} / variant {} — the listing no longer exists. Restock by hand.",
                            order.getId(), qty, item.getStoreId(), item.getProductId(), item.getVariantId());
                }
            }

            // The product's own stock, taken at order creation. A bulk statement, matching the
            // variant leg above and matching the decrement in createOrder.
            //
            // This used to go through the managed entity, because back then createOrder's
            // decrement was a read-modify-write and a bulk update here would have been overwritten
            // by that entity's flush at commit. Now that createOrder takes units with a conditional
            // statement too, neither side mutates the entity, nothing is left dirty to flush, and
            // the two compose in the stale-order path exactly as the variant statements do.
            //
            // Note this is now a true mirror of the take. While the decrement was floored at 0 and
            // this restore was not, a declined card on a sold-out product invented a unit of
            // stock: it subtracted from a count already at 0, changing nothing, then added one
            // back. The decrement refuses instead of flooring, so that asymmetry is gone.
            if (item.getProductId() != null) {
                productRepo.incrementStock(item.getProductId(), qty);
                productRepo.markInStockIfReplenished(item.getProductId());
            }
        }

        order.setStockRestoredAt(Instant.now());
        log.info("[STOCK] Order {}: returned stock for {} variant line(s)", order.getId(), variantLines);
        return true;
    }
}
