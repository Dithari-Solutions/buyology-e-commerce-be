package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.event.OrderPaidEvent;
import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.order.service.QuiqupCoverage;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates a Quiqup delivery job for each paid order they will carry.
 *
 * <p>This is the half of the integration that did not exist: until now nothing turned an order
 * into a delivery, so a paid order simply never reached the carrier. Shaped deliberately like
 * {@code ErpOrderSyncService}, which solves the same problem for ERPNext — same listener, same
 * snapshot-then-call-then-stamp sequence, same "record the failure on the order and move on".
 *
 * <h2>What it will not do</h2>
 *
 * <p>Dispatch is refused, loudly and on the order, rather than approximated:
 *
 * <ul>
 *   <li><strong>An order spanning several stores.</strong> A Quiqup job has ONE pickup address. A
 *       courier sent to one shop for a parcel that is half in another collects half an order, and
 *       nothing downstream would notice. These need either a job per store or a consolidation step
 *       and get neither today, so they are left for a human.</li>
 *   <li><strong>A missing delivery coordinate.</strong> Quiqup route on coordinates. Without them
 *       there is nothing to send a courier to.</li>
 * </ul>
 *
 * <p>Neither case changes the order's status. The customer has paid and the order is valid; it
 * simply has no courier yet, and that is a fulfilment problem rather than an order problem.
 */
@Service
public class QuiqupDispatchService {

    private static final Logger log = LoggerFactory.getLogger(QuiqupDispatchService.class);

    private final QuiqupProperties props;
    private final QuiqupClient client;
    private final QuiqupOrderMapper mapper;
    private final QuiqupCoverage coverage;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final StoreRepository storeRepo;
    private final StoreLocationRepository storeLocationRepo;
    private final TransactionTemplate txTemplate;

