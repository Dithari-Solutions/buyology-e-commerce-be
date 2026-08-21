package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.payment.dto.RefundRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the transaction boundaries a refund runs on, because getting them wrong stopped refunds
 * dead and did it silently.
 *
 * <p>What happened: {@code initiateRefund} was {@code @Transactional} and took
 * {@code SELECT … FOR UPDATE} on the payment_transactions row, then called a REQUIRES_NEW method to
 * write the claim. That claim runs on a second connection, and inserting a payment_refunds row
 * makes PostgreSQL take FOR KEY SHARE on the parent transaction it references — which conflicts
 * with FOR UPDATE. So the inner transaction waited for a lock the outer held, while the outer sat
 * waiting for the inner to return. Postgres could not call it a deadlock, because the outer was
 * idle in transaction rather than waiting on a lock, so nothing broke the tie: every refund and
 * every cancellation of a paid order hung until something timed out.
 *
 * <p>Two properties keep it fixed, and neither is visible at the call site:
 * <ul>
 *   <li>the lock and the claim share ONE transaction — a transaction never blocks on its own locks;
 *   <li>{@code initiateRefund} holds no transaction at all, so the gateway call — the slowest thing
 *       in the flow — never pins a database connection, and no rollback can erase a refund that
 *       already took the customer's money.
 * </ul>
 */
class RefundTransactionBoundaryTest {

    private static Method method(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            return fail("Method " + type.getSimpleName() + "#" + name + " was renamed or removed; "
                    + "the boundary it documents still has to live somewhere", e);
        }
    }

    @Test
    void theGatewayCallRunsInNoTransaction() {
        Method m = method(PaymentService.class, "initiateRefund", RefundRequest.class);
        assertNull(m.getAnnotation(Transactional.class),
                "initiateRefund must NOT be @Transactional: it calls Paymob over HTTP, and a "
                        + "transaction wrapped around that both pins a connection for the duration "
                        + "and lets a rollback erase a refund the customer has already been paid");
    }

    @Test
    void theLockAndTheClaimShareOneTransaction() {
        // The fix itself. If someone splits these apart again — lock in the caller, claim in a
        // REQUIRES_NEW callee — every refund hangs, and it hangs in production rather than here.
        Method m = method(RefundClaimStore.class, "lockCheckAndClaim", RefundRequest.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx, "lockCheckAndClaim must be @Transactional");
        assertEquals(Propagation.REQUIRES_NEW, tx.propagation(),
                "the claim must commit independently of any caller — that is what stops a later "
                        + "rollback erasing the record of a refund that has already gone out");
    }

    @Test
    void settlingAndReleasingAlsoCommitIndependently() {
        for (Method m : new Method[]{
                method(RefundClaimStore.class, "settleClaim", java.util.UUID.class, String.class),
                method(RefundClaimStore.class, "releaseOrFailClaim", java.util.UUID.class, RuntimeException.class)}) {
            Transactional tx = m.getAnnotation(Transactional.class);
            assertNotNull(tx, m.getName() + " must be @Transactional");
            assertEquals(Propagation.REQUIRES_NEW, tx.propagation(),
                    m.getName() + " records the outcome of a gateway call that has already "
                            + "happened; it cannot be allowed to roll back with a caller");
        }
    }

    @Test
    void paymentServiceNeverLocksTheTransactionRowItselfDuringARefund() throws Exception {
        // The lock belongs to lockCheckAndClaim now. A findWithLockById reappearing in
        // initiateRefund is precisely how the hang comes back.
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/buyology/ecommerce/payment/service/PaymentService.java"));
        int start = source.indexOf("public RefundResponse initiateRefund(");
        assertTrue(start > 0, "initiateRefund not found");
        int end = source.indexOf("\n    }", start);
        String body = source.substring(start, end);

        assertFalse(body.contains("findWithLockById"),
                "initiateRefund must not take the row lock — the claim store owns it, in the same "
                        + "transaction as the insert that would otherwise block on it:\n" + body);
    }
}
