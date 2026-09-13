package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The one place that decides what delivery costs.
 *
 * <p>It used to be two places — a pair of constants in {@code OrderService} and another pair in
 * {@code CartService}, kept aligned by a comment saying "same source-of-truth as CartService". A drift
 * between them means the cart quotes one fee and the order charges another, which the customer sees as
 * the total changing at payment.
 *
 * <p>The policy:
 * <ul>
 *   <li>subtotal at or above {@code free-shipping-threshold-aed} (100) — free, whatever the method;</li>
 *   <li>{@link DeliveryMethod#PICKUP} — free, because collecting it yourself is not a delivery;</li>
 *   <li>otherwise a flat {@code flat-fee-aed} (25), the same for every method and every market.</li>
 * </ul>
 *
 * <p>One rate, deliberately. This used to be tiered — 20 for the 30-minute service, a pass-through
 * Quiqup rate for standard deliveries in the countries they serve, a separate flat rate everywhere
 * else — and each of those distinctions was a way for the quote and the charge to disagree. A single
 * number cannot. The old keys are kept in configuration but no longer read (see the fields below), so
 * reverting to tiered pricing is a change to this one class.
 *
 * <p>The free-delivery threshold is checked FIRST and is untouched by any of this: an order at or above
 * it still ships free, whatever method it resolves to. Getting that order of operations wrong — a flat
 * fee returned before the threshold check — would start charging 25 AED on every large order, which is
 * the one thing this change must not do.
 *
 * <p>The rate is configurable because a courier's rate card changes without our release cycle. Quiqup
 * expose no quote endpoint (their documented paths cover create/get/ready/cancel/label/parcels and
 * nothing for pricing), so this is a contracted rate, not a per-order quotation. If they add quoting,
 * this class is where the call belongs.
 *
 * <p>All amounts here are AED, the settlement currency. Callers convert for display.
 */
@Component
public class DeliveryFeePolicy {

    private final BigDecimal freeShippingThresholdAed;
    private final BigDecimal flatFeeAed;
    private final QuiqupCoverage quiqupCoverage;

    /**
     * @param quiqupCoverage no longer consulted for PRICING — the fee is flat — but still injected
     *                       because it remains the gate for whether an order is Quiqup-dispatchable
     *                       ({@code QuiqupDispatchService}), and keeping it here documents that the
     *                       bean is load-bearing elsewhere rather than dead.
     */
    public DeliveryFeePolicy(
            @Value("${delivery.free-shipping-threshold-aed:100.00}") BigDecimal freeShippingThresholdAed,
            @Value("${delivery.flat-fee-aed:25.00}") BigDecimal flatFeeAed,
            QuiqupCoverage quiqupCoverage) {
        this.freeShippingThresholdAed = freeShippingThresholdAed;
        this.flatFeeAed = flatFeeAed;
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
        // FIRST, always. A flat fee returned ahead of this check would charge 25 AED on every order
        // over the threshold and silently delete free delivery.
        if (qualifiesForFreeDelivery(subtotalAed)) {
            return BigDecimal.ZERO;
        }
        // Collecting it from a store is not a delivery, so there is nothing to charge for. Stated here
        // as well as in OrderService.resolveFulfilment, which hardcodes zero and never calls this:
        // when two places decide the same thing, they should at least agree.
        if (method == DeliveryMethod.PICKUP) {
            return BigDecimal.ZERO;
        }
        return flatFeeAed;
    }

    /**
     * The 30-minute delivery rate.
     *
     * <p>The same flat rate as everything else now — the fee no longer depends on the method. Kept as a
     * method so the response fields that quote it still compile and still answer honestly for any
     * client already in the field; 30-minute delivery is itself switched off (see
     * {@code delivery.express-enabled}).
     */
    public BigDecimal expressFeeAed() {
        return flatFeeAed;
    }

    /**
     * The 30-minute fee actually payable on this AED subtotal — ZERO above the free-shipping
     * threshold, exactly as {@link #feeAed} decides for EXPRESS. Exists so every surface that
     * quotes express (cart, product page, checkout) prices it through the same branch the order
     * charges, instead of re-deriving the threshold by hand.
     */
    public BigDecimal expressFeeAedForSubtotal(BigDecimal subtotalAed) {
        return feeAed(com.buyology.ecommerce.order.domain.enums.DeliveryMethod.EXPRESS, null, subtotalAed);
    }

    public BigDecimal cartPreviewFeeAed(String countryCode, BigDecimal subtotalAed) {
        return feeAed(DeliveryMethod.REGULAR, countryCode, subtotalAed);
    }
}
