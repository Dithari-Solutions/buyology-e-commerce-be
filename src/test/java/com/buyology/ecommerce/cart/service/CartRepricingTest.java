package com.buyology.ecommerce.cart.service;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.StoreProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the cart-versus-checkout price decision: a basket is re-priced against the live sale, and a
 * checkout whose price has moved is refused rather than silently billed.
 *
 * <p>What this replaces: a cart line's unit price was written once at add-to-cart and never read
 * again. {@code buildCartItemResponse} returned the stored figure, quantity changes multiplied the
 * stored figure, and {@code OrderService.createOrder} copied it onto the order verbatim — so cart and
 * order could never disagree with each OTHER, and both could disagree with the catalogue forever.
 * Harmless while discounts were permanent; a revenue leak in one direction and a bait-and-switch in
 * the other the moment they expire.
 *
 * <p>These assertions are on the decision itself. If they fail, either the basket stops showing what
 * the customer will be charged, or checkout starts refusing orders nobody's price moved on.
 */
class CartRepricingTest {

    private static final Instant DURING_SALE = Instant.parse("2026-03-26T10:00:00Z");
    private static final Instant AFTER_SALE = Instant.parse("2026-04-05T10:00:00Z");
    private static final Instant BEFORE_SALE = Instant.parse("2026-03-20T10:00:00Z");

    private StoreProduct onSale() {
        StoreProduct sp = new StoreProduct();
        sp.setStorePrice(new BigDecimal("2000.00"));
        sp.setDiscountType(Product.DiscountType.PERCENTAGE);
        sp.setDiscountValue(new BigDecimal("25"));
        sp.setDiscountStartsAt(Instant.parse("2026-03-25T00:00:00Z"));
        sp.setDiscountEndsAt(Instant.parse("2026-03-31T20:00:00Z"));
        return sp;
    }

    // ── What a line should cost, now ──────────────────────────────────────────

    @Test
    void aLineAddedDuringTheSaleGetsTheSalePriceAndAWasPriceToStrikeThrough() {
        CartLinePricing.Priced priced = CartLinePricing.price(onSale(), DURING_SALE);

        assertEquals(new BigDecimal("1500.00"), priced.unitPrice());
        assertEquals(new BigDecimal("2000.00"), priced.originalUnitPrice());
    }

    @Test
    void afterTheSaleEndsTheSameLineRePricesToFullPriceAndLosesItsWasPrice() {
        // The leak this closes: the basket used to keep charging 1500 forever, and keep showing "was
        // 2000" above it, for a sale that finished last week. Note BOTH halves — a line that re-prices
        // to 2000 while still carrying originalUnitPrice=2000 renders a strike-through over an
        // identical number, which looks like a rendering bug and reads as a fake discount.
        CartLinePricing.Priced priced = CartLinePricing.price(onSale(), AFTER_SALE);

        assertEquals(new BigDecimal("2000.00"), priced.unitPrice());
        assertNull(priced.originalUnitPrice());
    }

    @Test
    void aLineAddedBeforeTheSaleStartedIsFullPricedAndTheDropIsPickedUpOnTheNextRead() {
        // The other direction, and the reason re-pricing is not "only when it favours us": a basket
        // filled the day before a sale used to be charged full price while the product page advertised
        // the discount, with no signal anywhere that the price had dropped.
        StoreProduct sp = onSale();

        assertEquals(new BigDecimal("2000.00"), CartLinePricing.price(sp, BEFORE_SALE).unitPrice());
        assertEquals(new BigDecimal("1500.00"), CartLinePricing.price(sp, DURING_SALE).unitPrice());
    }

