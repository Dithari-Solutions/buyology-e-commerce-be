package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The one place that decides what delivery costs.
 *
 * <p>It used to be two places — a pair of constants in {@code OrderService} and another pair in
 * {@code CartService}, kept aligned by a comment saying "same source-of-truth as CartService". That
 * held only while every method charged the same flat 15 AED. Now that 30-minute delivery and
 * standard delivery are priced differently, a drift between the two would mean the cart quotes one
 * fee and the order charges another, which the customer sees as a total changing at payment.
 *
 * <p>The policy:
 * <ul>
 *   <li>subtotal at or above {@code free-shipping-threshold-aed} (100) — free, whatever the method,</li>
 *   <li>below it, {@link DeliveryMethod#EXPRESS} (the 30-minute service, delivered by our own
 *       couriers) charges {@code express-fee-aed} (20),</li>
 *   <li>below it, {@link DeliveryMethod#REGULAR} charges {@code quiqup-fee-aed} — the rate we are
 *       billed, so the cost is passed through rather than absorbed — but only in the countries Quiqup
 *       actually serve (see {@link QuiqupCoverage}); a standard order anywhere else keeps the flat
 *       rate, because Quiqup are not carrying it,</li>
 *   <li>anything else (PICKUP, INTERNATIONAL) keeps the previous flat rate, deliberately unchanged.</li>
 * </ul>
 *
 * <p>Every rate is configurable, because a courier's rate card changes without our release cycle.
 * {@code quiqup-fee-aed} defaults to the old 15 AED rather than a guess at Quiqup's price: until it
 * is set deliberately, standard delivery is billed exactly as it is today. Quiqup expose no quote
 * endpoint (their documented paths cover create/get/ready/cancel/label/parcels and nothing for
 * pricing), so this is a contracted rate, not a per-order quotation. If they add quoting, this class
 * is where the call belongs.
 *
 * <p>All amounts here are AED, the settlement currency. Callers convert for display.
 */
@Component
public class DeliveryFeePolicy {

    private final BigDecimal freeShippingThresholdAed;
    private final BigDecimal expressFeeAed;
    private final BigDecimal quiqupFeeAed;
    private final BigDecimal standardFeeAed;
    private final QuiqupCoverage quiqupCoverage;

    public DeliveryFeePolicy(
            @Value("${delivery.free-shipping-threshold-aed:100.00}") BigDecimal freeShippingThresholdAed,
            @Value("${delivery.express-fee-aed:20.00}") BigDecimal expressFeeAed,
            @Value("${delivery.quiqup-fee-aed:15.00}") BigDecimal quiqupFeeAed,
            @Value("${delivery.standard-fee-aed:15.00}") BigDecimal standardFeeAed,
            QuiqupCoverage quiqupCoverage) {
        this.freeShippingThresholdAed = freeShippingThresholdAed;
        this.expressFeeAed = expressFeeAed;
        this.quiqupFeeAed = quiqupFeeAed;
        this.standardFeeAed = standardFeeAed;
        this.quiqupCoverage = quiqupCoverage;
    }

    /** The free-delivery threshold in AED, for display next to a "spend X more" nudge. */
    public BigDecimal freeShippingThresholdAed() {
        return freeShippingThresholdAed;
    }

    /** Whether this subtotal (already in AED) earns free delivery. */
    public boolean qualifiesForFreeDelivery(BigDecimal subtotalAed) {
        return subtotalAed != null && subtotalAed.compareTo(freeShippingThresholdAed) >= 0;
    }

    /**
     * The delivery fee in AED for a method, a delivery country and an AED subtotal.
     *
     * <p>The country matters because Quiqup's rate may only be charged where Quiqup actually deliver.
     * A standard order to a market they do not serve keeps the flat rate — it is carried by whatever
     * arrangement covers that country, so billing it their Dubai price would be wrong.
     *
     * @param method       null is treated as standard, so a caller that has not resolved the method
     *                     cannot accidentally get the free-delivery answer
     * @param countryCode  the delivery address country, alpha-2 or alpha-3
     * @param subtotalAed  the order/cart subtotal converted to AED
     */
    public BigDecimal feeAed(DeliveryMethod method, String countryCode, BigDecimal subtotalAed) {
        if (qualifiesForFreeDelivery(subtotalAed)) {
            return BigDecimal.ZERO;
        }
        if (method == DeliveryMethod.EXPRESS) {
            return expressFeeAed;
        }
        if (quiqupCoverage.covers(method, countryCode)) {
            return quiqupFeeAed;
        }
        return standardFeeAed;
    }

    /**
     * The fee the cart advertises before a delivery address — and therefore a method — is known.
     *
     * <p>Standard delivery, because that is what most orders are: only an address inside a store's
     * 30-minute radius resolves to EXPRESS. An order that does qualify is recalculated at checkout,
     * where the customer sees the final total before paying.
     */
    public BigDecimal cartPreviewFeeAed(String countryCode, BigDecimal subtotalAed) {
        return feeAed(DeliveryMethod.REGULAR, countryCode, subtotalAed);
    }
}
