package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The outbound half of cancelling a delivery: stopping the Quiqup job.
 *
 * <p>Before this existed, cancelling a dispatched order refunded the customer, returned their B2B
 * credit and emailed them — and never told Quiqup. The courier collected and delivered anyway: the
 * customer kept the goods and the money, and the later "delivered" webhook was dropped because the
 * order was already terminal, so nothing even recorded that it happened.
 *
 * <p>The core discipline is that <strong>a 2xx from the cancel endpoint is not believed.</strong>
 * The cancel contract is documented as unverified in two places ({@link QuiqupClient} and the
 * properties), and it is a batch endpoint that could quietly skip our id. Every outcome that
 * releases money is therefore verified with a GET on the job — a read, which is never blocked by
 * the production-write guard — and only a job that actually reads as cancelled unlocks the refund.
 */
@Service
public class QuiqupCancelService {

    private static final Logger log = LoggerFactory.getLogger(QuiqupCancelService.class);

    /** How the attempt went. Persisted on the order as a plain string — see Order.quiqupCancelStatus. */
    public enum Outcome {
        /** Quiqup verifiably shows the job cancelled. The refund may proceed. */
        CONFIRMED,
        /** The order never had a Quiqup job. Nothing to stop; the refund may proceed. */
        NOTHING_TO_CANCEL,
        /** The parcel is already collected, in transit or delivered. A human decides the money. */
        REFUSED_TOO_LATE,
        /** No usable answer. Retryable — the job may or may not be stopped. */
        UNCONFIRMED,
        /** Something no retry can fix: config blocked the write, the job id is unknown, or we gave up. */
        NEEDS_HUMAN
    }

    public record CancelResult(Outcome outcome, String detail) {
        /** Only a verified stop — or the absence of any job — lets money leave. */
        public boolean refundAllowed() {
            return outcome == Outcome.CONFIRMED || outcome == Outcome.NOTHING_TO_CANCEL;
        }
    }

    private final QuiqupProperties props;
    private final QuiqupClient client;
    private final OrderRepository orderRepo;
    private final ObjectMapper objectMapper;
    private final UserRoleRepository userRoleRepository;
    private final PushNotificationService pushService;
    private final TransactionTemplate txTemplate;

