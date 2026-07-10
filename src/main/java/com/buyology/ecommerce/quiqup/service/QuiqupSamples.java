package com.buyology.ecommerce.quiqup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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
        out.put("onDemand", read(ON_DEMAND));
        out.put("ecommerce", read(ECOMMERCE));
        out.put("quote", read(QUOTE));
        return out;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Bad Quiqup sample JSON", e);
        }
    }
}
