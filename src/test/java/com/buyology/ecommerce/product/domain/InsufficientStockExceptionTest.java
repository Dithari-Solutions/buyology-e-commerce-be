package com.buyology.ecommerce.product.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a customer reads when the units are not there, and what stays out of their way.
 *
 * <p>Worth pinning because the message goes straight into a 409 response body that clients display.
 * The refusals this replaced were phrased {@code "Insufficient stock for product 3f2a1b4c-… variant
 * 9d8e7f… (requested 2)"} — internal identifiers, shown to a shopper.
 */
class InsufficientStockExceptionTest {

    private static final UUID PRODUCT = UUID.fromString("3f2a1b4c-0000-0000-0000-000000000001");

    @Test
    void namesTheNumberLeftRatherThanJustSayingNo() {
        // "Out of stock" about a line sitting in their basket reads as a bug. The count is the useful
        // part: it tells them what they CAN have.
        assertEquals("Only 2 left in stock.",
                new InsufficientStockException(PRODUCT, "SKU-1", 5, 2).getMessage());
    }

    @Test
    void theSingularIsNotOneLeftInStockWithAnS() {
        assertEquals("Only 1 left in stock.",
                new InsufficientStockException(PRODUCT, "SKU-1", 4, 1).getMessage());
    }

    @Test
    void zeroReadsAsSoldOutRatherThanOnlyZeroLeft() {
        assertEquals("This item is now out of stock.",
                new InsufficientStockException(PRODUCT, "SKU-1", 1, 0).getMessage());
    }

    @Test
    void anUnknownCountAlsoReadsAsSoldOut() {
        // The variant guard cannot cheaply say how many remain — its conditional UPDATE only reports
        // that it matched nothing — so it passes null rather than inventing a figure.
        assertEquals("This item is now out of stock.",
                new InsufficientStockException(PRODUCT, "SKU-1", 1, null).getMessage());
    }

    @Test
    void aNegativeStoredCountIsNotShownToTheCustomer() {
        // Defensive: a negative count is now barred by a DB constraint, but if one ever appeared,
        // "Only -3 left in stock." must not reach a shopper.
        assertEquals("This item is now out of stock.",
                new InsufficientStockException(PRODUCT, "SKU-1", 1, -3).getMessage());
    }

    @Test
    void theIdentifiersAreKeptForTheLogAndOutOfTheMessage() {
        InsufficientStockException e = new InsufficientStockException(PRODUCT, "SKU-ABC", 5, 2);

        assertFalse(e.getMessage().contains(PRODUCT.toString()),
                "a customer-facing message must not carry a product UUID");
        assertFalse(e.getMessage().contains("SKU-ABC"));

        // The same facts are still recoverable where they are actually useful.
        assertTrue(e.describeForLog().contains(PRODUCT.toString()));
        assertTrue(e.describeForLog().contains("SKU-ABC"));
        assertTrue(e.describeForLog().contains("requested=5"));
        assertTrue(e.describeForLog().contains("available=2"));
    }
}
