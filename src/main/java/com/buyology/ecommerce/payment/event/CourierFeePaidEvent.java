package com.buyology.ecommerce.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when a standalone courier return-pickup fee charge succeeds (Paymob
 * webhook/redirect confirmation). The refund module listens for this to advance the
 * matching request from COURIER_FEE_PENDING to COURIER_REQUESTED.
 *
 * @param refundRequestId the refund request the fee belongs to
 * @param transactionId   the successful payment transaction
 * @param amount          charged amount (settlement currency)
 * @param currency        settlement currency (AED)
 */
public record CourierFeePaidEvent(
        UUID refundRequestId,
        UUID transactionId,
        BigDecimal amount,
        String currency) {
}
