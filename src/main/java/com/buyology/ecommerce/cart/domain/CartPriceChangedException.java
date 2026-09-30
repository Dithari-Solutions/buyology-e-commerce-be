package com.buyology.ecommerce.cart.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The basket got DEARER between being shown and the order being placed.
 *
 * <p>Only that direction reaches here. A basket that got cheaper is simply charged at the lower price
 * — a 409 that says "you are paying less" is a customer who cannot buy anything, which is worse than
 * the overcharge the refusal exists to prevent. See {@code CheckoutRepricing}.
 *
 * <p>Its own type for the same two reasons as {@code InsufficientStockException}, which it is modelled
 * on: the message reaches the customer, so it must read like a sentence rather than like a pair of
 * UUIDs; and a client needs to react to THIS specifically — reload the basket and show the new total —
 * rather than to the generic "something went wrong" a shopper will obligingly retry with the same
 * stale price.
 *
 * <p>It is not an error in the system. It is a sale ending in the seconds between two requests, which
 * is the entire point of a flash sale, so it is logged at INFO and answered with 409.
 *
 * <p><b>Only ever thrown BEFORE the money is captured</b>, and the message below depends on it: "you
 * have not been charged" is a promise, and on the cart-first flow — where the gateway settles first and
 * the order is built from the cart afterwards — it would be a lie. Worse than a lie: thrown there it
 * rolled the listener's transaction back and left a captured payment with no order at all. That path
 * records the difference as an anomaly instead of refusing; see {@code OrderService.CapturePhase}.
 */
public class CartPriceChangedException extends RuntimeException {

    /**
     * What the shopper reads, and — until both apps are updated to recognise the 409 — all they will
     * see, because the storefront and the app both surface the server's message verbatim.
     *
     * <p>So it has to stand alone: what happened, that they have not been charged, and what to do
     * next. No amounts and no SKU, because the basket is one reload away and will show both in the
     * customer's own currency, correctly formatted, which this class cannot do. And no mention of a
     * "flash sale" — a price can also rise because an admin edited it, and a customer told the wrong
     * reason stops believing the right one.
     */
    private static final String CUSTOMER_MESSAGE =
            "Prices in your basket have gone up since it was last shown to you, so your order was not "
                    + "placed and you have not been charged. This usually means a sale has ended. Please open "
                    + "your basket to see the new prices, then place your order again.";

    private final UUID cartItemId;
    private final String productSku;
    private final BigDecimal stampedTotal;
    private final BigDecimal liveTotal;

    /**
     * @param cartItemId the line that rose the most, for the log — not necessarily the only one
     * @param stampedTotal the basket total the customer was shown
     * @param liveTotal what the same basket costs now
     */
    public CartPriceChangedException(UUID cartItemId, String productSku,
                                     BigDecimal stampedTotal, BigDecimal liveTotal) {
        super(CUSTOMER_MESSAGE);
        this.cartItemId = cartItemId;
        this.productSku = productSku;
        this.stampedTotal = stampedTotal;
        this.liveTotal = liveTotal;
    }

    /** For the log line, not for the response body. */
    public String describeForLog() {
        return "basket " + stampedTotal + " -> " + liveTotal
                + " (largest rise on cartItem=" + cartItemId + " sku=" + productSku + ")";
    }

    public UUID getCartItemId() {
        return cartItemId;
    }

    public String getProductSku() {
        return productSku;
    }

    public BigDecimal getStampedTotal() {
        return stampedTotal;
    }

    public BigDecimal getLiveTotal() {
        return liveTotal;
    }
}
