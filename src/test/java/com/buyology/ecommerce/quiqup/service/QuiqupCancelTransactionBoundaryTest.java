package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the transaction shape of the courier-cancel leg, because every part of it is a way this
 * codebase has already broken once.
 *
 * <p>Three rules, three past incidents:
 * <ul>
 *   <li>An HTTP call to Quiqup (up to 20s) must hold no transaction and no pooled connection —
 *       the entry points that make one are therefore NOT {@code @Transactional}.
 *   <li>Writes made from inside an after-commit callback silently join the finished transaction
 *       and are discarded unless they are REQUIRES_NEW — the cancel service therefore builds its
 *       {@code TransactionTemplate} with {@code PROPAGATION_REQUIRES_NEW} explicitly, unlike the
 *       dispatch service, whose {@code @Async} context makes the default safe.
 *   <li>The transactional halves are public and reached through a proxy, or their annotation
 *       never applies at all.
 * </ul>
 */
class QuiqupCancelTransactionBoundaryTest {

    private static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            return fail(type.getSimpleName() + "#" + name + " was renamed or removed; the boundary "
                    + "it documents still has to live somewhere", e);
        }
    }

    @Test
    void theOutboundCancelHoldsNoTransaction() {
        Method m = method(QuiqupCancelService.class, "cancelForOrder", UUID.class, String.class);
        assertNull(m.getAnnotation(Transactional.class),
                "cancelForOrder makes an HTTP call of up to quiqup.timeout-ms — a transaction "
                        + "wrapped around it pins a pooled connection for the whole wait");
    }

    @Test
    void theCustomerEntryPointHoldsNoTransactionEither() {
        Method m = method(OrderService.class, "customerCancelOrder",
                UUID.class, UUID.class, String.class);
        assertNull(m.getAnnotation(Transactional.class),
                "customerCancelOrder pre-flights the courier over HTTP; the transactional half is "
                        + "applyCustomerCancellation, reached through the proxy");
    }

    @Test
    void theTransactionalHalfIsProxyReachable() {
        Method m = method(OrderService.class, "applyCustomerCancellation",
                UUID.class, UUID.class, String.class, QuiqupCancelService.CancelResult.class);
        assertNotNull(m.getAnnotation(Transactional.class),
                "applyCustomerCancellation is the half that writes; it must be transactional");
        assertTrue(java.lang.reflect.Modifier.isPublic(m.getModifiers()),
                "must be public or the proxy cannot intercept it and @Transactional is ignored");
    }

    @Test
    void thePostCancellationRecorderCommitsIndependently() {
        Method m = method(OrderService.class, "recordPostCancellationMovement",
                UUID.class, String.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation(),
                "invoked from the webhook path with no transaction of its own, and its record must "
                        + "survive whatever the caller does next");
    }

    @Test
    void theCancelServicesTemplateIsExplicitlyRequiresNew() throws Exception {
        // Not reachable by reflection — the propagation lives in a constructor call — so pin the
        // source. A plain `new TransactionTemplate(tm)` here reads identically, compiles, and
        // silently discards every write the service makes from an after-commit callback.
        String source = Files.readString(Path.of(
                "src/main/java/com/buyology/ecommerce/quiqup/service/QuiqupCancelService.java"));
        assertTrue(source.contains("setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)"),
                "QuiqupCancelService's TransactionTemplate must be REQUIRES_NEW: it is called on "
                        + "the after-commit thread where the finished transaction is still bound, "
                        + "and a REQUIRED template would join it and lose every write");
    }

    @Test
    void everyCancelSideEffectCallSiteStillPassesTheGate() throws Exception {
        // The boolean is the courier gate. A call site that hardcodes `true` on a path where the
        // courier was NOT verified reopens the goods-and-money loss; the only legitimate literal
        // `true` callers are the partner-confirmed path, the confirmed-preflight path and the retry
        // job, each of which has just verified the stop.
        String source = Files.readString(Path.of(
                "src/main/java/com/buyology/ecommerce/order/service/OrderService.java"));
        long gated = source.lines()
                .filter(l -> l.contains(".applyCancellationSideEffects("))
                .count();
        assertTrue(gated >= 3, "expected the three call sites to still exist; found " + gated);
        assertFalse(source.contains("applyCancellationSideEffects(saved, req.getCancellationReason())"),
                "the admin path must pass the courier outcome, not omit the gate");
    }
}
