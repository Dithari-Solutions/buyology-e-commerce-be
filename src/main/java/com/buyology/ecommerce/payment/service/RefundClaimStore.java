package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.payment.domain.PaymentRefund;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.dto.RefundRequest;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.enums.RefundStatus;
import com.buyology.ecommerce.payment.repository.PaymentRefundRepository;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Owns the database side of a refund, so that the gateway call can happen outside a transaction.
 *
 * <p>A separate bean rather than methods on {@code PaymentService}, because Spring applies
 * {@code @Transactional} through a proxy: a plain {@code this.} call would silently join the
 * caller's transaction and roll back with it, which is the exact failure this exists to prevent.
 *
 * <p>The problem it solves: the gateway call used to sit inside the refund transaction with the
 * record written afterwards. Anything that rolled that transaction back once the money had left —
 * a read timeout, an optimistic-lock clash, a failure in a later step — erased the only evidence
 * the refund happened, and the double-refund guard then read zero and allowed it to be sent again.
 *
 * <p>Writing the claim first inverts which way the system can be wrong. The worst case becomes a
 * refund recorded that never happened, which blocks a retry and surfaces as a support ticket. The
 * case it replaces was a refund that happened and was invisible, which pays a customer twice and
 * nothing detects.
 */
@Service
public class RefundClaimStore {

    private static final Logger log = LoggerFactory.getLogger(RefundClaimStore.class);

    /**
     * Everything the gateway call needs, resolved while a transaction is still open.
     *
     * <p>Carries plain values rather than the entities: {@code PaymentTransaction.methodConfig} and
     * {@code PaymentRefund.transaction} are both LAZY, and the whole point of this type is to be
     * used after the transaction has closed.
     */
    public record RefundClaim(UUID refundId, UUID transactionId, String secretKey, String baseUrl,
                              String paymobTransactionId, long refundCents) {
    }

    private final PaymentRefundRepository refundRepo;
    private final PaymentTransactionRepository transactionRepo;

    public RefundClaimStore(PaymentRefundRepository refundRepo,
                            PaymentTransactionRepository transactionRepo) {
        this.refundRepo = refundRepo;
        this.transactionRepo = transactionRepo;
    }

    /**
     * Locks the transaction, re-checks the refundable balance, and records the claim — all in ONE
     * transaction, which then commits.
     *
     * <p>The single transaction is load-bearing and was the bug. The lock and the claim used to sit
     * in different transactions: {@code initiateRefund} held {@code SELECT … FOR UPDATE} on the
     * payment_transactions row while calling a REQUIRES_NEW claim on a second connection. Inserting
     * a payment_refunds row makes PostgreSQL take FOR KEY SHARE on the parent it references, and
     * FOR KEY SHARE conflicts with FOR UPDATE — so the inner transaction blocked on a lock the
     * outer held, while the outer sat waiting for the inner to return. Postgres could not call it a
     * deadlock (the outer was idle in transaction, not waiting on a lock), so nothing broke the
     * tie: every refund, and every cancellation of a paid order, hung until something timed out.
     * A transaction never blocks on its own locks, so doing both here cannot reproduce it.
     *
     * <p>The lock still does its real job — two admins refunding the same transaction at once
     * serialise here, and the second one's guard sees the first one's committed claim.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundClaim lockCheckAndClaim(RefundRequest req) {
        PaymentTransaction tx = transactionRepo.findWithLockById(req.getTransactionId())
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + req.getTransactionId()));

        if (tx.getStatus() != PaymentStatus.SUCCESS && tx.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Refunds only allowed for SUCCESS or PARTIALLY_REFUNDED");
        }

        // Counts PENDING as well as SUCCESS: a PENDING row is a refund whose outcome we never
        // learned, which is money that may already have left. Treating it as "nothing happened" is
        // what lets a retry send it a second time.
        BigDecimal alreadyRefunded = refundRepo.sumRefundedOrInFlight(tx);
        if (alreadyRefunded.add(req.getAmount()).compareTo(tx.getAmount()) > 0) {
            throw new IllegalArgumentException("Refund exceeds remaining amount");
        }

        long refundCents = req.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        PaymentRefund refund = new PaymentRefund();
        refund.setTransaction(tx);
        refund.setAmount(req.getAmount());
        refund.setAmountCents(refundCents);
        refund.setCurrency(tx.getCurrency());
        refund.setReason(req.getReason());
        refund.setStatus(RefundStatus.PENDING);
        refund.setRefundedBy(SecurityUtils.currentUserIdOrNull() != null
                ? SecurityUtils.currentUserIdOrNull()
                : req.getRefundedBy());
        refund = refundRepo.save(refund);

        var provider = tx.getMethodConfig().getProvider();
        return new RefundClaim(refund.getId(), tx.getId(), provider.getSecretKey(), provider.getBaseUrl(),
                tx.getPaymobTransactionId().toString(), refundCents);
    }

    /** Marks a claim paid once the gateway has confirmed it, and moves the transaction with it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRefund settleClaim(UUID refundId, String providerRefundId) {
        PaymentRefund refund = refundRepo.findById(refundId)
                .orElseThrow(() -> new NoSuchElementException("Refund not found: " + refundId));
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setProviderRefundId(providerRefundId);
        refund = refundRepo.save(refund);

        PaymentTransaction tx = transactionRepo.findWithLockById(refund.getTransaction().getId())
                .orElseThrow(() -> new NoSuchElementException("Transaction not found"));
        BigDecimal totalRefunded = refundRepo.sumRefundedOrInFlight(tx);
        tx.setStatus(totalRefunded.compareTo(tx.getAmount()) >= 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED);
        transactionRepo.save(tx);
        return refund;
    }

    /**
     * Resolves a claim after the gateway rejected the refund, or failed to answer at all.
     *
     * <p>Only a definite refusal — a 4xx, meaning the gateway understood and declined — releases
     * the claim. A timeout or a connection failure leaves the row PENDING, where the guard counts
     * it and a retry is refused, because an unanswered refund is one that may well have executed.
     * A blocked refund is a conversation; a duplicated one is money gone with nothing to notice it.
     *
     * <p>The refusal has to be recognised through the wrapper. {@code PaymobClient} converts every
     * failure — a 4xx, a 5xx and a socket timeout alike — into {@code PaymentGatewayException}, so
     * testing the thrown exception itself never matched and a declined refund stayed PENDING
     * forever, permanently blocking that transaction from ever being refunded. Only a 4xx cause
     * counts: a 5xx means the gateway broke while handling the request and may still have moved
     * the money.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseOrFailClaim(UUID refundId, RuntimeException cause) {
        boolean definiteRefusal = isDefiniteRefusal(cause);
        refundRepo.findById(refundId).ifPresent(r -> {
            if (definiteRefusal) {
                r.setStatus(RefundStatus.FAILED);
                refundRepo.save(r);
                log.warn("[PAYMENT] Refund {} was refused by the gateway; claim released.", refundId);
            } else {
                log.error("[PAYMENT] Refund {} got no usable answer from the gateway. Leaving it "
                        + "PENDING so a retry cannot send it twice — reconcile against the gateway "
                        + "by hand before releasing it.", refundId, cause);
            }
        });
    }

    /** Walks the cause chain, because the gateway client wraps what it throws. */
    static boolean isDefiniteRefusal(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof HttpClientErrorException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
