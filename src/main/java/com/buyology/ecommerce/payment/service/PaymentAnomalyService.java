package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.payment.domain.PaymentAnomaly;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.enums.PaymentAnomalyKind;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.repository.PaymentAnomalyRepository;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Records a settled payment that could not be applied to its order, and makes it somebody's
 * problem.
 *
 * <p>The failure this replaces was total silence: onPaymentSucceeded had no else-branch, so a
 * payment landing on a CANCELLED order — money captured, card charged — fell out of the lambda
 * with no log, no record and no refund, and reconcileStuckPayments only scanned PENDING_PAYMENT so
 * it never saw it either.
 *
 * <p>Deliberately does NOT depend on OrderService — it takes repositories — so OrderService can
 * inject it plainly with no construction cycle.
 */
@Service
public class PaymentAnomalyService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAnomalyService.class);

    /** What the listener should do with a settled payment, given the order it points at. */
    public enum Outcome { APPLY, ALREADY_APPLIED, ANOMALY }

    public record Classification(Outcome outcome, PaymentAnomalyKind kind) {
        static Classification apply() { return new Classification(Outcome.APPLY, null); }
        static Classification alreadyApplied() { return new Classification(Outcome.ALREADY_APPLIED, null); }
        static Classification anomaly(PaymentAnomalyKind kind) { return new Classification(Outcome.ANOMALY, kind); }
    }

    private final PaymentAnomalyRepository anomalyRepo;
    private final PaymentTransactionRepository transactionRepo;
    private final UserRoleRepository userRoleRepository;
    private final PushNotificationService pushService;
    private final ObjectProvider<PaymentAnomalyService> self;

    public PaymentAnomalyService(PaymentAnomalyRepository anomalyRepo,
                                 PaymentTransactionRepository transactionRepo,
                                 UserRoleRepository userRoleRepository,
                                 PushNotificationService pushService,
                                 ObjectProvider<PaymentAnomalyService> self) {
        this.anomalyRepo = anomalyRepo;
        this.transactionRepo = transactionRepo;
        this.userRoleRepository = userRoleRepository;
        this.pushService = pushService;
        this.self = self;
    }

    /**
     * Decides what a settled payment means for the order it references. Pure — every input is a
     * value or a lazy supplier — so the whole decision table is unit-testable.
     *
     * @param settledByAnother a SUPPLIER because the extra query behind it must only run on the
     *                         anomaly branch, never on the happy path every payment takes
     */
    public static Classification classify(OrderStatus status, boolean txIsOrdersOwn,
                                          boolean amountSufficient, BooleanSupplier settledByAnother) {
        if (status == OrderStatus.PENDING_PAYMENT) {
            return amountSufficient
                    ? Classification.apply()
                    : Classification.anomaly(PaymentAnomalyKind.UNDERPAID);
        }
        if (txIsOrdersOwn) {
            // The order already carries THIS transaction: a replayed event (webhook + redirect
            // both fire), not a second charge.
            return Classification.alreadyApplied();
        }
        if (status == OrderStatus.CANCELLED || status == OrderStatus.FAILED) {
            return Classification.anomaly(PaymentAnomalyKind.PAID_AFTER_CANCELLED);
        }
        if (settledByAnother.getAsBoolean()) {
            return Classification.anomaly(PaymentAnomalyKind.DUPLICATE_CHARGE);
        }
        // The branch where we explicitly do not know what happened — precisely where automation
        // must not move money.
        return Classification.anomaly(PaymentAnomalyKind.UNEXPECTED_ORDER_STATE);
    }

    /** Whether the order is already settled by a DIFFERENT successful payment. */
    public boolean settledByAnotherSuccessfulPayment(UUID orderId, UUID thisTxId) {
        return transactionRepo.findAllByAppOrderId(orderId).stream()
                .anyMatch(t -> !t.getId().equals(thisTxId)
                        && (t.getStatus() == PaymentStatus.SUCCESS
                            || t.getStatus() == PaymentStatus.PARTIALLY_REFUNDED));
    }

    /**
     * Records the anomaly durably and alerts the superadmins — once per payment, ever.
     *
     * <p>The duplicate handling is deliberately layered: the exists() pre-check keeps the common
     * replay quiet and cheap, and the unique index is the authority when two replicas race the
     * check. The constraint violation is caught HERE, outside the REQUIRES_NEW boundary — inside
     * it the persistence context is already rollback-only and touching the EntityManager again is
     * undefined. Losing the race alerts nobody: admins must not get one push per replica per sweep
     * for the same incident.
     *
     * @return true when this call actually recorded (and alerted) the anomaly
     */
    public boolean recordAndAlert(PaymentAnomalyKind kind, PaymentTransaction tx, UUID orderId,
                                  OrderStatus orderStatus, String detail, String detectedBy) {
        if (anomalyRepo.existsByPaymentTransactionId(tx.getId())) {
            return false;
        }
        try {
            self.getObject().insert(kind, tx, orderId, orderStatus, detail, detectedBy);
        } catch (DataIntegrityViolationException e) {
            log.debug("[PAYMENT-ANOMALY] Lost the recording race for tx {} — already recorded", tx.getId());
            return false;
        }

        log.error("[PAYMENT-ANOMALY] {} — tx {} ({} {}) against order {} ({}). {}",
                kind, tx.getId(), tx.getAmount(), tx.getCurrency(), orderId, orderStatus, detail);
        try {
            Map<String, String> data = Map.of(
                    "transactionId", tx.getId().toString(),
                    "orderId", orderId == null ? "" : orderId.toString(),
                    "type", "PAYMENT_ANOMALY");
            String body = kind + ": " + tx.getAmount() + " " + tx.getCurrency()
                    + (orderId == null ? " with no order" : " on order " + shortId(orderId))
                    + (kind.autoRefunds() ? " — auto-refund queued" : " — needs review");
            userRoleRepository.findUserIdsByRoleName("SUPERADMIN").forEach(uid ->
                    pushService.sendToUser(uid, "Payment needs attention", body, "PAYMENT_ANOMALY", data));
        } catch (Exception e) {
            log.warn("[PAYMENT-ANOMALY] Could not alert superadmins for tx {}: {}", tx.getId(), e.getMessage());
        }
        return true;
    }

    /**
     * The insert alone, REQUIRES_NEW and proxy-invoked: the evidence must commit on its own
     * connection BEFORE the caller's transaction writes anything that could fail — an anomaly
     * record that rolls back with the listener defeats its purpose.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(PaymentAnomalyKind kind, PaymentTransaction tx, UUID orderId,
                       OrderStatus orderStatus, String detail, String detectedBy) {
        PaymentAnomaly a = new PaymentAnomaly();
        a.setPaymentTransactionId(tx.getId());
        a.setAppOrderId(orderId);
        a.setKind(kind.name());
        a.setOrderStatus(orderStatus == null ? null : orderStatus.name());
        a.setAmount(tx.getAmount());
        a.setCurrency(tx.getCurrency());
        a.setDetail(detail);
        a.setDetectedBy(detectedBy);
        anomalyRepo.saveAndFlush(a);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }
}
