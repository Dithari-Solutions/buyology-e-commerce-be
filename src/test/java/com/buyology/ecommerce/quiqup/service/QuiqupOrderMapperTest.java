package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the payload a courier is dispatched on.
 *
 * <p>Everything here is cheap to assert and expensive to get wrong. A mistake in this class does
 * not throw, does not fail a build and does not show up in a log: it sends a real van to a real
 * street that is not the customer's, and the first person to find out is the customer.
 *
 * <p>The coordinate order is the sharpest example and gets the most attention below. Quiqup take
 * <strong>[longitude, latitude]</strong>, which is the opposite of how every UI in this codebase
 * writes a coordinate, and in Dubai the two numbers (55.27, 25.19) are close enough that a
 * transposed pair still looks like a plausible coordinate — it is simply in the wrong country, and
 * nothing between here and the courier would question it.
 */
class QuiqupOrderMapperTest {

    private static final double STORE_LAT = 25.1972;
    private static final double STORE_LNG = 55.2744;
    private static final double CUSTOMER_LAT = 25.0805;
    private static final double CUSTOMER_LNG = 55.1403;

    private final QuiqupOrderMapper mapper =
            new QuiqupOrderMapper(new ObjectMapper(), new QuiqupProperties());

    private static Order order() {
        Order order = new Order();
        order.setId(UUID.fromString("3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607"));
        order.setRecipientFirstName("Layla");
        order.setRecipientLastName("Haddad");
        order.setRecipientPhone("+971500000002");
        order.setAddressLine1("Marina Gate 1");
        order.setAddressLine2("Apartment 1203");
        order.setCity("Dubai");
        order.setCountry("UAE");
        order.setDeliveryLatitude(CUSTOMER_LAT);
        order.setDeliveryLongitude(CUSTOMER_LNG);
        return order;
    }

    private static StoreLocation origin() {
        StoreLocation location = new StoreLocation();
        location.setBranchName("Buyology Downtown");
        location.setAddress("Emaar Square Building 1");
        location.setCity("Dubai");
        location.setCountry("UAE");
        location.setLatitude(STORE_LAT);
        location.setLongitude(STORE_LNG);
        return location;
    }

    private static OrderItem item(String sku, int qty) {
        OrderItem i = new OrderItem();
        i.setProductSku(sku);
        i.setQuantity(qty);
        return i;
    }

    // ── The one that sends a van to the wrong country ────────────────────────

    @Test
    void coordinatesAreLongitudeThenLatitude() {
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001",
                List.of(item("SKU-1", 1)));

        var destination = payload.get("destination").get("address").get("coords");
        assertEquals(CUSTOMER_LNG, destination.get(0).asDouble(), 1e-9, "index 0 must be LONGITUDE");
        assertEquals(CUSTOMER_LAT, destination.get(1).asDouble(), 1e-9, "index 1 must be LATITUDE");

