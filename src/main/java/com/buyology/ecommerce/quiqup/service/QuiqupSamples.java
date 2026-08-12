package com.buyology.ecommerce.quiqup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Best-guess MOCK payloads for a Dubai test delivery, used to pre-fill the admin UI.
 *
 * <p>These are intentionally editable in the UI: the exact Quiqup field names are confirmed
 * against their sandbox/Postman pack during live testing. Treat them as a starting point.
 */
@Component
public class QuiqupSamples {

    private final ObjectMapper objectMapper;

    public QuiqupSamples(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Quiqup /orders schema: coords are [lng, lat]; kind = service type; origin=pickup,
    // destination=dropoff, items=parcels. On-demand preset uses the 4hr service.
    private static final String ON_DEMAND = """
        {
          "kind": "partner_4hr",
          "notes": "Buyology sandbox test — on-demand",
          "payment_mode": "pre_paid",
          "payment_amount": 0,
          "partner_order_id": "TEST-OD-0001",
          "origin": {
            "contact_name": "Buyology Store",
            "contact_phone": "+971500000001",
            "notes": "Collect from front desk",
            "address": {
              "address1": "Emaar Square Building 1",
              "address2": "Downtown Dubai",
              "coords": [55.2744, 25.1972],
              "country": "UAE",
              "town": "Dubai"
            }
          },
          "destination": {
            "contact_name": "Test Customer",
            "contact_phone": "+971500000002",
            "share_tracking": true,
            "notes": "Call on arrival",
            "address": {
              "address1": "Marina Gate 1",
              "address2": "Apartment 1203, Dubai Marina",
              "coords": [55.1403, 25.0805],
              "country": "UAE",
              "town": "Dubai"
            }
          },
          "items": [
            { "name": "Parcel 1", "quantity": 1 }
          ]
        }""";

    // Ecommerce preset — same schema, next-day service.
    private static final String ECOMMERCE = """
        {
          "kind": "partner_next_day",
          "notes": "Buyology sandbox test — ecommerce",
          "payment_mode": "pre_paid",
          "payment_amount": 0,
          "partner_order_id": "TEST-EC-0001",
          "origin": {
            "contact_name": "Buyology Store",
            "contact_phone": "+971500000001",
            "notes": "Collect from front desk",
            "address": {
              "address1": "Emaar Square Building 1",
              "address2": "Downtown Dubai",
              "coords": [55.2744, 25.1972],
              "country": "UAE",
              "town": "Dubai"
            }
          },
          "destination": {
            "contact_name": "Test Customer",
            "contact_phone": "+971500000002",
            "share_tracking": true,
            "notes": "Leave with concierge",
            "address": {
              "address1": "Marina Gate 1",
              "address2": "Apartment 1203, Dubai Marina",
              "coords": [55.1403, 25.0805],
              "country": "UAE",
              "town": "Dubai"
            }
          },
          "items": [
            { "name": "Parcel 1", "quantity": 1 }
          ]
        }""";

    private static final String QUOTE = """
        {
          "pickup": { "lat": 25.1972, "lng": 55.2744, "city": "Dubai" },
          "dropoff": { "lat": 25.0805, "lng": 55.1403, "city": "Dubai" },
          "package_size": "small"
        }""";

    public Map<String, JsonNode> all() {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        out.put("onDemand", withUniqueRef(read(ON_DEMAND), "TEST-OD"));
        out.put("ecommerce", withUniqueRef(read(ECOMMERCE), "TEST-EC"));
        out.put("quote", read(QUOTE)); // a quote is not an order and carries no partner reference
        return out;
    }

    /**
     * Give each prefill a fresh {@code partner_order_id}.
     *
     * <p>It was a constant, so every order created from the page arrived at Quiqup with the same
     * reference — four test orders on their dashboard all reading {@code TEST-OD-0001}, which is the
     * one column that is supposed to tell them apart. {@code partner_order_id} is our side of the
     * correlation: it is what identifies the order in <em>our</em> system when a webhook or an invoice
     * line comes back, so duplicates make a delivery event ambiguous the moment more than one order
     * is in flight.
     *
     * <p>When this module graduates to real deliveries, this field must carry the real order id
     * rather than a generated one.
     */
    private JsonNode withUniqueRef(JsonNode sample, String prefix) {
        if (sample instanceof ObjectNode object && object.has("partner_order_id")) {
            object.put("partner_order_id", prefix + "-" + uniqueSuffix());
        }
        return sample;
    }

    /**
     * A timestamp in the business zone plus a few random characters: readable next to Quiqup's own
     * order dates, and still distinct if the button is clicked twice in the same second.
     */
    private static String uniqueSuffix() {
        String stamp = LocalDateTime.now(ZoneId.of("Asia/Dubai"))
                .format(DateTimeFormatter.ofPattern("yyMMdd-HHmmss"));
        String random = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0x10000));
        return stamp + "-" + random;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Bad Quiqup sample JSON", e);
        }
    }
}
