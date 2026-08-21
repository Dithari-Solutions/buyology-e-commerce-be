package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.payment.exception.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins which gateway failures are allowed to release a refund claim.
 *
 * <p>The claim is written and committed before the refund is sent, so that a rollback can never
 * erase a refund that already took the customer's money. The cost of that choice is that a claim
 * left behind blocks the transaction from ever being refunded again — so releasing it correctly is
 * the other half of the mechanism, and it is decided entirely by which exception came back.
 *
 * <p>The distinction is not academic. Getting it wrong in one direction sends a customer their
 * money twice with nothing to notice; in the other it strands a legitimate refund forever behind a
 * PENDING row nobody knows to clear.
 *
 * <p>The subtle part, and the bug this was written for: {@code PaymobClient} converts every failure
 * — a 4xx, a 5xx, and a socket timeout alike — into {@link PaymentGatewayException}. Testing the
 * thrown exception's own type therefore never matched anything, so a refund the gateway explicitly
 * declined stayed PENDING forever and permanently blocked that transaction.
 */
class RefundClaimReleaseTest {

    private static PaymentGatewayException asPaymobWraps(RuntimeException cause) {
        // Exactly what PaymobClient.post does with a failure.
        return new PaymentGatewayException("Payment provider rejected the request", cause);
    }

    @Test
    void aDeclinedRefundReleasesTheClaim() {
        // 4xx: the gateway understood the request and refused it. No money moved, so the claim must
        // come off — otherwise this transaction can never be refunded by anyone.
        assertTrue(RefundClaimStore.isDefiniteRefusal(asPaymobWraps(
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                        null, null, null))));
    }

    @Test
    void theWrapperDoesNotHideTheRefusal() {
        // The regression itself: the refusal is never the thrown exception, only its cause.
        RuntimeException thrown = asPaymobWraps(
                HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable",
                        null, null, null));

        assertFalse(thrown instanceof HttpClientErrorException,
                "if this ever becomes true the wrapper is gone and this test is measuring nothing");
        assertTrue(RefundClaimStore.isDefiniteRefusal(thrown),
                "the refusal has to be recognised through the wrapper");
    }

    @Test
    void aGatewayServerErrorKeepsTheClaim() {
        // 5xx: the gateway broke while handling the request. It may well have moved the money
        // before falling over, so releasing the claim here is how a customer gets paid twice.
        assertFalse(RefundClaimStore.isDefiniteRefusal(asPaymobWraps(
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error",
                        null, null, null))));
    }

    @Test
    void aTimeoutKeepsTheClaim() {
        // The most dangerous case: no answer at all. The refund may have executed.
        assertFalse(RefundClaimStore.isDefiniteRefusal(asPaymobWraps(
                new ResourceAccessException("Read timed out", new SocketTimeoutException()))));
    }

    @Test
    void anUnrelatedFailureKeepsTheClaim() {
        assertFalse(RefundClaimStore.isDefiniteRefusal(
                new IllegalStateException("something else went wrong entirely")));
    }

    @Test
    void findsARefusalNestedSeveralLayersDown() {
        assertTrue(RefundClaimStore.isDefiniteRefusal(
                new RuntimeException("outer", asPaymobWraps(
                        HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict",
                                null, null, null)))));
    }

    @Test
    void doesNotSpinOnASelfReferencingCause() {
        // Defensive: a cause chain that points at itself must terminate, not hang the refund path.
        RuntimeException loop = new RuntimeException("loop") {
            @Override public synchronized Throwable getCause() { return this; }
        };
        assertFalse(RefundClaimStore.isDefiniteRefusal(loop));
    }
}