    public QuiqupDispatchService(QuiqupProperties props,
                                 QuiqupClient client,
                                 QuiqupOrderMapper mapper,
                                 QuiqupCoverage coverage,
                                 OrderRepository orderRepo,
                                 OrderItemRepository orderItemRepo,
                                 StoreRepository storeRepo,
                                 StoreLocationRepository storeLocationRepo,
                                 PlatformTransactionManager transactionManager) {
        this.props = props;
        this.client = client;
        this.mapper = mapper;
        this.coverage = coverage;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.storeRepo = storeRepo;
        this.storeLocationRepo = storeLocationRepo;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** True when the module and automatic dispatch are both switched on. */
    public boolean enabled() {
        return props.isEnabled() && props.getDispatch().isEnabled();
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    /**
     * Dispatches once the order is committed as PAID.
     *
     * <p>AFTER_COMMIT because the order must be visible to this thread and to any retry; {@code
     * @Async} because a courier API call must not sit inside the customer's payment request.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        if (!enabled()) return;
        try {
            dispatch(event.getOrderId());
        } catch (Exception e) {
            // dispatch() already recorded the reason on the order; this is the last-resort guard so
            // an escaping exception cannot kill the async executor's thread silently.
            log.error("[QUIQUP] dispatch threw for order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Creates the Quiqup job for one order.
     *
     * <p>Safe to call repeatedly: an order that already carries a Quiqup id is left alone, which is
     * what makes both the retry job and an admin's manual retry harmless.
     *
     * @return a short human-readable outcome, for the admin page and the logs
     */
    public String dispatch(UUID orderId) {
        try {
            Snapshot snap = txTemplate.execute(status -> loadSnapshot(orderId));
            if (snap == null) {
                return "Order not found";
            }
            if (snap.quiqupOrderId != null && !snap.quiqupOrderId.isBlank()) {
                return "Already dispatched (Quiqup order " + snap.quiqupOrderId + ")";
            }
            String refusal = refuseReason(snap);
            if (refusal != null) {
                recordFailure(orderId, refusal);
                log.warn("[QUIQUP] Not dispatching order {} — {}", orderId, refusal);
                return refusal;
            }

            // Claim before calling, never after. Production runs two app replicas and neither the
            // event listener nor the retry job is cluster-guarded, so the check above is not by
            // itself protection: both instances can pass it, and the gap between passing it and
            // recording a Quiqup id is exactly as wide as the HTTP call. Losing this race must mean
            // doing nothing, because winning it twice means two couriers for one parcel.
            if (!claim(orderId)) {
                log.info("[QUIQUP] Order {} already claimed by another instance; standing down", orderId);
                return "Already being dispatched by another instance";
            }

            ObjectNode payload = mapper.toCreatePayload(
                    snap.order, snap.origin, snap.originPhone, snap.items);

            QuiqupApiResult result = client.request("POST", props.getPaths().getCreate(), payload);
            if (result == null || !result.ok()) {
                String reason = "Quiqup rejected the job: "
                        + (result == null ? "no response" : result.status() + " " + result.body());
                releaseClaim(orderId);
                recordFailure(orderId, reason);
                log.error("[QUIQUP] Dispatch failed for order {} — {}", orderId, reason);
                return reason;
            }

            String quiqupId = extractOrderId(result);
            if (quiqupId == null) {
                // Accepted but unidentifiable. Recording a failure would let the retry create a
                // SECOND job for the same parcel, which is worse than a stuck order — so this is
                // recorded as needing a human, not as retryable.
                // Deliberately NOT released: Quiqup may well have created the job, and freeing the
                // claim would let the retry book a second courier for the same parcel. The claim
                // staying put is what keeps this in a human's hands.
                String reason = "Quiqup accepted the job but returned no id; check for a duplicate "
                        + "before retrying. Response: " + result.body();
                recordFailure(orderId, reason);
                log.error("[QUIQUP] Dispatch ambiguous for order {} — {}", orderId, reason);
                return reason;
            }

            recordSuccess(orderId, quiqupId);
            log.info("[QUIQUP] Order {} dispatched as Quiqup order {}", orderId, quiqupId);

            if (props.getDispatch().isAutoReadyForCollection()) {
                markReadyForCollection(orderId, quiqupId);
            }
            return "Dispatched (Quiqup order " + quiqupId + ")";

        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            releaseClaim(orderId);
            recordFailure(orderId, reason);
            log.error("[QUIQUP] Dispatch failed for order {}", orderId, e);
            return reason;
        }
    }

    /**
     * Tells Quiqup the parcel is packed and ready, which is what actually summons a courier.
     *
     * <p>Separate from {@link #dispatch} because it is the irreversible half: creating a job is a
     * booking, releasing it sends a van. A failure here leaves the job created — the parcel is
     * still going, just not yet — so it is recorded and not treated as a dispatch failure, which
     * would otherwise make the retry job create a duplicate job.
     */
    public String markReadyForCollection(UUID orderId, String quiqupOrderId) {
        try {
            String path = QuiqupClient.fillPath(props.getPaths().getReadyForCollection(), quiqupOrderId);
            QuiqupApiResult result = client.request("PUT", path, null);
            if (result == null || !result.ok()) {
                String reason = "Ready-for-collection failed: "
                        + (result == null ? "no response" : result.status() + " " + result.body());
                log.error("[QUIQUP] {} for order {} (Quiqup {})", reason, orderId, quiqupOrderId);
                return reason;
            }
            log.info("[QUIQUP] Quiqup order {} marked ready for collection", quiqupOrderId);
            return "Ready for collection";
        } catch (Exception e) {
            log.error("[QUIQUP] Ready-for-collection threw for order {}", orderId, e);
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // =========================================================================
    // Eligibility
    // =========================================================================

    /** Why this order must not be dispatched, or null when it may be. */
    private String refuseReason(Snapshot snap) {
        Order order = snap.order;

        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PACKAGING) {
            return "Order is " + order.getStatus() + ", not awaiting dispatch";
        }
        // The same bean that decides whether Quiqup's rate is charged decides whether Quiqup carry
        // it. Billing one carrier and using another is the divergence this is here to prevent.
        if (!coverage.covers(order.getDeliveryMethod(), order.getCountry())) {
            return "Not a Quiqup delivery (" + order.getDeliveryMethod() + " to " + order.getCountry() + ")";
        }
        if (order.getDeliveryLatitude() == null || order.getDeliveryLongitude() == null) {
            return "Order has no delivery coordinates; Quiqup route on coordinates";
        }
        if (order.getRecipientPhone() == null || order.getRecipientPhone().isBlank()) {
            return "Order has no recipient phone; the courier cannot make contact";
        }
        if (snap.storeCount > 1) {
            return "Order spans " + snap.storeCount + " stores and a Quiqup job has one pickup "
                    + "address; needs manual handling";
        }
        if (snap.origin == null) {
            return "No active store location to collect from";
        }
        if (snap.origin.getLatitude() == null || snap.origin.getLongitude() == null) {
            return "Store location " + snap.origin.getId() + " has no coordinates";
        }
        return null;
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    /**
     * Everything the call needs, read in one short transaction.
     *
     * <p>Loaded up front so no database connection is held across the HTTP call — the same reason
     * the ERP sync does it. A courier API that takes twenty seconds must not hold a pooled
     * connection for twenty seconds.
     */
    private Snapshot loadSnapshot(UUID orderId) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            return null;
        }
        List<OrderItem> items = orderItemRepo.findAllByOrderId(orderId);
        Set<UUID> storeIds = items.stream()
                .map(OrderItem::getStoreId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        StoreLocation origin = null;
        String originPhone = null;
        if (storeIds.size() == 1) {
            UUID storeId = storeIds.iterator().next();
            origin = storeLocationRepo.findAllByStoreIdAndIsActive(storeId, true).stream()
                    // The primary branch is the one that holds stock for online orders; any other
                    // active branch is a better answer than none.
                    .sorted((a, b) -> Boolean.compare(
                            Boolean.TRUE.equals(b.getIsPrimary()), Boolean.TRUE.equals(a.getIsPrimary())))
                    .findFirst()
                    .orElse(null);
            originPhone = storeRepo.findById(storeId).map(Store::getContactPhone).orElse(null);
        }
        return new Snapshot(order, items, storeIds.size(), origin, originPhone, order.getQuiqupOrderId());
    }

    /**
     * How long a claim may sit before another instance may take it.
     *
     * <p>Must comfortably exceed the Quiqup request timeout: if a claim expired while the original
     * call was still in flight, the reclaim would dispatch the same parcel a second time — the
     * exact outcome the claim exists to prevent, caused by the claim itself.
     */
    private Duration staleClaimWindow() {
        return Duration.ofMillis(props.getTimeoutMs()).plus(Duration.ofMinutes(5));
    }

    /** True when this instance won the right to dispatch this order. */
    private boolean claim(UUID orderId) {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(staleClaimWindow());
        Integer claimed = txTemplate.execute(status ->
                orderRepo.claimForQuiqupDispatch(orderId, now, staleBefore));
        return claimed != null && claimed > 0;
    }

    /** Hands the claim back after a failure, so the retry does not wait out the stale window. */
    private void releaseClaim(UUID orderId) {
        try {
            txTemplate.executeWithoutResult(status -> orderRepo.releaseQuiqupDispatchClaim(orderId));
        } catch (Exception e) {
            log.warn("[QUIQUP] Could not release dispatch claim on order {}: {}", orderId, e.getMessage());
        }
    }

    private void recordSuccess(UUID orderId, String quiqupOrderId) {
        txTemplate.executeWithoutResult(status -> orderRepo.findById(orderId).ifPresent(o -> {
            o.setQuiqupOrderId(quiqupOrderId);
            o.setQuiqupDispatchedAt(Instant.now());
            o.setQuiqupDispatchError(null);
            o.setCarrierName("Quiqup");
            o.setTrackingCode(quiqupOrderId);
            orderRepo.save(o);
        }));
    }

    private void recordFailure(UUID orderId, String reason) {
        try {
            txTemplate.executeWithoutResult(status -> orderRepo.findById(orderId).ifPresent(o -> {
                o.setQuiqupDispatchError(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
                orderRepo.save(o);
            }));
        } catch (Exception e) {
            // Recording the failure must never become the failure.
            log.error("[QUIQUP] Could not record dispatch failure on order {}: {}", orderId, e.getMessage());
        }
    }

    // =========================================================================
    // Response parsing
    // =========================================================================

    /**
     * Pulls Quiqup's order id out of a create response.
     *
     * <p>Their payloads nest it inconsistently between endpoints, so this looks in the documented
     * places rather than assuming one. Returning null when none is found is deliberate and is
     * handled as "needs a human": a job may well have been created, and retrying blind would book
     * a second courier for the same parcel.
     */
    static String extractOrderId(QuiqupApiResult result) {
        if (result == null || !(result.body() instanceof JsonNode node)) {
            return null;
        }
        for (String field : new String[]{"id", "order_id", "uuid", "reference"}) {
            JsonNode direct = node.get(field);
            if (direct != null && !direct.isNull() && !direct.asText().isBlank()) {
                return direct.asText();
            }
        }
        for (String wrapper : new String[]{"order", "data", "result"}) {
            JsonNode nested = node.get(wrapper);
            if (nested != null && nested.isObject()) {
                for (String field : new String[]{"id", "order_id", "uuid", "reference"}) {
                    JsonNode value = nested.get(field);
                    if (value != null && !value.isNull() && !value.asText().isBlank()) {
                        return value.asText();
                    }
                }
            }
        }
        return null;
    }

    /** The order plus everything needed to describe its collection, detached from the session. */
    private record Snapshot(Order order, List<OrderItem> items, int storeCount,
                            StoreLocation origin, String originPhone, String quiqupOrderId) {
    }
}