    @Test
    void aPermanentMarkdownIsNotDisturbedByRePricing() {
        // Every discount that existed before V60 is one of these. Re-pricing must be a no-op for them,
        // or this change starts moving prices on the existing catalogue.
        StoreProduct permanent = new StoreProduct();
        permanent.setStorePrice(new BigDecimal("2000.00"));
        permanent.setDiscountType(Product.DiscountType.FIXED);
        permanent.setDiscountValue(new BigDecimal("999.00"));

        CartLinePricing.Priced then = CartLinePricing.price(permanent, BEFORE_SALE);
        CartLinePricing.Priced later = CartLinePricing.price(permanent, AFTER_SALE);

        assertEquals(new BigDecimal("999.00"), then.unitPrice());
        assertEquals(then.unitPrice(), later.unitPrice());
        assertFalse(CartLinePricing.moved(then.unitPrice(), later.unitPrice()));
    }

    // ── Whether the checkout refuses ─────────────────────────────────────────

    @Test
    void aPriceThatRoseSinceTheBasketWasShownIsTreatedAsMoved() {
        // The sale ended between the cart being rendered and Pay being pressed. Charging 2000 for what
        // the screen said was 1500 is the failure this whole feature must not introduce, so the
        // checkout refuses and the customer re-confirms.
        assertTrue(CartLinePricing.moved(new BigDecimal("1500.00"), new BigDecimal("2000.00")));
    }

    @Test
    void aPriceThatFellSinceTheBasketWasShownIsAlsoTreatedAsMoved() {
        // Deliberately symmetric. Charging less than the basket said leaves the screen and the receipt
        // disagreeing, which nothing downstream can explain to a customer — and a sale starting
        // mid-checkout is exactly as much a price change as one ending.
        assertTrue(CartLinePricing.moved(new BigDecimal("2000.00"), new BigDecimal("1500.00")));
    }

    @Test
    void anUnchangedPriceLetsTheCheckoutThroughEvenAtADifferentScale() {
        // compareTo, not equals. 1500.00 and 1500.000 are the same money; treating a scale difference
        // from a column definition or a currency conversion as a price change would refuse every
        // checkout in the shop.
        assertFalse(CartLinePricing.moved(new BigDecimal("1500.00"), new BigDecimal("1500.00")));
        assertFalse(CartLinePricing.moved(new BigDecimal("1500.00"), new BigDecimal("1500.000")));
    }

    // ── The struck-through figure is a second number, and it moves on its own ──

    @Test
    void theWasFigureMovingWithoutTheChargedPriceIsStillAMovement() {
        // Stamped at 800 with "was 1000" on a FIXED 800 sale; the admin lowers the listing's storePrice
        // to 850. The charged price does not move, so the unit-price gate skipped the line — and the
        // basket went on claiming a 200 saving while the product page showed 50. Both figures are shown
        // to the customer, and V61 copies the "was" one onto the order as the record of which advertised
        // price was honoured, so both have to be true.
        assertTrue(CartLinePricing.originalMoved(new BigDecimal("1000.00"), new BigDecimal("850.00")));
    }

    @Test
    void aWasFigureAppearingOrDisappearingIsAMovementBecauseNullIsAValueHere() {
        // Unlike a unit price, null does not mean "unknown" for this figure — it means "not on a sale".
        // A sale ending has to clear the strike-through, and a sale starting has to add one.
        assertTrue(CartLinePricing.originalMoved(new BigDecimal("1000.00"), null));
        assertTrue(CartLinePricing.originalMoved(null, new BigDecimal("1000.00")));
        assertFalse(CartLinePricing.originalMoved(null, null));
    }

    @Test
    void theSameWasFigureAtADifferentScaleIsNotAMovement() {
        assertFalse(CartLinePricing.originalMoved(new BigDecimal("1000.00"), new BigDecimal("1000.000")));
    }

    @Test
    void anUnknownPriceIsNotAPriceDispute() {
        // A line whose listing has been switched off or removed. That is a stock/availability problem
        // and the order path refuses it with a message about availability; turning it into a 409 about
        // money would send the shopper to a basket where nothing looks wrong.
        assertFalse(CartLinePricing.moved(new BigDecimal("1500.00"), null));
        assertFalse(CartLinePricing.moved(null, new BigDecimal("1500.00")));
    }
}
