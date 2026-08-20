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
}
