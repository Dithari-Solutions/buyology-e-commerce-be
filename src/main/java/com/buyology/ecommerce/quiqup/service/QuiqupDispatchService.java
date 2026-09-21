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

    /**
     * Says once, at startup, what dispatch is about to do — and shouts if that is probably not what
     * anyone intended.
     *
     * <p>The dangerous configuration is dispatch ON against Quiqup's STAGING estate. It passes every
     * guard: the base URL is staging so the production-write check is satisfied, jobs are accepted,
     * ids come back, and each order records a clean dispatch with no error. No courier is ever
     * assigned, because the job only exists in a test environment. The order looks healthy on our
     * side and the parcel never moves — silent, and invisible until a customer asks where it is.
     *
     * <p>Not a startup failure, because that same combination is exactly right on a staging
     * deployment. It cannot be told apart from configuration alone, so this is as loud as it can
     * honestly be.
     */
    @jakarta.annotation.PostConstruct
    void announce() {
        if (!enabled()) {
            log.info("[QUIQUP] Automatic dispatch is OFF (quiqup.enabled={}, quiqup.dispatch.enabled={}).",
                    props.isEnabled(), props.getDispatch().isEnabled());
            return;
        }
        if (props.isStagingBase()) {
            log.warn("[QUIQUP] ******************************************************************");
            log.warn("[QUIQUP] DISPATCH IS ON AND POINTED AT QUIQUP STAGING ({}).", props.getBaseUrl());
            log.warn("[QUIQUP] Real paid orders will be sent to a TEST environment. They will be");
            log.warn("[QUIQUP] accepted, they will record a Quiqup id, and NO COURIER WILL COME.");
            log.warn("[QUIQUP] Correct for a staging deployment. On production, set");
            log.warn("[QUIQUP] QUIQUP_BASE_URL to the live estate or QUIQUP_DISPATCH_ENABLED=false.");
            log.warn("[QUIQUP] ******************************************************************");
            return;
        }
        log.warn("[QUIQUP] Automatic dispatch is ON against {} — paid orders will be sent to REAL "
                + "couriers (autoReadyForCollection={}).", props.getBaseUrl(),
                props.getDispatch().isAutoReadyForCollection());
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
        // Where the attempt got to, for the catch below. Releasing a claim this call never took
        // would free another instance's in-flight dispatch; freeing the order after the create was
        // sent would let the retry book a second courier.
        boolean claimed = false;
        boolean sent = false;
        int attempt = 0;
        try {
            Snapshot snap = txTemplate.execute(status -> loadSnapshot(orderId));
            if (snap == null) {
                return "Order not found";
            }
            if (snap.quiqupOrderId != null && !snap.quiqupOrderId.isBlank()) {
                return "Already dispatched (Quiqup order " + snap.quiqupOrderId + ")";
            }
            // "Not packed yet" is not a failure, and must not be written onto the order as one. A CASH
            // order makes this visible: it is created PENDING_PAYMENT and fires its handoff event right
            // then, so every cash order used to stamp "Order is PENDING_PAYMENT, not awaiting dispatch"
            // into quiqupDispatchError — shown in red on the admin page — before anybody had done
            // anything wrong. The retry sweep picks the order up once it reaches PACKAGING.
            String notReady = notReadyYet(snap);
            if (notReady != null) {
                log.debug("[QUIQUP] Order {} not dispatchable yet — {}", orderId, notReady);
                return notReady;
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
            claimed = true;

            // Counted before the call, like the cancel leg: an attempt that dies mid-flight must
            // still count, or a create that keeps failing is sent forever.
            attempt = countAttempt(orderId);

            ObjectNode payload = mapper.toCreatePayload(
                    snap.order, snap.origin, snap.originPhone, snap.items);

            sent = true;
            QuiqupApiResult result = client.request("POST", props.getPaths().getCreate(), payload);
            if (result == null || !result.ok()) {
                String reason = result == null
                        ? "No response from Quiqup"
                        : "Create call failed with " + result.status() + ": " + result.body();
                return settleFailure(orderId, attempt, classifyFailure(result), reason);
            }

            String quiqupId = extractOrderId(result);
            if (quiqupId == null) {
                // Accepted but unidentifiable: Quiqup may well have created the job, and retrying
                // would book a second courier for the same parcel, which is worse than a stuck order.
                return settleFailure(orderId, attempt, Failure.UNKNOWN,
                        "Quiqup accepted the job but returned no id. Response: " + result.body());
            }

            recordSuccess(orderId, quiqupId, extractTrackingUrl(result));
            log.info("[QUIQUP] Order {} dispatched as Quiqup order {}", orderId, quiqupId);

            if (props.getDispatch().isAutoReadyForCollection()) {
                markReadyForCollection(orderId, quiqupId);
            }
            return "Dispatched (Quiqup order " + quiqupId + ")";

        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("[QUIQUP] Dispatch failed for order {}", orderId, e);
            if (sent) {
                // Failed after the create went out, e.g. while recording Quiqup's id. The job may
                // exist, so this is the same as an unanswered create.
                return settleFailure(orderId, attempt, Failure.UNKNOWN,
                        "Failed after sending the job to Quiqup: " + reason);
            }
            if (claimed) {
                return settleFailure(orderId, attempt, Failure.RETRY, reason);
            }
            recordFailure(orderId, reason);
            return reason;
        }
    }

    // =========================================================================
    // Failed creates
    // =========================================================================

    /** What a failed create means for the order, and so whether the retry job may send it again. */
    enum Failure {
        /** Nothing was created and a later attempt may work. Retried, up to max-attempts. */
        RETRY,
        /** Refused outright, by Quiqup or by our own guard. The same payload fails the same way. */
        REJECTED,
        /**
         * The create may have gone through without an answer. Quiqup do not deduplicate on
         * partner_order_id, so sending it again can book a second courier for the same parcel.
         */
        UNKNOWN
    }

    /**
     * Sorts a failed create. Pure, so every branch is tested without a network.
     *
     * <p>A 422 is the case that forced this. Quiqup refused every cash job on payment_mode, and the
     * retry job sent the identical payload every five minutes from both replicas, about 425 times
     * in a day for one order, until Quiqup asked us to stop.
     */
    static Failure classifyFailure(QuiqupApiResult result) {
        if (result == null) {
            return Failure.UNKNOWN;
        }
        if (!result.mayHaveReachedQuiqup()) {
            // Never left this server. Our own guard refusing the write is configuration, which no
            // retry fixes; anything else (Quiqup unreachable, a token exchange failing) may clear.
            return isBlockedByOurOwnGuard(result) ? Failure.REJECTED : Failure.RETRY;
        }
        int status = result.status();
        // Answers that say the request was not processed: timed out waiting for it, rate-limited,
        // or not serving at all.
        if (status == 408 || status == 429 || status == 503) {
            return Failure.RETRY;
        }
        if (status >= 400 && status < 500) {
            return Failure.REJECTED;
        }
        // 500, 502, 504 and no answer at all: it may have created the job before failing.
        return Failure.UNKNOWN;
    }

    /** Both client guards return a plain string body starting with "Blocked:". */
    private static boolean isBlockedByOurOwnGuard(QuiqupApiResult result) {
        return result.body() instanceof String s && s.startsWith("Blocked:");
    }

    /** Records a failed create and decides whether the retry job may try again. */
    private String settleFailure(UUID orderId, int attempt, Failure failure, String reason) {
        switch (failure) {
            case RETRY -> {
                releaseClaim(orderId);
                int max = props.getDispatch().getMaxAttempts();
                if (attempt >= max) {
                    reason = "Automatic retries stopped after " + attempt + " attempts. Last: " + reason;
                    stop(orderId, reason);
                } else {
                    recordFailure(orderId, reason);
                }
            }
            case REJECTED -> {
                // Nothing was created, so the claim goes back: an admin who corrects the order can
                // dispatch it straight away.
                releaseClaim(orderId);
                reason = "Automatic retries stopped: " + reason;
                stop(orderId, reason);
            }
            case UNKNOWN -> {
                // Claim deliberately kept. Quiqup may have created the job, and only a human who has
                // looked for it should send another.
                // The instruction goes first: the column holds 1000 characters and a response body
                // can fill it.
                reason = "Automatic retries stopped: Quiqup may have created this job. Look for "
                        + QuiqupOrderMapper.partnerOrderId(orderId)
                        + " in the Quiqup dashboard before dispatching again. " + reason;
                stop(orderId, reason);
            }
        }
        log.error("[QUIQUP] Dispatch failed for order {} (attempt {}, {}) — {}", orderId, attempt, failure, reason);
        return reason;
    }

    /**
     * What would be sent for this order, without sending it.
     *
     * <p>The point of a dry run is that the expensive mistakes in this payload are the ones that
     * look fine. A transposed coordinate is a valid coordinate, a swapped origin and destination is
     * a well-formed job, and neither produces an error anywhere — the first report is a courier at
     * the wrong door. Being able to read the exact payload for a REAL order, before any of it
     * reaches a carrier, is the only cheap way to catch those.
     *
     * <p>Runs every eligibility check the real dispatch runs, so a refusal here is the refusal you
     * would get, not an approximation of it.
     */
    public DispatchPreview preview(UUID orderId) {
        Snapshot snap = txTemplate.execute(status -> loadSnapshot(orderId));
        if (snap == null) {
            return new DispatchPreview(false, "Order not found", null, null);
        }
        if (snap.quiqupOrderId != null && !snap.quiqupOrderId.isBlank()) {
            return new DispatchPreview(false, "Already dispatched", null, snap.quiqupOrderId);
        }
        String refusal = refuseReason(snap);
        if (refusal != null) {
            return new DispatchPreview(false, refusal, null, null);
        }
        return new DispatchPreview(true, null,
                mapper.toCreatePayload(snap.order, snap.origin, snap.originPhone, snap.items), null);
    }

    /**
     * @param dispatchable  whether this order would be sent
     * @param reason        why not, when it would not
     * @param payload       exactly what would be POSTed to Quiqup
     * @param quiqupOrderId the existing job id, when the order has already been dispatched
     */
    public record DispatchPreview(boolean dispatchable, String reason,
                                  com.fasterxml.jackson.databind.JsonNode payload,
                                  String quiqupOrderId) {
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
    /**
     * The currency a Quiqup cash collection is implicitly in.
     *
     * <p>Their create payload has no currency field, so the amount is interpreted in the market the
     * courier settles in. Quiqup are a UAE operation, so that is AED — and an order in any other
     * currency cannot be handed to them for collection without misstating what is owed.
     */
    private static final String QUIQUP_SETTLEMENT_CURRENCY = "AED";

    /**
     * Why this order is not ready for a courier YET, or null when it is.
     *
     * <p>Separate from {@link #refuseReason} because the two mean different things to an admin. A
     * refusal is a problem with the order that somebody has to resolve — no coordinates, two stores,
     * no phone — and belongs on the order in red. "Not packed yet" is the normal state of a healthy
     * order that simply has not got there, and recording it as a failure turns every cash order into
     * a false alarm the moment it is created.
     */
    private String notReadyYet(Snapshot snap) {
        OrderStatus status = snap.order.getStatus();
        if (status != OrderStatus.PAID && status != OrderStatus.PACKAGING) {
            return "Order is " + status + ", not awaiting dispatch";
        }
        return null;
    }

    private String refuseReason(Snapshot snap) {
        Order order = snap.order;

        String notReady = notReadyYet(snap);
        if (notReady != null) {
            return notReady;
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
        // A cash job tells Quiqup an AMOUNT and no currency — their payload has no currency field, so
        // the number is implicitly whatever the courier's market settles in. That is safe while the
        // order is in AED and actively dangerous otherwise: a 400 AZN order would be collected as 400
        // AED. Refusing is the only honest option, because there is nowhere to put the currency.
        //
        // Also refuses a cash order with no total at all, rather than sending a courier to collect 0.
        if (order.isCashOnDelivery() && !order.isMoneyCollected()) {
            if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
                return "Cash order has no amount to collect";
            }
            if (order.getCurrency() == null
                    || !QUIQUP_SETTLEMENT_CURRENCY.equalsIgnoreCase(order.getCurrency())) {
                return "Cash order is in " + order.getCurrency() + " but a Quiqup job carries no "
                        + "currency, so the amount would be collected as " + QUIQUP_SETTLEMENT_CURRENCY
                        + "; needs manual handling";
            }
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

    /**
     * Counts a create attempt and returns the new total.
     *
     * <p>Also clears a previous stop: a new attempt is an admin dispatching by hand (the retry job
     * never picks up a stopped order), and its own outcome decides whether retries stop again.
     */
    private int countAttempt(UUID orderId) {
        Integer attempts = txTemplate.execute(status -> {
            Order o = orderRepo.findById(orderId).orElse(null);
            if (o == null) return 0;
            int n = (o.getQuiqupDispatchAttempts() == null ? 0 : o.getQuiqupDispatchAttempts()) + 1;
            o.setQuiqupDispatchAttempts(n);
            o.setQuiqupDispatchStoppedAt(null);
            orderRepo.save(o);
            return n;
        });
        return attempts == null ? 0 : attempts;
    }

    /** Records the failure and takes the order out of the retry job's worklist. */
    private void stop(UUID orderId, String reason) {
        try {
            txTemplate.executeWithoutResult(status -> orderRepo.findById(orderId).ifPresent(o -> {
                o.setQuiqupDispatchError(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
                o.setQuiqupDispatchStoppedAt(Instant.now());
                orderRepo.save(o);
            }));
        } catch (Exception e) {
            // Same rule as recordFailure: recording the failure must never become the failure.
            log.error("[QUIQUP] Could not stop retries on order {}: {}", orderId, e.getMessage());
        }
    }

    private void recordSuccess(UUID orderId, String quiqupOrderId, String trackingUrl) {
        txTemplate.executeWithoutResult(status -> orderRepo.findById(orderId).ifPresent(o -> {
            o.setQuiqupOrderId(quiqupOrderId);
            o.setQuiqupDispatchedAt(Instant.now());
            o.setQuiqupDispatchError(null);
            o.setQuiqupDispatchStoppedAt(null);
            o.setCarrierName("Quiqup");
            // trackingCode is surfaced to the customer on OrderResponse, so it holds the thing a
            // customer can actually use. Quiqup's numeric job id is not that — it means nothing
            // outside their dashboard — and it is already kept on quiqupOrderId for our own lookups.
            if (trackingUrl != null && trackingUrl.length() <= 100) {
                o.setTrackingCode(trackingUrl);
            } else {
                o.setTrackingCode(quiqupOrderId);
            }
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

    /**
     * Pulls the customer-facing tracking link out of a create response.
     *
     * <p>Quiqup mint one per job and share it with the customer themselves, and it is the only live
     * tracking this channel has — our own WebSocket tracking follows our courier fleet, which is not
     * who is carrying this parcel. Storing it means a customer asking "where is it" has an answer
     * that does not involve anyone opening the Quiqup dashboard.
     */
    static String extractTrackingUrl(QuiqupApiResult result) {
        if (result == null || !(result.body() instanceof JsonNode node)) {
            return null;
        }
        JsonNode order = node.get("order");
        JsonNode source = order != null && order.isObject() ? order : node;
        JsonNode url = source.get("tracking_url");
        return url == null || url.isNull() || url.asText().isBlank() ? null : url.asText();
    }

    /** The order plus everything needed to describe its collection, detached from the session. */
    private record Snapshot(Order order, List<OrderItem> items, int storeCount,
                            StoreLocation origin, String originPhone, String quiqupOrderId) {
    }
}
