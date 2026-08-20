package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.enums.OrderStatus;

import java.util.Locale;

/**
 * Translates Quiqup's delivery vocabulary into our order statuses.
 *
 * <p>Kept separate from the order layer on purpose: this class owns "what does Quiqup call this",
 * while {@code OrderService} owns "is that transition allowed". Mixing the two is how a carrier's
 * naming quirk ends up encoded in our state machine.
 *
 * <p>Unrecognised statuses map to null and are recorded but not acted on. That is the right
 * default for a partner whose event list we do not control: a status we have never seen is far
 * more likely to be a new intermediate step than a delivery outcome, and guessing at it would move
 * an order on evidence we do not have.
 *
 * <p>Deliberately does NOT reuse {@code OrderService.syncStatusFromCourier}. That maps onto the
 * deprecated {@code COURIER_ASSIGNED} / {@code PICKED_UP} values, which exist for our own courier
 * fleet's historical data; a new integration should land on the current statuses instead.
 */
public final class QuiqupStatusMapper {

    private QuiqupStatusMapper() {
    }

    /**
     * The order status a Quiqup delivery status implies, or null when we do not recognise it.
     *
     * <p>Quiqup's job states progress roughly: pending → accepted/assigned → collected → in transit
     * → delivered, with cancellation and failure as terminal outcomes. The names below are matched
     * loosely — case-insensitively, and on the meaningful word rather than the whole string —
     * because their event payloads use several spellings for the same state and a punctuation
     * change should not silently stop an order from progressing.
     */
    public static OrderStatus toOrderStatus(String quiqupStatus) {
        if (quiqupStatus == null || quiqupStatus.isBlank()) {
            return null;
        }
        String s = quiqupStatus.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

        // Failure outcomes first: they must win even when a string also contains a happier word
        // (a "delivery_failed" event mentions delivery but is the opposite of one).
        if (s.contains("cancel")) return OrderStatus.CANCELLED;
        if (s.contains("fail") || s.contains("reject") || s.contains("return")) return OrderStatus.FAILED;

        // In-flight BEFORE the bare "deliver" test, because the most common way of saying a parcel
        // is still in the van — "out_for_delivery" — contains the word "delivery". Testing for
        // delivery first reads perfectly naturally and marks those orders DELIVERED, which is
        // terminal: nothing later corrects it, and the customer is told their order arrived while
        // the courier is still driving. This ordering is load-bearing, not stylistic.
        if (s.contains("transit") || s.contains("on_the_way") || s.contains("out_for")
                || s.contains("dropoff") || s.contains("arriv")) {
            return OrderStatus.IN_TRANSIT;
        }

        if (s.contains("deliver")) return OrderStatus.DELIVERED;
        if (s.contains("collect") || s.contains("picked") || s.contains("pickup")
                || s.contains("accept") || s.contains("assign") || s.contains("courier")) {
            return OrderStatus.IN_COURIER;
        }

        // "pending", "created", "scheduled" and anything unknown: recorded, not acted on. The job
        // existing at Quiqup is not itself a change to the customer's order.
        return null;
    }

    /** A short line for the order's tracking history, so support can see where a status came from. */
    public static String trackingNote(String quiqupStatus) {
        return "Quiqup delivery status: " + (quiqupStatus == null ? "unknown" : quiqupStatus);
    }
}
