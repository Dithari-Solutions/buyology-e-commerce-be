package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.membership.domain.CreditUsage;
import com.buyology.ecommerce.membership.repository.CreditUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Gives B2B wallet credit back when the order it paid for does not happen.
 *
 * <p>A B2B order settled with wallet credit leaves a {@link CreditUsage} row behind, and that row is
 * what payback chasing and membership gating read. Cancelling the order used to refund the card leg
 * and say nothing about the credit leg, so the member stayed on the hook for goods they never
 * received — on a 20,000 AED minimum order, the largest single-event loss in the system, and the
 * only one that falls on the customer rather than the business.
 *
 * <p>Its own bean, and its own transaction, for two reasons. The caller runs this <em>after</em> the
 * cancellation has committed, where there is no ambient transaction to join — and returning the
 * balance and marking the usage reversed must still land together, or a failure between them leaves
 * credit handed back against a row that will hand it back again.
 */
@Service
public class CreditReturnService {

    private static final Logger log = LoggerFactory.getLogger(CreditReturnService.class);

    private final CreditUsageRepository creditUsageRepository;
    private final WalletService walletService;

    public CreditReturnService(CreditUsageRepository creditUsageRepository, WalletService walletService) {
        this.creditUsageRepository = creditUsageRepository;
        this.walletService = walletService;
    }

    /**
     * Returns the credit an order consumed, if it consumed any.
     *
     * <p>Only the outstanding portion comes back — the amount minus anything the member has already
     * repaid. Restoring the full balance to someone who had already paid part of it back would hand
     * them the difference; cash they have already repaid is a refund question for a human, not
     * something to invent credit for here.
     *
     * <p>Idempotent on {@link CreditUsage.Status#REVERSED}, so a re-cancellation, a retry or a
     * duplicate webhook cannot credit twice.
     *
     * @param orderId       the cancelled order
     * @param creditApplied what the order recorded as paid from the wallet; null or zero is a no-op
     * @return true when credit was actually returned
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean returnForCancelledOrder(UUID orderId, BigDecimal creditApplied) {
        if (creditApplied == null || creditApplied.signum() <= 0) {
            return false;
        }
        CreditUsage usage = creditUsageRepository.findByOrderId(orderId).orElse(null);
        if (usage == null) {
            // The order says credit was applied but no usage row exists to reverse. Worth shouting
            // about: it means the two halves of the B2B payment disagree.
            log.error("[B2B] Order {} applied {} of credit but has no credit_usage row — the credit "
                    + "cannot be returned automatically", orderId, creditApplied);
            return false;
        }
        if (usage.getStatus() == CreditUsage.Status.REVERSED) {
            return false;
        }

        BigDecimal paid = usage.getPaidAmount() == null ? BigDecimal.ZERO : usage.getPaidAmount();
        BigDecimal stillOwed = usage.getAmount().subtract(paid).max(BigDecimal.ZERO);
        if (stillOwed.signum() > 0) {
            walletService.addCredit(usage.getUserId(), stillOwed,
                    "Credit returned — order " + orderId + " cancelled", "SYSTEM");
        }

        // REVERSED, deliberately not PAID. The member did not pay this, and every report that counts
        // repayments would otherwise count money that was never collected. It is also absent from
        // the OUTSTANDING/PARTIAL/OVERDUE sets that drive chasing and membership gating, which is
        // the whole point of returning it.
        usage.setStatus(CreditUsage.Status.REVERSED);
        creditUsageRepository.save(usage);

        log.info("[B2B] Returned {} {} of credit for cancelled order {} (usage {}, {} already repaid)",
                stillOwed, usage.getCurrency(), orderId, usage.getId(), paid);
        return stillOwed.signum() > 0;
    }
}