        var pickup = payload.get("origin").get("address").get("coords");
        assertEquals(STORE_LNG, pickup.get(0).asDouble(), 1e-9, "index 0 must be LONGITUDE");
        assertEquals(STORE_LAT, pickup.get(1).asDouble(), 1e-9, "index 1 must be LATITUDE");
    }

    @Test
    void longitudeIsAlwaysTheLargerValueForTheUae() {
        // A second, independent check on the same bug: everywhere Quiqup operate, longitude (~55)
        // exceeds latitude (~25). If these ever transpose, this fails even if someone "fixes" the
        // test above by swapping the expectations too.
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001", List.of());

        for (String end : new String[]{"origin", "destination"}) {
            var coords = payload.get(end).get("address").get("coords");
            assertTrue(coords.get(0).asDouble() > coords.get(1).asDouble(),
                    end + " coords look transposed: " + coords);
        }
    }

    @Test
    void pickupIsTheStoreAndDropoffIsTheCustomer() {
        // Reversing these is the other way to send a courier to the wrong place, and it produces a
        // payload that is entirely well-formed.
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001", List.of());

        assertEquals("Buyology Downtown", payload.get("origin").get("contact_name").asText());
        assertEquals("+971500000001", payload.get("origin").get("contact_phone").asText());
        assertEquals("Emaar Square Building 1", payload.get("origin").get("address").get("address1").asText());

        assertEquals("Layla Haddad", payload.get("destination").get("contact_name").asText());
        assertEquals("+971500000002", payload.get("destination").get("contact_phone").asText());
        assertEquals("Marina Gate 1", payload.get("destination").get("address").get("address1").asText());
    }

    // ── Money ────────────────────────────────────────────────────────────────

    @Test
    void theCourierNeverCollectsMoney() {
        // Every order reaching dispatch is already paid. A payload that said otherwise would have a
        // courier asking a customer to pay a second time.
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001", List.of());

        assertEquals("pre_paid", payload.get("payment_mode").asText());
        assertEquals(0, payload.get("payment_amount").asInt());
    }

    // ── Reference and parcels ────────────────────────────────────────────────

    @Test
    void carriesAReferenceThatMatchesWhatSupportSees() {
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001", List.of());

        assertEquals("BUY-3F2A1B4C", payload.get("partner_order_id").asText());
        assertEquals("BUY-3F2A1B4C", QuiqupOrderMapper.partnerOrderId(order()));
    }

    @Test
    void declaresExactlyOneParcelHoweverManyLinesTheOrderHas() {
        // items[] is the list of PHYSICAL PACKAGES a courier must collect — confirmed against a real
        // staging job, whose dashboard showed one entry becoming one numbered parcel with its own
        // dimensions, next to a separate and empty "Products" section. One entry per order line
        // would send a courier to collect three packages from a shop holding one box.
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001",
                List.of(item("SKU-A", 2), item("SKU-B", 1), item("SKU-C", 4)));

        var items = payload.get("items");
        assertEquals(1, items.size(), "a three-line order is still one box");
        assertEquals(1, items.get(0).get("quantity").asInt());
    }

    @Test
    void stillDeclaresAParcelWhenItemsAreMissing() {
        // Quiqup reject a job with no items, and an order with no readable items is still a real
        // parcel sitting on a shelf.
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001", List.of());

        assertEquals(1, payload.get("items").size());
        assertEquals(1, payload.get("items").get(0).get("quantity").asInt());
    }

    @Test
    void whatIsInTheBoxGoesOnTheCollectionNote() {
        // The contents still have to reach whoever hands the parcel over — just not as a package
        // count. SKUs rather than titles: a title is translated per language and would describe the
        // same parcel differently depending on who placed the order.
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001",
                List.of(item("SKU-A", 2), item("SKU-B", 1)));

        String note = payload.get("origin").get("notes").asText();
        assertTrue(note.contains("SKU-A"), note);
        assertTrue(note.contains("x2"), "quantities matter to whoever is packing: " + note);
        assertTrue(note.contains("SKU-B"), note);
        assertTrue(note.contains("BUY-3F2A1B4C"), "the note must name the order: " + note);
    }

    @Test
    void theCollectionNoteStaysShortOnALargeOrder() {
        var many = List.of(item("A", 1), item("B", 1), item("C", 1), item("D", 1), item("E", 1));
        assertTrue(QuiqupOrderMapper.contentsSummary(many).contains("+2 more"),
                QuiqupOrderMapper.contentsSummary(many));
    }

    @Test
    void sendsAPostcodeFieldEvenWhenWeHaveNone() {
        // The staging dashboard showed "Post Code: N/A" on both ends because we never sent the
        // field at all. It is present now, and absent-but-present beats missing.
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001", List.of());

        assertTrue(payload.get("origin").get("address").has("postcode"));
        assertTrue(payload.get("destination").get("address").has("postcode"));
    }

    @Test
    void neverEmitsAnEmptyRequiredAddressField() {
        Order sparse = order();
        sparse.setAddressLine2(null);
        sparse.setRecipientFirstName(null);
        sparse.setRecipientLastName(null);

        ObjectNode payload = mapper.toCreatePayload(sparse, origin(), null, List.of());

        // Quiqup reject empty strings on these, and a rejected job is a delivery that never happens.
        assertFalse(payload.get("destination").get("address").get("address2").asText().isEmpty());
        assertFalse(payload.get("destination").get("contact_name").asText().isEmpty());
        assertFalse(payload.get("origin").get("contact_phone").asText().isEmpty());
    }

    @Test
    void asksQuiqupToShareTrackingWithTheCustomer() {
        // Quiqup's own tracking link is the only live tracking this channel has — our WebSocket
        // tracking belongs to our own courier fleet.
        ObjectNode payload = mapper.toCreatePayload(order(), origin(), "+971500000001", List.of());
        assertTrue(payload.get("destination").get("share_tracking").asBoolean());
    }

    @Test
    void usesTheConfiguredServiceLevel() {
        QuiqupProperties props = new QuiqupProperties();
        props.getDispatch().setKind("partner_4hr");
        var custom = new QuiqupOrderMapper(new ObjectMapper(), props);

        assertEquals("partner_4hr",
                custom.toCreatePayload(order(), origin(), "+971500000001", List.of()).get("kind").asText());
    }
}
