package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins response parsing against a REAL Quiqup response, captured from their staging estate.
 *
 * <p>Everything this class checks was a guess until that response arrived, and both guesses fail
 * silently rather than loudly: read the wrong field for the job id and the order is flagged for a
 * human while a job sits at Quiqup unclaimed; read a plausible-but-wrong one and every later call —
 * ready-for-collection, cancel, webhook resolution — is aimed at something that is not this
 * delivery.
 *
 * <p>The payload is deliberately kept verbatim rather than reduced to the fields under test. Its
 * shape is the specification, and the fields trimmed away would be exactly the ones that make a
 * naive search pick the wrong value.
 */
class QuiqupResponseParsingTest {

    /**
     * A real create-order response. Note what is in here besides the order id: a uuid, a user id, a
     * parcel barcode "26012997-1" that differs from the order id by one character, and an item id.
     * Four plausible ids for a search to land on, and only one of them is the order.
     */
    private static final String CREATE_RESPONSE = """
            {
              "order": {
                "id": 26012997,
                "uuid": "61f1399f-55db-46fe-8e91-422ff4675568",
                "state": "pending",
                "kind": "partner_same_day",
                "service_kind": "partner_4hr",
                "partner_order_id": "TEST-OD-260820-203343-4872",
                "payment_mode": "pre_paid",
                "payment_amount": "0.0",
                "tracking_url": "https://track-parcel.staging.quiqup.com/61f1399f-55db-46fe-8e91-422ff4675568",
                "user": { "email": "aaqish@buyology.online", "fullname": "Aaqish Gaffar", "id": 497473 },
                "items": [
                  { "id": "30021787", "name": "Parcel 1", "parcel_barcode": "26012997-1",
                    "quantity": 1, "weight": "5.0",
                    "dimensions": { "height": 10, "length": 10, "width": 10 } }
                ],
                "destination": {
                  "id": "123",
                  "contact_name": "Test Customer",
                  "address": { "address1": "Marina Gate 1", "country": "AE",
                               "coordinates": { "lat": 25.0805, "lng": 55.1403 } }
                },
                "origin": {
                  "id": "123",
                  "contact_name": "Buyology Store",
                  "address": { "address1": "Emaar Square Building 1", "country": "AE",
                               "coordinates": { "lat": 25.1972, "lng": 55.2744 } }
                }
              }
            }""";

    private static QuiqupApiResult result(String json) throws Exception {
        return new QuiqupApiResult(200, true, new ObjectMapper().readTree(json));
    }

    // ── The job id ───────────────────────────────────────────────────────────

    @Test
    void findsTheOrderIdInsideTheOrderWrapper() throws Exception {
        // The id is nested under "order" and is a NUMBER, not a string.
        assertEquals("26012997", QuiqupDispatchService.extractOrderId(result(CREATE_RESPONSE)));
    }

    @Test
    void doesNotPickAnyOfTheOtherIdsInTheSameResponse() throws Exception {
        String id = QuiqupDispatchService.extractOrderId(result(CREATE_RESPONSE));

        assertNotEquals("26012997-1", id, "that is the PARCEL barcode, not the order");
        assertNotEquals("497473", id, "that is the USER id");
        assertNotEquals("30021787", id, "that is an ITEM id");
        assertNotEquals("123", id, "that is an address id");
        assertNotEquals("61f1399f-55db-46fe-8e91-422ff4675568", id,
                "the uuid identifies the same order, but the numeric id is what the REST paths take");
    }

    @Test
    void returnsNullRatherThanGuessingWhenNoIdIsPresent() throws Exception {
        // Deliberate: a null is handled as "needs a human", because Quiqup may have created the job
        // and a blind retry would book a second courier for the same parcel.
        assertNull(QuiqupDispatchService.extractOrderId(result("{\"message\":\"accepted\"}")));
        assertNull(QuiqupDispatchService.extractOrderId(result("[]")));
        assertNull(QuiqupDispatchService.extractOrderId(null));
        assertNull(QuiqupDispatchService.extractOrderId(new QuiqupApiResult(200, true, "not json")));
    }

    // ── The customer's tracking link ─────────────────────────────────────────

    @Test
    void findsTheTrackingUrl() throws Exception {
        // Quiqup share this with the customer themselves, and it is the only live tracking this
        // channel has — our own WebSocket tracking belongs to our courier fleet, not to them.
        assertEquals("https://track-parcel.staging.quiqup.com/61f1399f-55db-46fe-8e91-422ff4675568",
                QuiqupDispatchService.extractTrackingUrl(result(CREATE_RESPONSE)));
    }

    @Test
    void toleratesAResponseWithNoTrackingUrl() throws Exception {
        assertNull(QuiqupDispatchService.extractTrackingUrl(result("{\"order\":{\"id\":1}}")));
        assertNull(QuiqupDispatchService.extractTrackingUrl(null));
    }

    // ── Their status vocabulary, as actually observed ────────────────────────

    @Test
    void theStateFieldIsWhatCarriesTheDeliveryStatus() {
        // Confirmed from the live response: the field is "state", and its values are lowercase.
        // "pending" means the job exists, which is not itself a change to the customer's order.
        assertNull(QuiqupStatusMapper.toOrderStatus("pending"));
        assertEquals(com.buyology.ecommerce.order.domain.enums.OrderStatus.CANCELLED,
                QuiqupStatusMapper.toOrderStatus("cancelled"));
    }
}
