package com.buyology.ecommerce.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when a standalone repair courier-fee charge succeeds (Paymob webhook/redirect
 * confirmation). The repair module listens for this to advance the matching request — either
 * confirming the inbound courier pickup (SUBMITTED → AWAITING_DEVICE) or the post-decline
 * courier return.
 *
 * @param repairId      the repair request the fee belongs to
 * @param transactionId the successful payment transaction
 * @param amount        charged amount (settlement currency)
 * @param currency      settlement currency (AED)
 */
public record RepairCourierFeePaidEvent(
        UUID repairId,
        UUID transactionId,
        BigDecimal amount,
        String currency) {
}
