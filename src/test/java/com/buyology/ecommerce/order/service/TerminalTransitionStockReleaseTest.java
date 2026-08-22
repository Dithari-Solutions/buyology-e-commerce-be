package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the two halves of the stock guarantee: which transitions give units back, and the fact that
 * no status write can skip the place that decides.
 *
 * <p>The defect this replaces was not a wrong branch — it was six right-looking branches, each of
 * which independently forgot. Six places that must all remember will not all remember, so the fix
 * was to make the release something a status change does rather than something a branch calls. The
 * scan below is what keeps it that way: a new terminal branch written next year gets the release
 * by writing its status the only way OrderService allows, and a plain setStatus reintroducing the
 * leak fails here instead of quietly bleeding inventory.
 */
class TerminalTransitionStockReleaseTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/buyology/ecommerce/order/service/OrderService.java");

    // ── The policy ───────────────────────────────────────────────────────────

    @Test
    void everyCancellationPutsTheUnitsBack() {
        // Cancelling is only allowed up to IN_COURIER and the business already auto-refunds
        // there, so the goods are coming back to us in every case.
        for (OrderStatus from : new OrderStatus[]{
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PACKAGING,
                OrderStatus.READY_FOR_PICKUP, OrderStatus.IN_COURIER}) {
            assertTrue(OrderService.releasesStock(from, OrderStatus.CANCELLED),
                    "cancelling from " + from + " must return the stock");
        }
    }

    @Test
    void aFailedPaymentPutsTheUnitsBack() {
        // Nothing ever left the building. This is the common case — a declined card — and the one
        // that silently ate three units off a five-unit variant in the original bug.
        assertTrue(OrderService.releasesStock(OrderStatus.PENDING_PAYMENT, OrderStatus.FAILED));
    }

    @Test
    void aFailedDeliveryDoesNotPutTheUnitsBack() {
        // That parcel is with a courier or on its way back to us. Inventing a sellable unit for it
        // oversells the last one, and whether a human refunds or redelivers is already open.
        for (OrderStatus from : new OrderStatus[]{
                OrderStatus.READY_FOR_PICKUP, OrderStatus.IN_COURIER, OrderStatus.IN_TRANSIT}) {
            assertFalse(OrderService.releasesStock(from, OrderStatus.FAILED),
                    "a parcel already out at " + from + " is not on any shelf");
        }
    }

    @Test
    void progressingAnOrderNeverTouchesStock() {
        // The units are legitimately gone — they were sold. Releasing here would be the mirror
        // failure: the shop believes it holds inventory it has already shipped.
        for (OrderStatus to : new OrderStatus[]{
                OrderStatus.PAID, OrderStatus.PACKAGING, OrderStatus.IN_COURIER,
                OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED, OrderStatus.READY_FOR_PICKUP}) {
            assertFalse(OrderService.releasesStock(OrderStatus.PENDING_PAYMENT, to),
                    "moving to " + to + " must not return stock");
        }
    }

    // ── The guarantee ────────────────────────────────────────────────────────

    @Test
    void noStatusWriteEscapesTheChokePoint() throws Exception {
        // The whole point. If this fails, someone has written a status directly and that branch
        // silently keeps the customer's units.
        List<String> offenders = new ArrayList<>();
        String[] lines = Files.readString(SOURCE).split("\n");
        boolean insideChokePoint = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("private void transitionTo(Order order, OrderStatus target)")) {
                insideChokePoint = true;
                continue;
            }
            // transitionTo is the one method allowed to write a status; it ends at the next
            // method declaration.
            if (insideChokePoint && line.startsWith("    /**")) {
                insideChokePoint = false;
            }
            if (insideChokePoint) {
                continue;
            }
            // Any receiver, not a hand-listed set of variable names: a future branch that
            // calls its local something else must still be caught.
            if (line.matches(".*[A-Za-z0-9_]\\.setStatus\\(\\s*(OrderStatus|target|newStatus|req\\.getStatus).*")) {
                offenders.add((i + 1) + ": " + line.trim());
            }
        }

        assertTrue(offenders.isEmpty(),
                "these write an order status without going through transitionTo, so they never "
                        + "return the customer's stock:\n  " + String.join("\n  ", offenders));
    }

    @Test
    void theChokePointStillCallsTheReleaseService() throws Exception {
        // Guards against the release being "tidied out" of transitionTo while every call site
        // still routes through it — which would look completely clean and leak everything.
        String source = Files.readString(SOURCE);
        int start = source.indexOf("private void transitionTo(Order order, OrderStatus target)");
        assertTrue(start > 0, "transitionTo not found");
        String body = source.substring(start, source.indexOf("\n    }", start));

        assertTrue(body.contains("releasesStock("), "transitionTo must consult the release policy");
        assertTrue(body.contains("stockReservationService.releaseForOrder(order)"),
                "transitionTo must actually return the stock:\n" + body);
    }

    @Test
    void theReleaseIsNotDeferredToAfterCommit() throws Exception {
        // It must share the caller's transaction. Split off into an after-commit callback or a
        // REQUIRES_NEW, it would block on the orders row lock its own caller holds — with no
        // deadlock detection — and lose the @Version protection that makes it exactly-once.
        String source = Files.readString(SOURCE);
        int start = source.indexOf("private void transitionTo(Order order, OrderStatus target)");
        String body = source.substring(start, source.indexOf("\n    }", start));

        assertFalse(body.contains("runAfterCommit"),
                "the stock release must be atomic with the status write, not after-commit");
    }
}
