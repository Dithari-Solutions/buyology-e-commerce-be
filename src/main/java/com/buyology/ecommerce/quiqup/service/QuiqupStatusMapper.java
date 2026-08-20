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

        // Their documented vocabulary, matched EXACTLY. Substring matching was doing the work here
        // and it is not safe on these names, because several of them contain the word for a
        // different stage than the one they mean:
        //
        //   ready_for_collection  contains "collection" — nothing has been collected; the parcel is
        //                         packed and waiting, and calling it collected tells a customer a
        //                         courier holds their order when none does
        //   out_for_collection    contains "out_for"    — the courier is driving TO THE SHOP, not to
        //                         the customer; reading it as in-transit skips a whole leg
        //   out_for_delivery      contains "delivery"   — still in the van, not delivered
        //   delivery_failed       contains "delivery"   — the opposite of a delivery
        //
        // An exact table cannot be fooled by any of that.
        OrderStatus known = switch (s) {
            case "pending", "picking_started", "ready_for_collection",
                 "at_depot", "received_at_depot"          -> null;
            case "out_for_collection", "collected"        -> OrderStatus.IN_COURIER;
            case "in_transit", "out_for_delivery"         -> OrderStatus.IN_TRANSIT;
            case "delivery_complete", "delivered"         -> OrderStatus.DELIVERED;
            case "delivery_failed", "collection_failed"   -> OrderStatus.FAILED;
            case "out_for_return", "returned_to_origin"   -> OrderStatus.FAILED;
            case "cancelled", "canceled"                  -> OrderStatus.CANCELLED;
            default                                       -> UNKNOWN;
        };
        if (known != UNKNOWN) {
            return known;
        }

        // Unknown to us. Quiqup can add a state without telling us, so fall back to heuristics —
        // but only the ones that cannot be misread. Ordering is load-bearing: failure words beat
        // everything, and the in-flight phrases that contain "deliver" are tested before "deliver"
        // itself, or "out_for_delivery"-shaped names mark an order DELIVERED, which is terminal and
        // is never corrected.
        if (s.contains("cancel")) return OrderStatus.CANCELLED;
        if (s.contains("fail") || s.contains("reject") || s.contains("return")) return OrderStatus.FAILED;
        if (s.contains("out_for_collection") || s.contains("ready_for")) return null;
        if (s.contains("transit") || s.contains("on_the_way") || s.contains("out_for")
                || s.contains("dropoff") || s.contains("arriv")) {
            return OrderStatus.IN_TRANSIT;
        }
        if (s.contains("deliver")) return OrderStatus.DELIVERED;
        // "pickup"/"picked" are safe where a bare "collect" is not: no documented state contains
        // them, whereas "collection" appears in ready_for_collection, which means the opposite.
        if (s.contains("collected") || s.contains("picked") || s.contains("pickup")
                || s.contains("accept") || s.contains("assign") || s.contains("courier")) {
            return OrderStatus.IN_COURIER;
        }
        return null;
    }

    /**
     * Distinguishes "the table says this state changes nothing" from "the table has never heard of
     * this state". Both would otherwise be null, and they call for opposite handling: the first is
     * an answer, the second means fall through to the heuristics.
     */
    private static final OrderStatus UNKNOWN = OrderStatus.PENDING_PAYMENT;

    /** A short line for the order's tracking history, so support can see where a status came from. */
    public static String trackingNote(String quiqupStatus) {
        return "Quiqup delivery status: " + (quiqupStatus == null ? "unknown" : quiqupStatus);
    }
}