    public QuiqupCancelService(QuiqupProperties props,
                               QuiqupClient client,
                               OrderRepository orderRepo,
                               ObjectMapper objectMapper,
                               UserRoleRepository userRoleRepository,
                               PushNotificationService pushService,
                               PlatformTransactionManager transactionManager) {
        this.props = props;
        this.client = client;
        this.orderRepo = orderRepo;
        this.objectMapper = objectMapper;
        this.userRoleRepository = userRoleRepository;
        this.pushService = pushService;
        // REQUIRES_NEW, not the default. QuiqupDispatchService gets away with a plain template
        // because dispatch is @Async and runs on a thread with no transaction bound. This service
        // is called from inside OrderService.runAfterCommit on the SAME thread, where the finished
        // transaction is still bound — a REQUIRED template would JOIN it, and every write below
        // would be flushed into a session nobody commits and then discarded. That gotcha has
        // shipped here twice already.
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Exposed so OrderService can branch on the strictness policy without importing the config. */
    public boolean strictCustomerPreflight() {
        return props.getCancel().isStrictCustomerPreflight();
    }

    /**
     * Tries to stop the Quiqup job belonging to this order, and says how it went.
     *
     * <p>Deliberately not {@code @Transactional}: the HTTP call can take up to the configured
     * timeout, and no pooled connection may sit idle behind it. Every write is its own short
     * REQUIRES_NEW transaction, so a crash mid-flight loses nothing that matters — the attempt
     * counter is stamped before the call, and an unfinished attempt is retried by the sweep.
     */
    public CancelResult cancelForOrder(UUID orderId, String reason) {
        // 1. Read what we know, briefly; no connection is held across the HTTP call.
        Order snapshot = txTemplate.execute(status -> orderRepo.findById(orderId).orElse(null));
        if (snapshot == null) {
            return new CancelResult(Outcome.NEEDS_HUMAN, "order not found");
        }
        String quiqupOrderId = snapshot.getQuiqupOrderId();
        if (quiqupOrderId == null || quiqupOrderId.isBlank()) {
            return new CancelResult(Outcome.NOTHING_TO_CANCEL, "order was never dispatched");
        }

        // 2. Idempotent short-circuits: a decided case stays decided, and costs no HTTP.
        String recorded = snapshot.getQuiqupCancelStatus();
        if (Outcome.CONFIRMED.name().equals(recorded) || "CONFIRMED_BY_PARTNER".equals(recorded)) {
            return new CancelResult(Outcome.CONFIRMED, "already confirmed");
        }
        if (Outcome.REFUSED_TOO_LATE.name().equals(recorded)) {
            return new CancelResult(Outcome.REFUSED_TOO_LATE, "already refused: " + snapshot.getQuiqupCancelError());
        }
        if (Outcome.NEEDS_HUMAN.name().equals(recorded)) {
            return new CancelResult(Outcome.NEEDS_HUMAN, "already escalated: " + snapshot.getQuiqupCancelError());
        }

        if (props.isStagingBase()) {
            // A staging job cannot deliver anything; withholding a customer's money over it would
            // be the wrong failure. Recorded as confirmed so nothing retries it.
            persistOutcome(orderId, new CancelResult(Outcome.CONFIRMED, "staging job, no real courier"));
            return new CancelResult(Outcome.CONFIRMED, "staging job, no real courier");
        }
        if (!props.isEnabled() || !props.getCancel().isEnabled()) {
            CancelResult r = new CancelResult(Outcome.NEEDS_HUMAN,
                    "Quiqup module or cancel leg disabled while order carries job " + quiqupOrderId);
            log.error("[QUIQUP] Order {}: cannot stop job {} — the module is disabled. The job may "
                    + "still be live at Quiqup and we have no way to check.", orderId, quiqupOrderId);
            persistOutcome(orderId, r);
            alertSuperadmins(orderId, "Courier cancel needs attention",
                    "Order was cancelled but its Quiqup job could not be stopped (module disabled).");
            return r;
        }

        // 3. Win the right to make this call, atomically, against the other replica.
        Instant now = Instant.now();
        Integer claimed = txTemplate.execute(status ->
                orderRepo.claimForQuiqupCancel(orderId, now, now.minus(staleClaimWindow())));
        if (claimed == null || claimed == 0) {
            return new CancelResult(Outcome.UNCONFIRMED, "claimed by another instance");
        }

        // 4. Count the attempt BEFORE the call — one that crashes mid-flight must still count, or
        // a permanently failing cancel is retried forever.
        int attempts = txTemplate.execute(status -> {
            Order o = orderRepo.findById(orderId).orElse(null);
            if (o == null) return 0;
            if (o.getQuiqupCancelRequestedAt() == null) o.setQuiqupCancelRequestedAt(Instant.now());
            int n = (o.getQuiqupCancelAttempts() == null ? 0 : o.getQuiqupCancelAttempts()) + 1;
            o.setQuiqupCancelAttempts(n);
            orderRepo.save(o);
            return n;
        });

        // 5. The call, exactly as the working admin endpoint makes it: PUT the batch path, id in body.
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("order_ids").add(quiqupOrderId);
        QuiqupApiResult put = client.request("PUT", props.getPaths().getCancel(), body);

        // 6. Decide what actually happened — never on the PUT alone.
        CancelResult result = interpret(put, quiqupOrderId);

        if (result.outcome() == Outcome.UNCONFIRMED && attempts >= props.getCancel().getMaxAttempts()) {
            result = new CancelResult(Outcome.NEEDS_HUMAN,
                    "gave up after " + attempts + " unconfirmed attempts; last: " + result.detail());
        }

        // 7. Make the outcome durable and release the claim.
        persistOutcome(orderId, result);

        if (result.outcome() == Outcome.REFUSED_TOO_LATE || result.outcome() == Outcome.NEEDS_HUMAN) {
            log.error("[QUIQUP] Order {} / job {}: cancel outcome {} — {}. A human has to chase this "
                    + "parcel.", orderId, quiqupOrderId, result.outcome(), result.detail());
            alertSuperadmins(orderId, "Courier cancel needs attention",
                    "Order " + shortId(orderId) + ": Quiqup job " + quiqupOrderId + " — "
                            + result.outcome() + ". " + truncate(result.detail(), 120));
        }
        return result;
    }

    // ── Classification ───────────────────────────────────────────────────────

    /**
     * What a PUT + verifying GET actually mean. Package-private and free of I/O beyond the one GET,
     * so the tests can drive every branch with canned results.
     */
    CancelResult interpret(QuiqupApiResult put, String quiqupOrderId) {
        if (put == null) {
            return new CancelResult(Outcome.UNCONFIRMED, "no response from the cancel call");
        }
        if (isBlockedByOurOwnGuard(put)) {
            // Our own client refused the write (production-write guard or path guard). Config, not
            // weather: retrying can never succeed.
            return new CancelResult(Outcome.NEEDS_HUMAN, "blocked by client guard: " + put.body());
        }
        if (put.status() >= 500 || put.status() == 408 || put.status() == 429) {
            return new CancelResult(Outcome.UNCONFIRMED, "transient " + put.status() + " from the cancel call");
        }

        // 2xx or a plain 4xx: ALWAYS verify with a read. The write contract is unverified, the
        // endpoint is a batch that could silently skip our id, and this outcome releases money.
        QuiqupApiResult get = client.request("GET",
                QuiqupClient.fillPath(props.getPaths().getGet(), quiqupOrderId), null);
        return interpretVerification(put.ok(), get);
    }

    /** The verdict, from the verifying GET alone. Pure — fully unit-testable. */
    static CancelResult interpretVerification(boolean putOk, QuiqupApiResult get) {
        if (get == null || !get.ok()) {
            // Never treat a missing job as cancelled: a 404 is as likely a wrong id as a stopped
            // job, and CONFIRMED here releases the customer's refund on no evidence at all.
            return putOk
                    ? new CancelResult(Outcome.UNCONFIRMED, "cancel accepted but the job could not be re-read")
                    : new CancelResult(Outcome.NEEDS_HUMAN, "cancel rejected and the job could not be re-read");
        }
        String state = extractState(get);
        OrderStatus mapped = QuiqupStatusMapper.toOrderStatus(state);

        if (mapped == OrderStatus.CANCELLED) {
            return new CancelResult(Outcome.CONFIRMED, "job reads as '" + state + "'");
        }
        if (mapped == OrderStatus.IN_COURIER || mapped == OrderStatus.IN_TRANSIT
                || mapped == OrderStatus.DELIVERED) {
            return new CancelResult(Outcome.REFUSED_TOO_LATE, "job reads as '" + state + "'");
        }
        // Unstarted states (pending, ready_for_collection…) map to null: the job exists and is not
        // cancelled. A 2xx PUT that changed nothing is worth retrying; a 4xx that changed nothing
        // is a refusal we do not understand. FAILED-mapping states (rejected, returned) also land
        // here deliberately — "returned" means the parcel was collected, and that is a human's call.
        return putOk
                ? new CancelResult(Outcome.UNCONFIRMED, "job still reads as '" + state + "' after the cancel")
                : new CancelResult(Outcome.NEEDS_HUMAN, "cancel refused; job reads as '" + state + "'");
    }

    /** Both client guards return a plain string body starting with "Blocked:". */
    private static boolean isBlockedByOurOwnGuard(QuiqupApiResult put) {
        return (put.status() == 409 || put.status() == 400)
                && put.body() instanceof String s && s.startsWith("Blocked:");
    }

    /** The job's state, wherever this endpoint chose to nest it. Mirrors extractOrderId. */
    static String extractState(QuiqupApiResult result) {
        if (result == null || !(result.body() instanceof JsonNode node)) {
            return null;
        }
        for (String field : new String[]{"state", "status"}) {
            JsonNode direct = node.get(field);
            if (direct != null && direct.isTextual() && !direct.asText().isBlank()) {
                return direct.asText();
            }
        }
        for (String wrapper : new String[]{"order", "data", "result"}) {
            JsonNode nested = node.get(wrapper);
            if (nested != null && nested.isObject()) {
                for (String field : new String[]{"state", "status"}) {
                    JsonNode value = nested.get(field);
                    if (value != null && value.isTextual() && !value.asText().isBlank()) {
                        return value.asText();
                    }
                }
            }
        }
        return null;
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private void persistOutcome(UUID orderId, CancelResult result) {
        txTemplate.executeWithoutResult(status -> orderRepo.findById(orderId).ifPresent(o -> {
            o.setQuiqupCancelStatus(result.outcome().name());
            o.setQuiqupCancelError(result.outcome() == Outcome.CONFIRMED
                    ? null : truncate(result.detail(), 1000));
            if (result.outcome() == Outcome.CONFIRMED) {
                o.setQuiqupCancelConfirmedAt(Instant.now());
            }
            // Release the claim so a retry need not wait out the stale window.
            o.setQuiqupCancelClaimedAt(null);
            orderRepo.save(o);
        }));
    }

    /**
     * Must comfortably exceed the request timeout — a claim expiring while the original call is
     * still in flight would let the retry fire a second cancel, racing the refund gate. Identical
     * reasoning to QuiqupDispatchService.staleClaimWindow.
     */
    private Duration staleClaimWindow() {
        return Duration.ofMillis(props.getTimeoutMs()).plus(Duration.ofMinutes(5));
    }

    private void alertSuperadmins(UUID orderId, String title, String bodyText) {
        try {
            Map<String, String> data = Map.of("orderId", orderId.toString(), "type", "QUIQUP_CANCEL");
            userRoleRepository.findUserIdsByRoleName("SUPERADMIN").forEach(uid ->
                    pushService.sendToUser(uid, title, bodyText, "QUIQUP_CANCEL", data));
        } catch (Exception e) {
            log.warn("[QUIQUP] Could not alert superadmins about order {}: {}", orderId, e.getMessage());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }
}
