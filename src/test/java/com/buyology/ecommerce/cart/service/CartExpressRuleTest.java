package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.cart.dto.CartItemResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the cart's transcription of the express rule to the order's.
 *
 * <p>The authority is OrderService.resolveDeliveryMethod: EXPRESS only when EVERY item's store is
 * inside the radius. The cart said anyMatch — a single nearby item made the whole cart read as
 * express-capable — and the difference is one word and real money: the customer was quoted the
 * 20 AED express fee for a delivery the backend then silently downgraded to regular.
 */
class CartExpressRuleTest {

    private static CartItemResponse item(boolean quick) {
        CartItemResponse r = new CartItemResponse();
        r.setQuickDelivery(quick);
        return r;
    }

    @Test
    void everyItemNearbyMeansExpress() {
        assertTrue(CartExpressRule.expressAvailable(List.of(item(true), item(true))));
    }

    @Test
    void oneFarItemKillsExpressForTheWholeCart() {
        // The order will downgrade this cart. The cart saying otherwise is the quoted-vs-charged
        // divergence this class exists to end.
        assertFalse(CartExpressRule.expressAvailable(List.of(item(true), item(false))));
    }

    @Test
    void noNearbyItemsMeansNoExpress() {
        assertFalse(CartExpressRule.expressAvailable(List.of(item(false), item(false))));
    }

    @Test
    void anEmptyCartNeverAdvertisesExpress() {
        // Stream.allMatch on an empty stream is vacuously TRUE — without the non-empty guard an
        // empty cart reads as express-capable, which is nonsense with a price on it.
        assertFalse(CartExpressRule.expressAvailable(List.of()));
    }
}
