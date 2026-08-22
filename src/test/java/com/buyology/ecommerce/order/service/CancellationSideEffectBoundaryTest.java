package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.membership.service.CreditReturnService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the transaction boundary the cancellation refund runs on.
 *
 * <p>Cancelling a paid order spends money it cannot take back: a Paymob refund goes out, wallet
 * credit lands in a member's balance, emails leave. Two settings decide whether the database ends up
 * agreeing with any of that, and neither one is visible at the call site.
 *
 * <p>The first is <em>when</em>: after the cancellation commits, never inside it. Inside, any later
 * failure in the same transaction rolls the order back to PAID while the refund is already on its
 * way — an order that has been fully refunded, is still shippable, and looks untouched.
 *
 * <p>The second is <em>how</em>: REQUIRES_NEW, reached through the bean's proxy. Spring leaves the
 * finished transaction bound to the thread while after-commit callbacks run, so a REQUIRED
 * transaction opened here silently <em>joins</em> a transaction nobody will commit — every row
 * recording the refund is flushed into a session that is then closed, while the money still reaches
 * Paymob. A private method, or a plain {@code this.} call, has the same effect by skipping the proxy
 * that applies the annotation at all.
 *
 * <p>Both read as tidy-up an editor would happily "simplify", and neither failure announces itself
 * anywhere except a support ticket about a refund nobody can find. Hence this test.
 */
class CancellationSideEffectBoundaryTest {

    private static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            return fail("Method " + type.getSimpleName() + "#" + name + " was renamed or removed; "
                    + "the transaction boundary it documents still has to live somewhere", e);
        }
    }

    private static void assertRequiresNewAndProxyable(Method m) {
        assertTrue(Modifier.isPublic(m.getModifiers()),
                m.getName() + " must be public — Spring cannot apply @Transactional to a method its "
                        + "proxy cannot intercept, and the annotation would be silently ignored");
        assertFalse(Modifier.isFinal(m.getModifiers()),
                m.getName() + " must not be final — a CGLIB proxy cannot override it");

        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx, m.getName() + " must be @Transactional");
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation(),
                m.getName() + " must be REQUIRES_NEW: it runs after another transaction has "
                        + "committed, where REQUIRED joins that dead transaction and loses its writes");
    }

    @Test
    void theRefundRunsInItsOwnTransaction() {
        assertRequiresNewAndProxyable(method(OrderService.class, "applyCancellationSideEffects",
                com.buyology.ecommerce.order.domain.Order.class, String.class, boolean.class));
    }

    @Test
    void theCreditReturnRunsInItsOwnTransaction() {
        assertRequiresNewAndProxyable(method(CreditReturnService.class, "returnForCancelledOrder",
                java.util.UUID.class, java.math.BigDecimal.class));
    }

    @Test
    void everyCancellationCallSiteGoesThroughTheProxy() throws Exception {
        // A bare `applyCancellationSideEffects(...)` compiles, reads better, and quietly drops the
        // REQUIRES_NEW above — self-invocation never touches the proxy. The only way to keep that
        // from creeping back in is to say so where it would be introduced.
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/buyology/ecommerce/order/service/OrderService.java"));

        for (String line : source.split("\n")) {
            if (!line.contains("applyCancellationSideEffects(") || line.contains("public void ")) {
                continue;
            }
            assertTrue(line.contains("selfProvider.getObject().applyCancellationSideEffects("),
                    "self-invocation bypasses the proxy and drops REQUIRES_NEW: " + line.trim());
        }
    }
}
