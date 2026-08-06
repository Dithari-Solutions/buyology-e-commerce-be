package com.buyology.ecommerce.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal request to charge a standalone courier fee via Paymob (refund return pickup, a repair
 * device pickup/return, OR a sell-request device pickup/return). Built by the refund/repair/sell
 * modules (not bound from HTTP), so it carries only what the Paymob intention needs — there is no
 * cart, order or address.
 *
 * Exactly one of {@code refundRequestId} / {@code repairId} / {@code sellRequestId} is set; that
 * determines the transaction {@link com.buyology.ecommerce.payment.enums.PaymentPurpose} and which
 * module's paid-event the webhook publishes.
 *
 * @param refundRequestId the refund request this fee belongs to (null otherwise)
 * @param repairId        the repair request this fee belongs to (null otherwise)
 * @param sellRequestId   the sell request this fee belongs to (null otherwise)
 * @param customerId      auth_credentials.id of the payer (for payment-readiness + ownership)
 * @param amount          fee amount in {@code currency}
 * @param currency        the customer's display currency (3-letter ISO)
 * @param customerEmail   receipt email
 * @param customerPhone   optional contact phone
 * @param billingName     "First Last" used for the Paymob billing/customer block
 * @param redirectionUrl  where Paymob returns the browser after checkout
 */
public record CourierFeeChargeRequest(
        UUID refundRequestId,
        UUID repairId,
        UUID sellRequestId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        String customerEmail,
        String customerPhone,
        String billingName,
        String redirectionUrl) {
}
