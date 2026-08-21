package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.payment.domain.PaymentRefund;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.dto.RefundRequest;
import com.buyology.ecommerce.payment.enums.RefundStatus;
import com.buyology.ecommerce.payment.repository.PaymentRefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

/**
 * Records that a refund is in flight, in a transaction of its own.
 *
 * <p>A separate bean rather than methods on {@code PaymentService}, because Spring applies
 * {@code @Transactional} through a proxy: a plain {@code this.claimRefund(...)} would silently join
 * the caller's transaction and roll back with it, which is the exact failure this exists to
 * prevent. Calling it across a bean boundary is what makes REQUIRES_NEW real.
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

    private final PaymentRefundRepository refundRepo;

    public RefundClaimStore(PaymentRefundRepository refundRepo) {
        this.refundRepo = refundRepo;
    }

    /** Writes the refund as PENDING and commits it, independently of the caller. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRefund claimRefund(PaymentTransaction tx, RefundRequest req, long refundCents) {
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
        return refundRepo.save(refund);
    }

    /**
     * Resolves a claim after the gateway rejected the refund, or failed to answer at all.
     *
     * <p>Only a definite refusal — a 4xx, meaning the gateway understood and declined — releases
     * the claim. A timeout or a connection failure leaves the row PENDING, where the guard counts
     * it and a retry is refused, because an unanswered refund is one that may well have executed.
     * A blocked refund is a conversation; a duplicated one is money gone with nothing to notice it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseOrFailClaim(UUID refundId, RuntimeException cause) {
        boolean definiteRefusal = cause instanceof HttpClientErrorException;
        refundRepo.findById(refundId).ifPresent(r -> {
            if (definiteRefusal) {
                r.setStatus(RefundStatus.FAILED);
                refundRepo.save(r);
                log.warn("[PAYMENT] Refund {} was refused by the gateway; claim released.", refundId);
            } else {
                log.error("[PAYMENT] Refund {} got no answer from the gateway. Leaving it PENDING so "
                        + "a retry cannot send it twice — reconcile against the gateway by hand "
                        + "before releasing it.", refundId, cause);
            }
        });
    }
}
