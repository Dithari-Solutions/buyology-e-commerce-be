package com.buyology.ecommerce.cart.reminder;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.StoreProduct;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that the abandoned-cart email prices a line from the LIVE listing, not from the figure frozen
 * onto the row when the shopper added it.
 *
 * <p>An email is the one surface that is a written promise: a cached card can be reloaded and a basket
 * re-renders on every read, but a message sitting in an inbox quoting a price cannot be corrected. This
 * sweep writes to carts nobody has opened for a day — exactly the ones whose stamped prices are most
 * likely to be stale — so since V60 the stamped figure can be a sale price for a sale that has finished.
 *
 * <p>Two things are asserted, and they are deliberately different in kind. The SQL must fetch the
 * listing's discount columns at all (there is no in-memory database here, so the join is checked as a
 * contract on the statement), and the arithmetic must be the shared function rather than a second
 * implementation — the failure mode this repo has already written a comment about being an email that
 * quotes a number no other surface agrees with.
 *
 * <p>There is no variant join and no variant branch. A variant line is priced from its parent listing
 * like every other line (see {@code CartLinePricing}), so the email does not need to know whether a line
 * carries a variant — which is also why {@code ci.variant_id} is not selected.
 */
class CartReminderPricingTest {

    private static final Instant DURING = Instant.parse("2026-03-26T10:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-03-25T00:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-03-31T20:00:00Z");

    private String linesSql() throws Exception {
        Field field = CartReminderRepository.class.getDeclaredField("LINES");
        field.setAccessible(true);
        return ((String) field.get(null)).replaceAll("\\s+", " ");
    }

    @Test
    void theLineQueryJoinsTheLiveListingSoALineCanBeRePricedAtAll() throws Exception {
        // Without the join there is nothing to re-price from, and the code falls back to the stamped
        // figure — which is the frozen number this test exists to stop being emailed.
        String sql = linesSql();

        assertTrue(sql.contains("LEFT JOIN store_products sp"), sql);
        assertTrue(sql.contains("sp.store_id = ci.store_id"), sql);
        assertTrue(sql.contains("sp.product_id = ci.product_id"), sql);
        assertTrue(sql.contains("sp.is_active = TRUE"), sql);
        assertTrue(sql.contains("sp.deleted_at IS NULL"),
                "a delisted assignment is not a price, so it must not be priced into an email");
    }

    @Test
    void theWindowColumnsAreSelectedBecauseWithoutThemAnEndedSaleStillPrices() throws Exception {
        String sql = linesSql();

        assertTrue(sql.contains("sp.store_price"), sql);
        assertTrue(sql.contains("sp.discount_type"), sql);
        assertTrue(sql.contains("sp.discount_value"), sql);
        assertTrue(sql.contains("sp.discount_starts_at"), sql);
        assertTrue(sql.contains("sp.discount_ends_at"), sql);
    }

    @Test
    void theEmailDoesNotReadTheVariantTableForAPrice() throws Exception {
        // The one number the email may quote is the listing's, because that is the one number every other
        // surface quotes. Joining store_product_variants back in would be the first step to a second one.
        String sql = linesSql();

        assertFalse(sql.contains("store_product_variants"), sql);
    }

    @Test
    void theArithmeticIsNotReimplementedInSql() throws Exception {
        // The repository's own comment insists on this, and it is the reason the discount columns come out
        // of the join raw. A second implementation of the discount maths in SQL is precisely how an email
        // ends up quoting a figure the cart and the card both disagree with.
        String sql = linesSql();

        assertFalse(sql.contains("CASE WHEN sp.discount_type"), sql);
        assertFalse(sql.toUpperCase().contains("PERCENTAGE"), sql);
    }

    @Test
    void theEmailQuotesTheSameNumberTheBasketWouldCharge() {
        // The value the reader of that email will see, computed by the function the email actually calls.
        // Listing 1000 at 25% off -> 750.00, and the line is quantity 2.
        BigDecimal unit = StoreProduct.effectivePrice(new BigDecimal("1000.00"),
                Product.DiscountType.PERCENTAGE, new BigDecimal("25"), STARTS, ENDS, DURING);

        assertEquals(new BigDecimal("750.00"), unit);
        assertEquals(new BigDecimal("1500.00"), unit.multiply(BigDecimal.valueOf(2)),
                "the email quotes a line total, and it must be the discounted one");
    }

    @Test
    void anEndedSaleIsNotEmailedAsIfItWereStillRunning() {
        // The direction that costs trust rather than money: a message promising a sale price for a sale
        // that finished. The window is evaluated at send time, not at add-to-cart.
        BigDecimal afterTheSale = StoreProduct.effectivePrice(new BigDecimal("1000.00"),
                Product.DiscountType.PERCENTAGE, new BigDecimal("25"), STARTS, ENDS,
                Instant.parse("2026-04-05T10:00:00Z"));

        assertEquals(new BigDecimal("1000.00"), afterTheSale);
    }
}
