package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins how a delivery partner's wording becomes a change to a customer's order.
 *
 * <p>Two failure directions, and they are not symmetric. Mapping too eagerly tells a customer their
 * order was delivered when it is still in a van — the more damaging of the two, because DELIVERED
 * is terminal and nothing later will correct it. Mapping too little leaves an order looking stalled
 * while it is in fact progressing, which is recoverable. The mapping is therefore deliberately
 * conservative: anything not recognised moves nothing.
 */
class QuiqupStatusMapperTest {

    @Test
    void terminalOutcomesBeatEarlierWordsInTheSameString() {
        // "delivery_failed" contains "deliver". Read left to right it looks like a delivery; it is
        // the opposite, and a customer told "delivered" for a failed drop is a support call at best.
        assertEquals(OrderStatus.FAILED, QuiqupStatusMapper.toOrderStatus("delivery_failed"));
        assertEquals(OrderStatus.FAILED, QuiqupStatusMapper.toOrderStatus("DELIVERY_REJECTED"));
        assertEquals(OrderStatus.CANCELLED, QuiqupStatusMapper.toOrderStatus("delivery_cancelled"));
        assertEquals(OrderStatus.FAILED, QuiqupStatusMapper.toOrderStatus("returned_to_sender"));
    }

    @Test
    void recognisesTheDeliveryOutcome() {
        assertEquals(OrderStatus.DELIVERED, QuiqupStatusMapper.toOrderStatus("delivered"));
        assertEquals(OrderStatus.DELIVERED, QuiqupStatusMapper.toOrderStatus("DELIVERED"));
        assertEquals(OrderStatus.DELIVERED, QuiqupStatusMapper.toOrderStatus("order.delivered"));
    }

    @Test
    void recognisesInTransit() {
        for (String s : new String[]{"in_transit", "IN-TRANSIT", "on_the_way", "out_for_delivery",
                                     "arrived_at_dropoff", "arriving"}) {
            assertEquals(OrderStatus.IN_TRANSIT, QuiqupStatusMapper.toOrderStatus(s), s);
        }
    }

    @Test
    void recognisesCollection() {
        for (String s : new String[]{"collected", "picked_up", "pickup_complete",
                                     "courier_assigned", "job_accepted"}) {
            assertEquals(OrderStatus.IN_COURIER, QuiqupStatusMapper.toOrderStatus(s), s);
        }
    }

    @Test
    void movesNothingOnAStatusWeDoNotRecognise() {
        // A status we have never seen is far more likely to be a new intermediate step than a
        // delivery outcome. Guessing would move an order on evidence we do not have.
        for (String s : new String[]{"pending", "created", "scheduled", "queued",
                                     "driver_briefed", "wibble", "", "   "}) {
            assertNull(QuiqupStatusMapper.toOrderStatus(s), s);
        }
        assertNull(QuiqupStatusMapper.toOrderStatus(null));
    }

    @Test
    void neverReturnsADeprecatedStatus() {
        // The courier flow maps onto COURIER_ASSIGNED / PICKED_UP for historical reasons. A new
        // integration must land on the current statuses instead.
        for (String s : new String[]{"collected", "picked_up", "courier_assigned", "in_transit",
                                     "delivered", "cancelled", "failed"}) {
            OrderStatus mapped = QuiqupStatusMapper.toOrderStatus(s);
            assertNotEquals(OrderStatus.COURIER_ASSIGNED, mapped, s);
            assertNotEquals(OrderStatus.PICKED_UP, mapped, s);
            assertNotEquals(OrderStatus.SHIPPED, mapped, s);
        }
    }

    @Test
    void theTrackingNoteSaysWhereTheStatusCameFrom() {
        assertTrue(QuiqupStatusMapper.trackingNote("collected").contains("Quiqup"));
        assertTrue(QuiqupStatusMapper.trackingNote("collected").contains("collected"));
        assertTrue(QuiqupStatusMapper.trackingNote(null).contains("unknown"));
    }

