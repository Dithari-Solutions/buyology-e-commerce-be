package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.dto.PaymentStallDiagnosis;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a payment's raw state into the reason it did not complete.
 *
 * <p>The inputs are already there — a payment leaves a distinct mark at each stage it passes. An
 * intention id means we reached the gateway; a Paymob transaction id means the customer actually
 * attempted payment on the page; a decline code means their bank answered. Reading those in order
 * says where they stopped, which is the thing an admin needs and the status alone never says.
 *
 * <p>Two rules shape everything here. Decline codes are matched, never decline messages: the
 * message is free English the gateway can reword at any time, and a customer-facing explanation
 * built on it would break silently. And an instalment payment awaiting settlement is reported as
 * <em>paid</em>, because it is — Tabby and Tamara approve the shopper before Paymob confirms, and
 * telling that customer to complete a payment they have already made is the worst message this
 * system could send.
 */
@Component
public class PaymentStallDiagnoser {

    /**
     * Bank decline codes (ISO 8583, which Paymob passes through from the acquirer) mapped to what
     * they mean for this customer. The second string is written to be read by the shopper: it says
     * what to do next and never blames them for their bank's answer.
     */
    private static final Map<String, String[]> DECLINE_CODES = Map.ofEntries(
            Map.entry("01", new String[]{"The bank asked the customer to contact them",
                    "Your bank needs to speak with you before approving this payment. A quick call to them, or a different card, will get it through."}),
            Map.entry("02", new String[]{"The bank asked the customer to contact them",
                    "Your bank needs to speak with you before approving this payment. A quick call to them, or a different card, will get it through."}),
            Map.entry("04", new String[]{"Card blocked by the issuing bank",
                    "Your bank has placed a hold on this card. Please try a different card, or contact them to lift it."}),
            Map.entry("05", new String[]{"Declined by the bank without a stated reason",
                    "Your bank declined the payment without telling us why — this is usually a security check on an online purchase. Trying again, using a different card, or approving it in your banking app normally works."}),
            Map.entry("12", new String[]{"The bank rejected the transaction as invalid",
                    "Something about the payment didn't reach your bank correctly. Please try again, and use a different card if it happens twice."}),
            Map.entry("13", new String[]{"The bank rejected the amount",
                    "Your bank rejected the amount of this payment. Please try a different card."}),
            Map.entry("14", new String[]{"Card number not recognised by the issuer",
                    "The card number wasn't recognised. Please check the digits and try again."}),
            Map.entry("15", new String[]{"No bank found for that card number",
                    "The card number wasn't recognised. Please check the digits and try again."}),
            Map.entry("33", new String[]{"Card expired",
                    "That card has expired. Please pay with a current card."}),
            Map.entry("41", new String[]{"Card reported lost",
                    "Your bank has blocked this card. Please use a different one."}),
            Map.entry("43", new String[]{"Card reported stolen",
                    "Your bank has blocked this card. Please use a different one."}),
            Map.entry("51", new String[]{"Insufficient funds",
                    "There weren't enough available funds on the card at that moment. Please try again, or use a different card."}),
            Map.entry("54", new String[]{"Card expired",
                    "That card has expired. Please pay with a current card."}),
            Map.entry("55", new String[]{"Incorrect PIN entered",
                    "The PIN didn't match. Please try again."}),
            Map.entry("57", new String[]{"Card not permitted for this type of purchase",
                    "This card isn't enabled for online purchases. Your bank can switch that on, or you can use a different card."}),
            Map.entry("61", new String[]{"Payment exceeds the card's limit",
                    "This payment is above the card's limit. Your bank can raise it, or you can use a different card."}),
            Map.entry("62", new String[]{"Restricted card",
                    "Your bank has restricted this card for online purchases. Please use a different one."}),
            Map.entry("63", new String[]{"Blocked by the bank's security check",
                    "Your bank's security check blocked the payment. Please approve it in your banking app, or use a different card."}),
            Map.entry("65", new String[]{"Too many attempts on this card today",
                    "Your bank has limited how many times this card can be used today. Please try again tomorrow, or use a different card."}),
            Map.entry("75", new String[]{"Too many incorrect PIN attempts",
                    "Too many incorrect PIN attempts. Please try again later or use a different card."}),
            Map.entry("91", new String[]{"The customer's bank was unreachable",
                    "We couldn't reach your bank at that moment — this is temporary and not a problem with your card. Please try again."}),
            Map.entry("96", new String[]{"The bank's system reported a fault",
                    "Your bank's system reported a fault. Please try again in a few minutes."})
    );

