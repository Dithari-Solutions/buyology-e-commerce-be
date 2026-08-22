package com.buyology.ecommerce.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins what superseding a stale checkout order must NOT become.
 *
 * <p>supersedeStaleOrder runs inside createOrder's own transaction, and both of its "obvious
 * improvements" are regressions this codebase has already paid for once, in each direction:
 * <ul>
 *   <li>Making it REQUIRES_NEW (to "protect" the cancellation) means a createOrder rollback leaves
 *       the prior order cancelled and its promo and stock released — with NO replacement order. The
 *       customer's checkout failed and their original order died with it.
 *   <li>Routing it through applyCancellationSideEffects (to "reuse" the cancel path) refunds money
 *       a PENDING_PAYMENT order never took and emails the customer a cancellation notice in the
 *       middle of their own checkout.
 * </ul>
 */
class CheckoutSupersedeBoundaryTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/buyology/ecommerce/order/service/OrderService.java");

    @Test
    void supersedeJoinsTheCheckoutTransaction() throws Exception {
        Method m = OrderService.class.getDeclaredMethod("supersedeStaleOrder",
                com.buyology.ecommerce.order.domain.Order.class, UUID.class);
        assertTrue(Modifier.isPrivate(m.getModifiers()),
                "supersedeStaleOrder must stay private — it only makes sense inside createOrder");
        assertNull(m.getAnnotation(Transactional.class),
                "must carry NO @Transactional of its own: it stands or falls with createOrder, so "
                        + "a rolled-back checkout leaves the prior order payable with its claims intact");
    }

    @Test
    void supersedeNeverRefundsOrEmails() throws Exception {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("private void supersedeStaleOrder(");
        assertTrue(start > 0, "supersedeStaleOrder not found");
        String body = source.substring(start, source.indexOf("\n    }", start));

        assertFalse(body.contains("applyCancellationSideEffects"),
                "a PENDING_PAYMENT order took no money: the refund-and-email path must be unreachable");
        assertFalse(body.contains("emailService"),
                "no cancellation email mid-checkout");
        assertTrue(body.contains("transitionTo(stale, OrderStatus.CANCELLED)"),
                "the cancel must go through transitionTo, which is what returns the stock");
        assertTrue(body.contains("promoCodeService.releaseReservation(stale.getId())"),
                "the promo claim must come back in the same transaction");
    }

    @Test
    void thePaidShortCircuitTakesNoConditions() throws Exception {
        // A PAID order is money already taken; no coupon, address or method difference may ever
        // cause a second order for the same cart. The short-circuit must consult nothing beyond
        // the status.
        String source = Files.readString(SOURCE);
        int start = source.indexOf("for (Order prior : priorOrders) {\n            if (prior.getStatus() == OrderStatus.PAID)");
        assertTrue(start > 0,
                "the unconditional PAID short-circuit must exist before any checkout resolution");
        int planResolution = source.indexOf("resolveFulfilment(userId, req, cart");
        assertTrue(start < planResolution,
                "the PAID short-circuit must run BEFORE the checkout is resolved, so it stays "
                        + "reachable when profile/address/promo validation would throw");
    }
}