    // ── Quiqup's documented vocabulary ───────────────────────────────────────
    // Taken from their API docs and support material:
    //   ready_for_collection → collected → at_depot → out_for_delivery → delivery_complete
    // plus picking_started, out_for_collection, in_transit, received_at_depot,
    // collection_failed, delivery_failed, out_for_return, cancelled.

    @Test
    void readyForCollectionDoesNotMeanCollected() {
        // The parcel is packed and waiting; nobody has it. Substring matching read the word
        // "collection" and moved the order to IN_COURIER, telling the customer a courier was
        // holding their order while it sat on a shelf in the shop.
        assertNull(QuiqupStatusMapper.toOrderStatus("ready_for_collection"));
    }

    @Test
    void outForCollectionIsNotOutForDelivery() {
        // The courier is driving TO THE SHOP. Substring matching saw "out_for" and reported
        // IN_TRANSIT, skipping the entire collection leg — the customer is told their order is on
        // the way to them before it has left the store.
        assertEquals(OrderStatus.IN_COURIER, QuiqupStatusMapper.toOrderStatus("out_for_collection"));
    }

    @Test
    void walksTheirDocumentedLifecycleInOrder() {
        assertNull(QuiqupStatusMapper.toOrderStatus("pending"));
        assertNull(QuiqupStatusMapper.toOrderStatus("picking_started"));
        assertNull(QuiqupStatusMapper.toOrderStatus("ready_for_collection"));
        assertEquals(OrderStatus.IN_COURIER,  QuiqupStatusMapper.toOrderStatus("out_for_collection"));
        assertEquals(OrderStatus.IN_COURIER,  QuiqupStatusMapper.toOrderStatus("collected"));
        assertNull(QuiqupStatusMapper.toOrderStatus("at_depot"));
        assertNull(QuiqupStatusMapper.toOrderStatus("received_at_depot"));
        assertEquals(OrderStatus.IN_TRANSIT,  QuiqupStatusMapper.toOrderStatus("in_transit"));
        assertEquals(OrderStatus.IN_TRANSIT,  QuiqupStatusMapper.toOrderStatus("out_for_delivery"));
        assertEquals(OrderStatus.DELIVERED,   QuiqupStatusMapper.toOrderStatus("delivery_complete"));
    }

    @Test
    void mapsTheirFailureStates() {
        assertEquals(OrderStatus.FAILED,    QuiqupStatusMapper.toOrderStatus("delivery_failed"));
        assertEquals(OrderStatus.FAILED,    QuiqupStatusMapper.toOrderStatus("collection_failed"));
        assertEquals(OrderStatus.FAILED,    QuiqupStatusMapper.toOrderStatus("out_for_return"));
        assertEquals(OrderStatus.CANCELLED, QuiqupStatusMapper.toOrderStatus("cancelled"));
    }

    @Test
    void aStateWeHaveNeverSeenStillCannotBeMisread() {
        // Quiqup can add a state without telling us, so the heuristics stay — but they must not
        // reintroduce the bugs the exact table exists to prevent.
        assertEquals(OrderStatus.FAILED, QuiqupStatusMapper.toOrderStatus("delivery_attempt_failed"));
        assertEquals(OrderStatus.IN_TRANSIT, QuiqupStatusMapper.toOrderStatus("out_for_delivery_retry"));
        assertNull(QuiqupStatusMapper.toOrderStatus("ready_for_something_new"));
        assertNull(QuiqupStatusMapper.toOrderStatus("driver_briefed"));
    }

    @Test
    void neverReturnsTheSentinelUsedInternally() {
        // The table uses PENDING_PAYMENT as its "not in the table" marker. It must never escape —
        // moving a delivered order back to PENDING_PAYMENT would be a spectacular way to fail.
        for (String s : new String[]{"pending", "collected", "delivered", "wibble", "at_depot",
                                     "out_for_delivery", "delivery_failed", "cancelled"}) {
            assertNotEquals(OrderStatus.PENDING_PAYMENT, QuiqupStatusMapper.toOrderStatus(s), s);
        }
    }
}
