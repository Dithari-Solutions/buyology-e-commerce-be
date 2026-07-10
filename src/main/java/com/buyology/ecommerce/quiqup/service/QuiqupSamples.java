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

    private static final String ON_DEMAND = """
        {
          "partner_order_id": "TEST-OD-0001",
          "description": "Sandbox test parcel",
          "payment_type": "cash_on_delivery",
          "cash_on_delivery_amount": 0,
          "pickup": {
            "contact_name": "Buyology Store",
            "contact_phone": "+971500000001",
            "address": "Downtown Dubai, Emaar Square, Building 1",
            "city": "Dubai",
            "country": "UAE",
            "lat": 25.1972,
            "lng": 55.2744,
            "notes": "Collect from front desk"
          },
          "dropoff": {
            "contact_name": "Test Customer",
            "contact_phone": "+971500000002",
            "address": "Dubai Marina, Marina Gate 1, Apt 1203",
            "city": "Dubai",
            "country": "UAE",
            "lat": 25.0805,
            "lng": 55.1403,
            "notes": "Call on arrival"
          },
          "packages": [
            { "description": "1 x Test item", "quantity": 1, "weight": 1.5, "size": "small" }
          ]
        }""";

    private static final String ECOMMERCE = """
        {
          "partner_order_id": "TEST-EC-0001",
          "reference": "TEST-EC-0001",
          "service_type": "next_day",
          "payment_type": "prepaid",
          "cash_on_delivery_amount": 0,
          "recipient": {
            "name": "Test Customer",
            "phone": "+971500000002",
            "email": "test.customer@example.com",
            "address": "Dubai Marina, Marina Gate 1, Apt 1203",
            "city": "Dubai",
            "country": "UAE",
            "lat": 25.0805,
            "lng": 55.1403
          },
          "items": [
            { "sku": "TEST-SKU-1", "description": "Test item", "quantity": 1, "price": 100 }
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
