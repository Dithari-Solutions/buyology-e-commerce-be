package com.buyology.ecommerce.product.domain;

import java.util.UUID;

/**
 * A customer asked for more units of a product than we hold.
 *
 * <p>Its own type rather than {@code IllegalStateException} for two reasons.
 *
 * <p>The message reaches the customer. {@code GlobalExceptionHandler} renders an
 * {@code IllegalStateException} as a 409 carrying {@code ex.getMessage()} verbatim, and the existing
 * stock refusals phrase that as {@code "Insufficient stock for product 3f2a1b4c-… variant 9d8e7f…
 * (requested 2)"} — internal identifiers, shown to a shopper, in a body a client is expected to
 * display. The wording here is what the cart already says when it refuses a quantity
 * ({@code "Only 2 left in stock."}), so a customer meets the same sentence wherever they hit the
 * ceiling, and the identifiers stay in the log where they are actually useful.
 *
 * <p>And it is distinguishable. A refused sale is an ordinary, expected outcome of two people wanting
 * the same last unit; an {@code IllegalStateException} is not. Keeping them apart means a client can
 * react to this specifically — "someone bought the last one, here is your basket" rather than the
 * generic "something went wrong, please try again" that a shopper will obligingly retry forever.
 */
public class InsufficientStockException extends RuntimeException {

    private final UUID productId;
    private final String productSku;
    private final int requested;
    private final int available;

    public InsufficientStockException(UUID productId, String productSku, int requested, Integer available) {
        super(customerMessage(available));
        this.productId = productId;
        this.productSku = productSku;
        this.requested = requested;
        // The count read from the entity is what we loaded, which is not necessarily what the
        // conditional UPDATE found a moment later — that is the whole reason the UPDATE decides and
        // this is only a message. Null (untracked) cannot reach here, but is not worth crashing over.
        this.available = available == null ? 0 : Math.max(0, available);
    }

    /**
     * What the shopper reads.
     *
     * <p>Names the number, because "out of stock" about a line sitting in their basket reads as a bug
     * rather than as news. Matches {@code CartService.outOfStockMessage} word for word on purpose: the
     * cart's check is a courtesy that happens early and this one is the binding refusal, and a
     * customer should not be able to tell which of the two spoke to them.
     */
    private static String customerMessage(Integer available) {
        int left = available == null ? 0 : available;
        if (left <= 0) {
            return "This item is now out of stock.";
        }
        return left == 1
                ? "Only 1 left in stock."
                : "Only " + left + " left in stock.";
    }

    /** For the log line, not for the response body. */
    public String describeForLog() {
        return "product=" + productId + " sku=" + productSku
                + " requested=" + requested + " available=" + available;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductSku() {
        return productSku;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
