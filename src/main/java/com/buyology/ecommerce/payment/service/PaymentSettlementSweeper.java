package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Asks the gateway about payments it never finished telling us about.
 *
 * <p>Every other recovery path in this codebase waits to be told. The webhook is told; the
 * browser-redirect confirmation is told; the stuck-order reconciler acts only on a transaction
 * that is <em>already</em> SUCCESS, which means something had to have told us first. So when the
 * telling itself fails, nothing notices — and instalment payments are exactly where it fails.
 *
 * <p>Tabby and Tamara settle in two steps: Paymob reports {@code pending} the moment the shopper
 * is approved, then sends a second {@code success} webhook when the provider confirms. The money
 * is real after the first step; the order only becomes PAID on the second. Lose that second
 * delivery — a deploy restarting mid-POST, a network drop, a callback never registered against
 * that integration — and the customer has paid, the Paymob dashboard says paid, and the order sits
 * in PENDING_PAYMENT indefinitely with no alert, no retry, and nobody looking.
 *
 * <p>This job inverts the direction: instead of waiting to be told, it periodically asks. Each
 * candidate goes through {@link PaymentService#recheckOrderPayment}, the same path the admin
 * button uses — so settlement decisions, the amount/currency guard, and the order-paid events all
 * stay in one place rather than being reimplemented here with subtly different rules about what
 * counts as paid.
 *
 * <p>Safe to run on both replicas: re-checking is idempotent (a transaction already terminal is
 * returned untouched), so the worst a duplicate run costs is a redundant GET to Paymob.
 */
@Component
public class PaymentSettlementSweeper {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettlementSweeper.class);

    /**
     * How long a payment gets to settle on its own before we go asking. Long enough that the
     * normal two-step instalment flow completes untouched, short enough that a lost webhook is
     * caught while the customer is still expecting their order to move.
     */
    private static final Duration SETTLE_GRACE = Duration.ofMinutes(20);

    /**
     * How far back to keep asking. A checkout abandoned last month is not a lost payment, and
     * re-querying the whole back catalogue forever would be a standing load on the gateway.
     */
    private static final Duration LOOKBACK = Duration.ofDays(14);

    private static final int BATCH = 40;

    private final PaymentTransactionRepository transactionRepo;
    private final PaymentService paymentService;

    public PaymentSettlementSweeper(PaymentTransactionRepository transactionRepo,
                                    PaymentService paymentService) {
        this.transactionRepo = transactionRepo;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 3 * 60 * 1000L)
    public void sweepUnsettledPayments() {
        Instant now = Instant.now();
        List<PaymentTransaction> candidates = transactionRepo.findUnsettledOrderPayments(
                now.minus(SETTLE_GRACE), now.minus(LOOKBACK), BATCH);
        if (candidates.isEmpty()) return;

        // One order can carry several unsettled attempts (a card try, then an instalment try).
        // recheckOrderPayment already picks the right one, so ask once per order.
        Set<UUID> orderIds = new LinkedHashSet<>();
        for (PaymentTransaction tx : candidates) {
            if (tx.getAppOrderId() != null) orderIds.add(tx.getAppOrderId());
        }

        int settled = 0;
        for (UUID orderId : orderIds) {
            try {
                // Through the proxy on purpose: each order settles in its own transaction, so one
                // failure cannot roll back the ones already recovered, and the order-paid events
                // fire per order after that order's own commit.
                PaymentService.RecheckResult result = paymentService.recheckOrderPayment(orderId, null);
                if (result.settled()) {
                    settled++;
                    log.warn("[SETTLEMENT-SWEEP] Recovered order {} — the gateway had settled it but "
                            + "no webhook ever applied that here", orderId);
                }
            } catch (RuntimeException e) {
                // A gateway hiccup on one order must not abandon the rest of the batch; the next
                // run picks it up again, since it stays unsettled until it genuinely settles.
                log.error("[SETTLEMENT-SWEEP] Re-check failed for order {}", orderId, e);
            }
        }

        log.info("[SETTLEMENT-SWEEP] Checked {} unsettled order payment(s) against the gateway; "
                + "recovered {}", orderIds.size(), settled);
    }
}
