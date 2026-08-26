package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.payment.domain.PaymentAnomaly;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.dto.RefundRequest;
import com.buyology.ecommerce.payment.dto.RefundResponse;
import com.buyology.ecommerce.payment.enums.PaymentAnomalyKind;
import com.buyology.ecommerce.payment.repository.PaymentAnomalyRepository;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The sweep behind the listener: finds settled order payments nobody applied, and refunds the two
 * unambiguous kinds.
 *
 * <p>Detection is driven from the TRANSACTION side on purpose. The cases it must find include a
 * payment whose order was never created at all, which no scan over orders can see; and a healthy
 * payment — one its order points back at — is excluded in SQL, so the back catalogue cannot fill
 * the page and starve the sweep.
 *
 * <p>Execution moves money, so it follows the repo's one rule about that: no transaction is held
 * anywhere near the gateway call. The claim (a one-statement conditional UPDATE) commits first,
 * initiateRefund runs with nothing open — it does its own claim-commit-call-settle — and the
 * outcome is recorded after. Idempotency is layered: the anomaly claim beats the other replica,
 * and even if both somehow passed, RefundClaimStore's guard counting PENDING refunds refuses the
 * second send before any HTTP.
 */
@Component
public class PaymentAnomalyReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentAnomalyReconciler.class);

    private static final int DETECT_LIMIT = 100;
    private static final int REFUND_LIMIT = 25;
    private static final int MAX_REFUND_ATTEMPTS = 3;
    /** A payment younger than this may still be mid-flow (listener racing the webhook); leave it. */
    private static final Duration SETTLE_GRACE = Duration.ofMinutes(10);

    private final PaymentTransactionRepository transactionRepo;
    private final PaymentAnomalyRepository anomalyRepo;
    private final OrderRepository orderRepo;
    private final PaymentAnomalyService anomalyService;
    private final PaymentService paymentService;

    public PaymentAnomalyReconciler(PaymentTransactionRepository transactionRepo,
                                    PaymentAnomalyRepository anomalyRepo,
                                    OrderRepository orderRepo,
                                    PaymentAnomalyService anomalyService,
                                    PaymentService paymentService) {
        this.transactionRepo = transactionRepo;
        this.anomalyRepo = anomalyRepo;
        this.orderRepo = orderRepo;
        this.anomalyService = anomalyService;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${payment.anomaly-sweep-interval-ms:300000}",
               initialDelayString = "${payment.anomaly-sweep-initial-delay-ms:60000}")
    public void sweep() {
        try {
            detect();
        } catch (Exception e) {
            log.error("[PAYMENT-ANOMALY] Detection pass failed", e);
        }
        try {
            executeAutoRefunds();
        } catch (Exception e) {
            log.error("[PAYMENT-ANOMALY] Refund pass failed", e);
        }
    }

    private void detect() {
        Instant cutoff = Instant.now().minus(SETTLE_GRACE);
        List<PaymentTransaction> unclaimed =
                transactionRepo.findUnreviewedSettledOrderPayments(cutoff, DETECT_LIMIT);
        for (PaymentTransaction tx : unclaimed) {
            Order order = tx.getAppOrderId() == null
                    ? null : orderRepo.findById(tx.getAppOrderId()).orElse(null);
            if (order == null) {
                // A trashed order still exists; it is only hidden from Hibernate by the entity's
                // SQLRestriction. Alarming here would report every deliberate deletion as a lost
                // payment, so the row's real absence is what must be checked.
                if (tx.getAppOrderId() != null && orderRepo.existsIncludingTrash(tx.getAppOrderId())) {
                    continue;
                }
                anomalyService.recordAndAlert(PaymentAnomalyKind.ORPHANED_NO_ORDER, tx,
                        tx.getAppOrderId(), null,
                        "sweep: SUCCESS payment references a missing order row", "RECONCILER");
                continue;
            }
            // PENDING_PAYMENT (and legacy PROCESSING) belong to reconcileStuckPayments, which
            // promotes them; recording an anomaly here would flag an order that is about to become
            // healthy.
            @SuppressWarnings("deprecation")
            boolean stillPayable = order.getStatus() == OrderStatus.PENDING_PAYMENT
                    || order.getStatus() == OrderStatus.PROCESSING;
            if (stillPayable) {
                continue;
            }
            PaymentAnomalyKind kind =
                    (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FAILED)
                            ? PaymentAnomalyKind.PAID_AFTER_CANCELLED
                            : anomalyService.settledByAnotherSuccessfulPayment(order.getId(), tx.getId())
                                    ? PaymentAnomalyKind.DUPLICATE_CHARGE
                                    : PaymentAnomalyKind.UNEXPECTED_ORDER_STATE;
            anomalyService.recordAndAlert(kind, tx, order.getId(), order.getStatus(),
                    "sweep: payment " + tx.getAmount() + " " + tx.getCurrency()
                            + " is not the settling transaction of its " + order.getStatus() + " order",
                    "RECONCILER");
        }
    }

    private void executeAutoRefunds() {
        List<PaymentAnomaly> open = anomalyRepo.findByResolutionOrderByCreatedAtAsc(
                "OPEN", PageRequest.of(0, REFUND_LIMIT));
        for (PaymentAnomaly anomaly : open) {
            PaymentAnomalyKind kind;
            try {
                kind = PaymentAnomalyKind.valueOf(anomaly.getKind());
            } catch (IllegalArgumentException e) {
                continue;   // a kind this build does not know — a human's, by definition
            }
            if (!kind.autoRefunds() || anomaly.getAttempts() >= MAX_REFUND_ATTEMPTS) {
                continue;
            }
            // Win the claim or move on. One statement; the other replica gets 0.
            if (claim(anomaly.getId()) == 0) {
                continue;
            }
            try {
                RefundRequest req = new RefundRequest();
                req.setTransactionId(anomaly.getPaymentTransactionId());
                req.setAmount(anomaly.getAmount());
                req.setReason("Auto-refund: " + kind + " (payment review "
                        + anomaly.getId().toString().substring(0, 8) + ")");
                // No transaction is open here. initiateRefund claims, calls, settles on its own.
                RefundResponse refund = paymentService.initiateRefund(req);
                settle(anomaly.getId(), refund.getId());
                log.info("[PAYMENT-ANOMALY] Auto-refunded {} — refund {} for tx {}",
                        anomaly.getId(), refund.getId(), anomaly.getPaymentTransactionId());
            } catch (Exception e) {
                // Back to OPEN; the attempt counter (bumped by the claim) is what stops a
                // permanently failing refund from being retried forever. The customer's money is
                // protected regardless: an ambiguous gateway failure left a PENDING refund claim,
                // and RefundClaimStore counts those, so a retry cannot double-send.
                log.error("[PAYMENT-ANOMALY] Auto-refund failed for {} (attempt {}): {}",
                        anomaly.getId(), anomaly.getAttempts(), e.getMessage());
                release(anomaly.getId());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int claim(UUID anomalyId) {
        return anomalyRepo.claimForRefund(anomalyId, MAX_REFUND_ATTEMPTS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void settle(UUID anomalyId, UUID refundId) {
        anomalyRepo.findById(anomalyId).ifPresent(a -> {
            a.setResolution("AUTO_REFUNDED");
            a.setRefundId(refundId);
            anomalyRepo.save(a);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void release(UUID anomalyId) {
        anomalyRepo.findById(anomalyId).ifPresent(a -> {
            a.setResolution("OPEN");
            anomalyRepo.save(a);
        });
    }
}
