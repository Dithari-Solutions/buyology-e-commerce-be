package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.cart.domain.CartPriceChangedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the two halves of the checkout's price decision: WHICH WAY a movement has to go before a
 * customer is refused, and WHERE in createOrder the decision happens.
 *
 * <p>Both were wrong in the first pass, and in opposite ways. The rule refused a basket that had got
 * CHEAPER — a 409 no released client handles, i.e. a customer who cannot buy, to tell them they are
 * paying less. And the check ran BELOW the prior-order reuse, where it could not be reached at all by
 * the one request that needed it: a second createOrder for a cart whose sale has ended matched the
 * prior PENDING_PAYMENT order on the stamped figures and was handed straight back at the sale price.
 *
 * <p>The second pass added the third: the decision was made on the summed TOTALS, so a rise on one line
 * was paid for by an unrelated drop on another and the customer was overcharged on the first with
 * nothing recording it. {@link #aRiseIsRefusedEvenWhenAnotherLineFallsByExactlyAsMuch} is that case,
 * and it is the one a total-based test cannot see.
 */
class CheckoutRepricingTest {

    private static final UUID LINE_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID LINE_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    private static CheckoutRepricing.Line line(UUID id, int qty, String stamped, String live) {
        return new CheckoutRepricing.Line(id, "SKU-" + id, qty,
                new BigDecimal(stamped), null, live == null ? null : new BigDecimal(live), null);
    }

    /** A line carrying a struck-through "was" figure as well as its charged price. */
    private static CheckoutRepricing.Line line(UUID id, int qty, String stamped, String stampedWas,
                                               String live, String liveWas) {
        return new CheckoutRepricing.Line(id, "SKU-" + id, qty,
                new BigDecimal(stamped), stampedWas == null ? null : new BigDecimal(stampedWas),
                live == null ? null : new BigDecimal(live),
                liveWas == null ? null : new BigDecimal(liveWas));
    }

    // ── Which way it has to move ─────────────────────────────────────────────

    @Test
    void aBasketThatGotDearerIsRefused() {
        // The sale ended between the basket being shown at 1500 and Pay being pressed. Charging 2000
        // for what the screen said was 1500 is the failure this feature must not introduce.
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(
                List.of(line(LINE_A, 1, "1500.00", "2000.00")));

        assertTrue(outcome.anythingMoved());
        assertTrue(outcome.dearer(), "a rise must be refused");
        assertEquals(0, new BigDecimal("1500.00").compareTo(outcome.stampedTotal()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(outcome.liveTotal()));
        assertEquals(LINE_A, outcome.largestRise().cartItemId());
    }

    @Test
    void aBasketThatGotCheaperIsChargedAtTheLowerPriceRatherThanRefused() {
        // A sale that started while the customer was typing their address. Refusing here is strictly
        // worse than the overcharge the refusal exists to prevent: both clients surface the 409 as a
        // failed order, so "good news, it is cheaper" becomes "you cannot check out".
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(
                List.of(line(LINE_A, 2, "2000.00", "1500.00")));

        assertTrue(outcome.anythingMoved(), "the line still has to be corrected");
        assertFalse(outcome.dearer(), "a drop must never refuse a checkout");
        assertEquals(0, new BigDecimal("3000.00").compareTo(outcome.liveTotal()),
                "the quantity has to be in the total, or a two-unit drop looks like a one-unit one");
    }

    @Test
    void anUnchangedBasketRefusesNothingAndCorrectsNothing() {
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(
                List.of(line(LINE_A, 1, "1500.00", "1500.00"), line(LINE_B, 3, "10.00", "10.000")));

        assertFalse(outcome.anythingMoved(), "a scale difference is the same money, not a price change");
        assertFalse(outcome.dearer());
    }

    @Test
    void aRiseIsRefusedEvenWhenAnotherLineFallsByExactlyAsMuch() {
        // THE per-line test, and a total-based one passes straight over it. One sale ended (+500) while
        // another started (-500), so the two totals are identical — and the customer is still being
        // charged 2000 for a line the screen said was 1500. They were quoted each line, not a total, so
        // an unrelated drop is not their consent to pay more for this one, and nothing in the order
        // would record that it happened.
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(List.of(
                line(LINE_A, 1, "1500.00", "2000.00"),
                line(LINE_B, 1, "2000.00", "1500.00")));

        assertTrue(outcome.anythingMoved());
        assertEquals(2, outcome.moved().size(), "both lines still need re-stamping");
        assertEquals(0, outcome.stampedTotal().compareTo(outcome.liveTotal()),
                "the totals net to zero — which is exactly why they must not be what decides");
        assertTrue(outcome.dearer(), "ANY line getting dearer refuses the checkout");
        assertEquals(LINE_A, outcome.largestRise().cartItemId(), "and it names the line that rose");
    }

    @Test
    void aBasketWhereEveryChangeIsADropPassesSilentlyHoweverManyLinesMoved() {
        // The converse is deliberately NOT symmetrical. There is no customer interest in being stopped
        // to be told they are paying less, so no number of drops adds up to a refusal.
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(List.of(
                line(LINE_A, 1, "1500.00", "1200.00"),
                line(LINE_B, 4, "2000.00", "1999.99")));

        assertTrue(outcome.anythingMoved());
        assertEquals(2, outcome.moved().size());
        assertFalse(outcome.dearer(), "a drop must never refuse a checkout");
        assertNull(outcome.largestRise(), "and there is no line to name in a refusal that will not happen");
    }

    @Test
    void aLineWithNoLivePriceIsNeitherRefusedNorRePricedToNothing() {
        // A line whose store assignment has been switched off. Treating the unknown price as zero would
        // drop the basket total and quietly charge the customer for one item; the availability guards
        // further down are what should speak about this line, not a 409 about money.
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(List.of(
                line(LINE_A, 2, "500.00", null),
                line(LINE_B, 1, "100.00", "100.00")));

        assertFalse(outcome.anythingMoved());
        assertFalse(outcome.dearer());
        assertEquals(0, new BigDecimal("1100.00").compareTo(outcome.liveTotal()),
                "the unknown line must contribute its stamped price, not zero");
        assertEquals(0, outcome.stampedTotal().compareTo(outcome.liveTotal()));
    }

    @Test
    void theLargestRiseIsReportedSoTheLogNamesTheLineThatCausedIt() {
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(List.of(
                line(LINE_A, 1, "100.00", "150.00"),    // +50
                line(LINE_B, 10, "10.00", "20.00")));   // +100

        assertTrue(outcome.dearer());
        assertEquals(LINE_B, outcome.largestRise().cartItemId(),
                "quantity counts: a small unit rise on ten units is the bigger one");
    }

    // ── The struck-through figure moves on its own ───────────────────────────

    @Test
    void aLineWhoseWasFigureMovedIsReStampedEvenThoughTheChargedPriceDidNot() {
        // Stamped at 800 with "was 1000" on a FIXED 800 sale; the admin then lowers the listing's
        // storePrice to 850. The sale price is unchanged, so the old gate (unit price only) skipped the
        // line — and the basket went on advertising a 200 saving while the product page showed 50. The
        // same stale figure is copied onto order_items.original_unit_price, whose entire purpose is to
        // record WHICH advertised price was honoured.
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(
                List.of(line(LINE_A, 1, "800.00", "1000.00", "800.00", "850.00")));

        assertTrue(outcome.anythingMoved(), "the \"was\" figure is shown to the customer, so it has to be true");
        assertEquals(1, outcome.moved().size());
        assertFalse(outcome.dearer(),
                "nothing the customer pays has changed — a 409 here would stop a checkout to fix a "
                        + "strike-through");
        assertEquals(0, outcome.stampedTotal().compareTo(outcome.liveTotal()),
                "and the money is identical on both sides");
    }

    @Test
    void aSaleEndingIsStillOneMovementEvenThoughBothFiguresMoved() {
        // The ordinary end-of-sale: 800 (was 1000) goes back to 1000 with nothing struck through. It
        // rose, so it is refused before capture — and the line is counted once, not twice.
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(
                List.of(line(LINE_A, 1, "800.00", "1000.00", "1000.00", null)));

        assertEquals(1, outcome.moved().size());
        assertTrue(outcome.dearer());
    }

    @Test
    void aLineWithNoLiveListingIsNotReStampedByItsWasFigureEither() {
        // The listing is switched off, so both live figures are null. Null "was" must not read as "the
        // sale ended" and drag the line into a re-stamp: an unresolvable listing is an availability
        // problem, and the stock guards are what must speak about it.
        CheckoutRepricing.Outcome outcome = CheckoutRepricing.decide(
                List.of(line(LINE_A, 1, "800.00", "1000.00", null, null)));

        assertFalse(outcome.anythingMoved());
        assertFalse(outcome.dearer());
    }

    // ── What the refused customer is told ────────────────────────────────────

    @Test
    void theRefusalStandsOnItsOwnBecauseItIsAllTheCustomerWillSee() {
        // Both clients surface the server's message verbatim, and neither handles the 409 yet, so this
        // sentence is the entire experience until they are updated: what happened, that no money moved,
        // and what to do next. No amounts and no SKU — the basket shows both, in the customer's own
        // currency, which this class cannot format.
        String message = new CartPriceChangedException(LINE_A, "MBP-14-M4",
                new BigDecimal("1500.00"), new BigDecimal("2000.00")).getMessage();

        assertTrue(message.contains("not been charged") || message.contains("not charged"), message);
        assertTrue(message.toLowerCase().contains("basket"), message);
        assertFalse(message.contains("1500"), "no amounts: " + message);
        assertFalse(message.contains("MBP-14-M4"), "no SKUs: " + message);
        assertFalse(message.contains("409") || message.contains("Exception"), message);
    }

    // ── Where the decision runs ──────────────────────────────────────────────

    private static final Path SOURCE =
            Path.of("src/main/java/com/buyology/ecommerce/order/service/OrderService.java");

    @Test
    void createOrderRePricesBeforeItCanReuseOrPriceAnything() throws Exception {
        String source = Files.readString(SOURCE);
        int createOrder = source.indexOf("public OrderResponse createOrder(");
        assertTrue(createOrder > 0, "createOrder not found");

        int reprice = source.indexOf("repriceForCheckout(cart, cartItems, phase);", createOrder);
        int reuse = source.indexOf("CheckoutIdentity.isSameCheckout(", createOrder);
        int fulfilment = source.indexOf("resolveFulfilment(userId", createOrder);
        int subtotal = source.indexOf("BigDecimal subtotal = cart.getTotalPrice();", createOrder);

        assertTrue(reprice > 0, "createOrder must re-price the basket — if this was renamed, "
                + "rename it here rather than deleting the guard");
        assertTrue(reprice < reuse,
                "re-pricing must run ABOVE the prior-order reuse. Below it, a repeat createOrder for a "
                        + "cart whose sale has ended matches the prior order on the STAMPED subtotal and "
                        + "per-line prices, is returned at the old sale price, and never reaches this check.");
        assertTrue(reprice < fulfilment,
                "and above resolveFulfilment, which puts cart.getTotalPrice() through the free-delivery "
                        + "threshold — a fee quoted from a total that is about to change is the same bug one "
                        + "field over");
        assertTrue(reprice < subtotal, "and above the subtotal the order is actually priced from");
    }
}
