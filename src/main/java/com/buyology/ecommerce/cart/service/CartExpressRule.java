package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.cart.dto.CartItemResponse;

import java.util.List;

/**
 * Whether a cart can be delivered in 30 minutes — the CART's transcription of the order rule.
 *
 * <p>The authority is OrderService.resolveDeliveryMethod: EXPRESS only when EVERY item's store sits
 * inside the radius, else a silent downgrade to REGULAR. The cart used to say anyMatch — one nearby
 * item made the whole cart read as express-capable — so the customer was quoted the 20 AED express
 * fee and then charged a downgraded regular delivery. anyMatch and allMatch differ by exactly one
 * word and by real money.
 */
public final class CartExpressRule {

    private CartExpressRule() {
    }

    /**
     * @param items the SELECTED lines only — an unticked out-of-radius item is not part of the
     *              order and must not block express for a cart that qualifies. The caller filters,
     *              because after cart-selection createOrder's own list is selected-only too.
     */
    public static boolean expressAvailable(List<CartItemResponse> items) {
        // The non-empty guard is mandatory: Stream.allMatch on an empty stream is vacuously true,
        // and an empty cart must not advertise express.
        return !items.isEmpty() && items.stream().allMatch(CartItemResponse::isQuickDelivery);
    }
}
