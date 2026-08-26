package com.buyology.ecommerce.payment.dto;

import java.time.Instant;
import java.util.List;

/**
 * Why a payment did not complete, and where the customer stopped.
 *
 * <p>An order awaiting payment carries a status, which is a fact rather than an explanation: the
 * same PENDING_PAYMENT covers our checkout failing to reach the gateway, a shopper closing the tab,
 * a bank declining the card, and an instalment provider that has already approved the customer.
 * Those need four different responses and two of them need no customer contact at all, so the
 * status alone is not something an admin can act on.
 *
 * @param code               stable machine key for the stall reason
 * @param stage              where the customer stopped, in plain words
 * @param summary            one line an admin can act on
 * @param detail             what the gateway itself said, when it said anything
 * @param suggestedMessage   a starting point for the admin's message — never sent on its own
 * @param customerHasPaid    the money is already taken; chasing this customer would be wrong
 * @param contactRecommended whether reaching out would actually help
 * @param attemptCount       how many payment attempts this order has seen
 * @param methodsTried       the payment methods tried, in order
 * @param lastAttemptAt      when the most recent attempt happened
 */
public record PaymentStallDiagnosis(
        String code,
        String stage,
        String summary,
        String detail,
        String suggestedMessage,
        boolean customerHasPaid,
        boolean contactRecommended,
        int attemptCount,
        List<String> methodsTried,
        Instant lastAttemptAt
) {}
