package com.buyology.ecommerce.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal request to charge a standalone courier return-pickup fee via Paymob.
 * Built by the refund module (not bound from HTTP), so it carries only what the
 * Paymob intention needs — there is no cart, order or address.
 *
 * @param refundRequestId the refund request this fee belongs to
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
        UUID customerId,
        BigDecimal amount,
        String currency,
        String customerEmail,
        String customerPhone,
        String billingName,
        String redirectionUrl) {
}
