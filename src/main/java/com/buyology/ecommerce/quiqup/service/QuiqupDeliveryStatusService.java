package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.order.service.OrderService;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Turns an inbound Quiqup webhook into a change to the customer's order.
 *
 * <p>This is the return half of the integration. Before it, deliveries were recorded in
 * {@code quiqup_test_events} and read by nobody: an order collected, driven and handed over stayed
 * PAID in our database, and the customer was told nothing.
 *
 * <p>Kept out of the controller because the controller's job is transport and authentication —
 * reading bytes, verifying a signature, answering a status code — and this is a decision about a
 * customer's order. Keeping them separate is also what lets the mapping be tested without a
 * request.
 */
@Service
public class QuiqupDeliveryStatusService {

    private static final Logger log = LoggerFactory.getLogger(QuiqupDeliveryStatusService.class);

    private final QuiqupProperties props;
    private final OrderRepository orderRepo;
    private final OrderService orderService;

    public QuiqupDeliveryStatusService(QuiqupProperties props,
                                       OrderRepository orderRepo,
                                       OrderService orderService) {
        this.props = props;
        this.orderRepo = orderRepo;
        this.orderService = orderService;
    }

    /**
     * Applies one webhook event.
     *
     * @param root      the parsed body
     * @param eventType the event name the controller already extracted, used when the body carries
     *                  no separate status field
     */
    public void apply(JsonNode root, String eventType) {
        if (!props.getDispatch().isEnabled()) {
            // Dispatch off means we did not create these jobs, so their events are not ours to act
            // on — during a staging trial that is exactly the desired behaviour.
            return;
        }
        Order order = resolveOrder(root);
        if (order == null) {
            log.warn("[QUIQUP] Webhook event={} matched no order", eventType);
            return;
        }

        String quiqupStatus = statusOf(root, eventType);
        OrderStatus target = QuiqupStatusMapper.toOrderStatus(quiqupStatus);

        // Record what they said even when it maps to nothing. An unmapped status is the evidence
        // needed to add it later, and losing it means discovering the gap twice.
        stampQuiqupStatus(order.getId(), quiqupStatus);

        if (target == null) {
            log.info("[QUIQUP] Order {} — status '{}' recorded, no order change implied",
                    order.getId(), quiqupStatus);
            return;
        }
        boolean moved = orderService.syncStatusFromDeliveryPartner(
                order.getId(), target, QuiqupStatusMapper.trackingNote(quiqupStatus));
        if (!moved) {
            log.info("[QUIQUP] Order {} not moved by status '{}' (duplicate, stale or terminal)",
                    order.getId(), quiqupStatus);

            // A movement event on an order we already cancelled means the parcel was NOT stopped —
            // the exact case that used to vanish without trace while the courier delivered it.
            // Re-read the status rather than trusting the instance loaded before the sync, so an
            // order cancelled-and-superseded in between does not escalate falsely.
            if (target == OrderStatus.IN_COURIER || target == OrderStatus.IN_TRANSIT
                    || target == OrderStatus.DELIVERED) {
                orderRepo.findById(order.getId())
                        .filter(fresh -> fresh.getStatus() == OrderStatus.CANCELLED)
                        .ifPresent(fresh -> orderService.recordPostCancellationMovement(
                                fresh.getId(), quiqupStatus));
            }
        }
    }

    /**
     * Finds the order a delivery belongs to.
     *
     * <p>Quiqup's own id is authoritative — it is what we stored when the job was created. The
     * partner reference is a fallback for events that carry only the id we gave them, and it is
     * matched rather than parsed because it is a string we minted and they echo back.
     */
    private Order resolveOrder(JsonNode root) {
        for (String field : new String[]{"order_id", "id", "quiqup_order_id"}) {
            JsonNode node = root.get(field);
            if (node != null && !node.isNull() && !node.asText().isBlank()) {
                Order match = orderRepo.findByQuiqupOrderId(node.asText()).orElse(null);
                if (match != null) return match;
            }
        }
        JsonNode nested = root.get("order");
        if (nested != null && nested.isObject()) {
            JsonNode id = nested.get("id");
            if (id != null && !id.isNull()) {
                Order match = orderRepo.findByQuiqupOrderId(id.asText()).orElse(null);
                if (match != null) return match;
            }
        }
        // Fall back to the reference we generated, which is derived from the order's own id.
        for (String field : new String[]{"partner_order_id", "reference"}) {
            JsonNode node = root.get(field);
            if (node != null && !node.isNull()) {
                Order match = resolveByPartnerReference(node.asText());
                if (match != null) return match;
            }
        }
        return null;
    }

    /**
     * Resolves "BUY-XXXXXXXX" back to an order.
     *
     * <p>The reference carries only the first eight characters of the order's UUID, so this is a
     * prefix match and could in principle hit more than one order. It is used only when Quiqup's
     * own id did not resolve, and an ambiguous match is treated as no match — moving the wrong
     * customer's order is far worse than failing to move the right one.
     */
    private Order resolveByPartnerReference(String reference) {
        if (reference == null || !reference.startsWith("BUY-") || reference.length() < 12) {
            return null;
        }
        String prefix = reference.substring(4).toLowerCase();
        var matches = orderRepo.findByIdPrefix(prefix);
        if (matches.size() != 1) {
            if (matches.size() > 1) {
                log.error("[QUIQUP] Reference {} matched {} orders; refusing to guess",
                        reference, matches.size());
            }
            return null;
        }
        return matches.get(0);
    }

    /** The delivery status a body carries, falling back to the event name. */
    private static String statusOf(JsonNode root, String eventType) {
        for (String field : new String[]{"status", "state", "delivery_status", "event", "event_type"}) {
            JsonNode node = root.get(field);
            if (node != null && !node.isNull() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return eventType;
    }

    /** Records Quiqup's own wording on the order, independent of whether it moved anything. */
    private void stampQuiqupStatus(UUID orderId, String quiqupStatus) {
        if (quiqupStatus == null || quiqupStatus.isBlank()) return;
        try {
            orderRepo.findById(orderId).ifPresent(o -> {
                o.setQuiqupStatus(quiqupStatus.length() > 60 ? quiqupStatus.substring(0, 60) : quiqupStatus);
                orderRepo.save(o);
            });
        } catch (Exception e) {
            log.warn("[QUIQUP] Could not stamp status on order {}: {}", orderId, e.getMessage());
        }
    }
}
