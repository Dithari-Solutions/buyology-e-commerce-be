package com.buyology.ecommerce.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code @Transactional} onto the two public entry points that CREATE an order.
 *
 * <p>Why a reflection test and not a behavioural one: transactionality is invisible to every unit
 * test in this suite. The mocks accept writes happily with no transaction at all, so a lost
 * {@code @Transactional} passes all 551 tests and only shows up in production as a half-written
 * order — {@code createOrder} and {@code createBuyNowOrder} each perform many repository writes plus
 * stock and promo-reservation side effects, and they are exactly the methods that must not be able
 * to commit some of them.
 *
 * <p>This test exists because the annotation WAS lost, in a way no reviewer would spot and no test
 * could catch. A private helper was inserted between {@code createBuyNowOrder}'s javadoc and its
 * signature, so the file still read as:
 *
 * <pre>
 *   &#47;** Creates a "Buy Now" order ... *&#47;
 *   &#64;Transactional
 *   &#47;** Refuses the checkout if any line's price ... *&#47;
 *   private void requireCartPricesUnchanged(...) { ... }
 *
 *   public OrderResponse createBuyNowOrder(...) { ... }
 * </pre>
 *
 * <p>That is legal Java: an annotation binds to the next DECLARATION, not the next comment. So the
 * annotation silently moved onto a private method — where Spring's proxy ignores it entirely — and
 * Buy Now lost its transaction, every write inside it committing independently. Restoring it is a
 * one-line move; noticing it is the hard part, which is what this test is for.
 */
class OrderWriteTransactionBoundaryTest {

    @Test
    void createOrderIsTransactional() {
        assertTrue(isTransactional("createOrder"),
                "createOrder must be @Transactional — it writes the order, its items, stock "
                        + "reservations and a promo reservation, and a partial commit leaves stock "
                        + "withheld against an order that does not exist");
    }

    @Test
    void createBuyNowOrderIsTransactional() {
        assertTrue(isTransactional("createBuyNowOrder"),
                "createBuyNowOrder must be @Transactional — it builds an ephemeral cart and runs the "
                        + "full createOrder pipeline, so without a transaction a failure part-way "
                        + "leaves that cart, the order and the stock it moved permanently "
                        + "inconsistent. If this failed after an edit near the method, check whether "
                        + "something was inserted between its javadoc and its signature and stole "
                        + "the annotation.");
    }

    /**
     * True when EVERY overload of the named public method carries the annotation. Every overload,
     * because a caller reaching an unannotated one gets no transaction and the name alone gives no
     * hint which it hit.
     */
    private static boolean isTransactional(String methodName) {
        Method[] matches = Arrays.stream(OrderService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .toArray(Method[]::new);
        assertTrue(matches.length > 0, "No method named " + methodName + " on OrderService — "
                + "if it was renamed, rename it here too rather than deleting this guard");
        return Arrays.stream(matches).allMatch(m -> m.isAnnotationPresent(Transactional.class));
    }
}
