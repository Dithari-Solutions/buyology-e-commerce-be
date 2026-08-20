package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns one of our orders into the job payload Quiqup accepts.
 *
 * <p>Pure mapping: no database, no HTTP, no clock. That is deliberate — this is the piece where a
 * mistake puts a courier at the wrong address, so it has to be testable by inspection with nothing
 * mocked.
 *
 * <p>The schema is the one {@link QuiqupSamples} documents: {@code origin} is where the parcel is
 * collected, {@code destination} is the customer, {@code items} are parcels.
 */
@Component
public class QuiqupOrderMapper {

    /**
     * Quiqup want coordinates as <strong>[longitude, latitude]</strong>.
     *
     * <p>Named rather than inlined because it is the single highest-consequence detail in this
     * class and reads as unremarkable at a glance. In Dubai the two values are close enough
     * (55.27, 25.19) that a transposed pair is still a plausible-looking coordinate — it is simply
     * in the wrong country, and nothing downstream would reject it.
     *
     * <p>Confirmed by round-trip against Quiqup staging, not merely from their samples: a job sent
     * with {@code "coords": [55.2744, 25.1972]} came back as
     * {@code "coordinates": {"lat": 25.1972, "lng": 55.2744}}. Index 0 is longitude.
     */
    private static ArrayNode coords(ObjectMapper mapper, double longitude, double latitude) {
        ArrayNode node = mapper.createArrayNode();
        node.add(longitude);
        node.add(latitude);
        return node;
    }

    private final ObjectMapper objectMapper;
    private final QuiqupProperties props;

    public QuiqupOrderMapper(ObjectMapper objectMapper, QuiqupProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
    }

    /**
     * Builds the create-order payload.
     *
     * @param order  the paid order being dispatched
     * @param origin the active store location the parcel is collected from
     * @param originContactPhone the store's contact phone, for the courier to call on arrival
     * @param items  the order's items, used only to describe the parcels
     */
    public ObjectNode toCreatePayload(Order order, StoreLocation origin,
                                      String originContactPhone, List<OrderItem> items) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("kind", props.getDispatch().getKind());
        root.put("partner_order_id", partnerOrderId(order));
        root.put("notes", "Buyology order " + partnerOrderId(order));

        // Every order reaching this mapper has already been paid for on our side, so the courier
        // collects no money. Cash on delivery would need payment_mode "cod" and the amount to
        // collect, and is not offered on this channel.
        root.put("payment_mode", "pre_paid");
        root.put("payment_amount", 0);

        root.set("origin", originNode(origin, originContactPhone,
                "Collect 1 parcel for Buyology order " + partnerOrderId(order)
                        + ". Contents: " + contentsSummary(items)));
        root.set("destination", destinationNode(order));
        root.set("items", itemsNode(items));
        return root;
    }

    /**
     * The reference Quiqup echo back to us, and the one a human reads out on the phone.
     *
     * <p>Orders have no human-facing number of their own, so this uses the same short form the
     * order emails and the dashboard already show — an admin chasing a delivery sees the same
     * string in both systems.
     */
    public static String partnerOrderId(Order order) {
        return "BUY-" + order.getId().toString().substring(0, 8).toUpperCase();
    }

    private ObjectNode originNode(StoreLocation origin, String contactPhone, String collectionNote) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("contact_name", blankToDash(origin.getBranchName()));
        node.put("contact_phone", blankToDash(contactPhone));
        node.put("notes", collectionNote);

        ObjectNode address = objectMapper.createObjectNode();
        address.put("address1", blankToDash(origin.getAddress()));
        address.put("address2", blankToDash(origin.getCity()));
        address.put("postcode", blankToDash(origin.getPostalCode()));
        address.set("coords", coords(objectMapper, origin.getLongitude(), origin.getLatitude()));
        address.put("country", blankToDash(origin.getCountry()));
        address.put("town", blankToDash(origin.getCity()));
        node.set("address", address);
        return node;
    }

    private ObjectNode destinationNode(Order order) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("contact_name", recipientName(order));
        node.put("contact_phone", blankToDash(order.getRecipientPhone()));
        // Quiqup send the customer their own tracking link, which is the only live tracking this
        // channel has — our WebSocket tracking belongs to our own courier fleet, not to Quiqup.
        node.put("share_tracking", true);
        node.put("notes", "Call on arrival.");

        ObjectNode address = objectMapper.createObjectNode();
        address.put("address1", blankToDash(order.getAddressLine1()));
        address.put("address2", blankToDash(order.getAddressLine2()));
        address.put("postcode", blankToDash(order.getPostalCode()));
        address.set("coords", coords(objectMapper,
                order.getDeliveryLongitude(), order.getDeliveryLatitude()));
        // Order.country is alpha-3 ("UAE"). Confirmed accepted: Quiqup normalise it to "AE" on
        // their side, so no mapping table is needed here.
        address.put("country", blankToDash(order.getCountry()));
        address.put("town", blankToDash(order.getCity()));
        node.set("address", address);
        return node;
    }

    /**
     * ONE parcel for the whole order.
     *
     * <p>Quiqup's {@code items} array is the list of physical packages a courier must collect, not
     * a list of what is inside them — their dashboard shows each entry as a numbered parcel with
     * its own dimensions, alongside a separate and unrelated "Products" section. Emitting one entry
     * per order line therefore tells them to expect that many packages: the courier arrives for
     * three, the shop hands over one box, and the disagreement happens at the counter with the
     * customer's parcel in the middle of it.
     *
     * <p>A single order is packed as a single parcel here, so that is what is declared. An order
     * genuinely shipped as several boxes would need the packing step to tell us how many, which is
     * information this service does not have and must not invent.
     *
     * <p>What is in the box goes in {@link #contentsSummary} on the notes instead, where it helps
     * whoever hands the parcel over without being mistaken for a package count.
     */
    private ArrayNode itemsNode(List<OrderItem> items) {
        ArrayNode array = objectMapper.createArrayNode();
        ObjectNode parcel = objectMapper.createObjectNode();
        parcel.put("name", "Parcel");
        parcel.put("quantity", 1);
        array.add(parcel);
        return array;
    }

    /**
     * A short description of the contents, for the collection note.
     *
     * <p>Carries SKUs rather than product titles: titles are translated per language and would
     * describe the same parcel differently depending on who placed the order, which is exactly
     * wrong for something a warehouse reads while finding it.
     */
    static String contentsSummary(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return "1 parcel";
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (OrderItem item : items) {
            if (shown == 3) {
                sb.append(", +").append(items.size() - shown).append(" more");
                break;
            }
            String sku = item.getVariantSku() != null && !item.getVariantSku().isBlank()
                    ? item.getVariantSku()
                    : item.getProductSku();
            if (sku == null || sku.isBlank()) continue;
            if (shown > 0) sb.append(", ");
            sb.append(sku);
            int qty = item.getQuantity() == null ? 1 : item.getQuantity();
            if (qty > 1) sb.append(" x").append(qty);
            shown++;
        }
        return sb.length() == 0 ? "1 parcel" : sb.toString();
    }

    private static String recipientName(Order order) {
        String first = order.getRecipientFirstName() == null ? "" : order.getRecipientFirstName().trim();
        String last = order.getRecipientLastName() == null ? "" : order.getRecipientLastName().trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "-" : full;
    }

    /**
     * Quiqup reject empty strings on required address fields, and a rejected job is a delivery that
     * silently never happens. A dash keeps the field present and makes the gap visible to whoever
     * reads the job — {@link QuiqupDispatchService} refuses to dispatch when anything that actually
     * matters is missing, so a dash here only ever stands in for an optional line.
     */
    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }
}