    public PaymentStallDiagnosis diagnose(List<PaymentTransaction> transactions) {
        List<PaymentTransaction> all = transactions == null ? List.of() : transactions;

        List<PaymentTransaction> newestFirst = all.stream()
                .sorted(Comparator.comparing(
                        (PaymentTransaction t) -> t.getCreatedAt() == null ? Instant.EPOCH : t.getCreatedAt())
                        .reversed())
                .toList();

        Set<String> methods = new LinkedHashSet<>();
        for (PaymentTransaction t : newestFirst) {
            if (t.getMethodType() != null) methods.add(t.getMethodType().name());
        }
        List<String> methodsTried = new ArrayList<>(methods);
        Instant lastAttemptAt = newestFirst.isEmpty() ? null : newestFirst.get(0).getCreatedAt();
        int attempts = newestFirst.size();

        // Settled. Nothing to explain and nobody to chase.
        if (newestFirst.stream().anyMatch(t -> t.getStatus() == PaymentStatus.SUCCESS)) {
            return new PaymentStallDiagnosis("PAID", "Payment complete",
                    "This order is paid.", null, null,
                    true, false, attempts, methodsTried, lastAttemptAt);
        }

        // No attempt was ever recorded — the order exists but checkout never created a payment.
        if (newestFirst.isEmpty()) {
            return new PaymentStallDiagnosis("NEVER_STARTED", "Checkout",
                    "No payment was ever started for this order.",
                    null,
                    "We're holding your order, but we haven't received a payment for it yet. "
                            + "You can complete it from your account whenever you're ready.",
                    false, true, 0, methodsTried, null);
        }

        PaymentTransaction latest = newestFirst.get(0);
        PaymentMethodType method = latest.getMethodType();
        boolean instalment = method == PaymentMethodType.TABBY || method == PaymentMethodType.TAMARA;
        String methodLabel = method == null ? "the payment provider" : switch (method) {
            case TABBY -> "Tabby";
            case TAMARA -> "Tamara";
            case CARD -> "card";
            case B2B_CREDIT -> "B2B credit";
        };

        // The money is already taken; the gateway just hasn't confirmed settlement to us. Reported
        // as paid so no outreach path can mistake it for an unpaid order.
        if (latest.getStatus() == PaymentStatus.PROCESSING && instalment) {
            return new PaymentStallDiagnosis("INSTALMENT_AWAITING_SETTLEMENT",
                    methodLabel + " approval",
                    "The customer was approved by " + methodLabel + " and has paid. "
                            + methodLabel + " has not confirmed settlement to us yet — this "
                            + "normally clears on its own within minutes.",
                    latest.getFailureReason(), null,
                    true, false, attempts, methodsTried, lastAttemptAt);
        }

        // Card 3-D Secure: the bank asked for a one-time code and never got it.
        if (latest.getStatus() == PaymentStatus.PROCESSING) {
            return new PaymentStallDiagnosis("VERIFICATION_NOT_COMPLETED", "Bank verification",
                    "The customer reached their bank's verification step and did not complete it. "
                            + "No money was taken.",
                    latest.getFailureReason(),
                    "It looks like the verification step with your bank wasn't finished, so the "
                            + "payment didn't go through and nothing was charged. You can pick up "
                            + "where you left off from your account — have your phone nearby for "
                            + "the code your bank sends.",
                    false, true, attempts, methodsTried, lastAttemptAt);
        }

        if (latest.getStatus() == PaymentStatus.FAILED) {
            long declines = newestFirst.stream().filter(t -> t.getStatus() == PaymentStatus.FAILED).count();
            String[] mapped = lookupDecline(latest.getFailureCode());
            String adminReason = mapped != null ? mapped[0]
                    : (latest.getFailureReason() != null && !latest.getFailureReason().isBlank()
                            ? latest.getFailureReason() : "Declined without a reported reason");
            String customerReason = mapped != null ? mapped[1]
                    : "The payment was declined. Trying again, or using a different card, usually works.";

            // Several declines across more than one method is no longer something a better-worded
            // email fixes — it needs a person.
            if (declines >= 2 && methodsTried.size() >= 2) {
                return new PaymentStallDiagnosis("REPEATEDLY_DECLINED", "Payment declined",
                        "Declined " + declines + " times across " + String.join(", ", methodsTried)
                                + ". Most recent: " + adminReason + ". This needs a person.",
                        latest.getFailureReason(),
                        "We can see a few payment attempts on your order didn't go through, and we'd "
                                + "rather sort it out with you than let you keep trying. Reply to "
                                + "this message and we'll help you complete it.",
                        false, true, attempts, methodsTried, lastAttemptAt);
            }

            return new PaymentStallDiagnosis("DECLINED", "Payment declined",
                    "Declined by the bank: " + adminReason
                            + (latest.getFailureCode() == null ? "" : " (code " + latest.getFailureCode() + ")"),
                    latest.getFailureReason(),
                    customerReason + " Nothing was charged — you can complete your order from your account.",
                    false, true, attempts, methodsTried, lastAttemptAt);
        }

        // PENDING. How far it got is the difference between our problem and an abandoned checkout.
        if (latest.getIntentionId() == null) {
            return new PaymentStallDiagnosis("GATEWAY_UNREACHABLE", "Our checkout",
                    "We never reached the payment provider, so the customer was never shown a "
                            + "payment page. This is our side, not theirs.",
                    latest.getFailureReason(),
                    "We hit a problem on our side opening the payment page for your order, and "
                            + "your order is still waiting. We're sorry about that — you can "
                            + "complete it from your account now.",
                    false, true, attempts, methodsTried, lastAttemptAt);
        }

        return new PaymentStallDiagnosis("ABANDONED_AT_GATEWAY", methodLabel + " payment page",
                "The payment page opened and the customer left without completing payment. "
                        + "Nothing was charged.",
                null,
                "Your order is saved but the payment wasn't finished, so nothing has been charged "
                        + "yet. You can pick it up from where you left off in your account.",
                false, true, attempts, methodsTried, lastAttemptAt);
    }

    /** Codes arrive zero-padded, unpadded, or wrapped in prose depending on the acquirer. */
    private static String[] lookupDecline(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) return null;
        String code = rawCode.trim();
        String[] hit = DECLINE_CODES.get(code);
        if (hit != null) return hit;
        // "5" for "05", "051" never — only pad what is plausibly a two-digit code.
        if (code.length() == 1 && Character.isDigit(code.charAt(0))) {
            return DECLINE_CODES.get("0" + code);
        }
        if (code.length() == 3 && code.startsWith("0")) {
            return DECLINE_CODES.get(code.substring(1));
        }
        return null;
    }
}
