package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.buyology.ecommerce.quiqup.service.QuiqupCancelService.CancelResult;
import com.buyology.ecommerce.quiqup.service.QuiqupCancelService.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins what a Quiqup cancel response is allowed to MEAN, because the meaning releases money.
 *
 * <p>The cancel endpoint's contract is documented as unverified in two places, and it is a batch
 * endpoint that could quietly skip our id while returning 200. So the classifier's rule is that a
 * PUT is never believed on its own — every money-releasing verdict comes from re-reading the job —
 * and these tests are the rule's enforcement. Each wrong classification here is a concrete loss:
 * CONFIRMED too eagerly refunds a customer whose parcel is still arriving; UNCONFIRMED too eagerly
 * strands a refund that was safe to send.
 */
class QuiqupCancelInterpretationTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static QuiqupApiResult get(int status, String json) {
        try {
            return new QuiqupApiResult(status, status >= 200 && status < 300, M.readTree(json));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── The verdict comes from the re-read, not the PUT ──────────────────────

    @Test
    void aCancelledJobConfirms() {
        CancelResult r = QuiqupCancelService.interpretVerification(true,
                get(200, "{\"order\":{\"id\":26012997,\"state\":\"cancelled\"}}"));
        assertEquals(Outcome.CONFIRMED, r.outcome());
        assertTrue(r.refundAllowed());
    }

    @Test
    void aCollectedJobRefusesEvenThoughThePutSaid200() {
        // The batch endpoint answered 2xx — and the job is in a van anyway. Trusting the PUT here
        // is precisely how the customer ends up with the goods and the money.
        CancelResult r = QuiqupCancelService.interpretVerification(true,
                get(200, "{\"state\":\"out_for_delivery\"}"));
        assertEquals(Outcome.REFUSED_TOO_LATE, r.outcome());
        assertFalse(r.refundAllowed());
    }

    @Test
    void aDeliveredJobRefuses() {
        CancelResult r = QuiqupCancelService.interpretVerification(true,
                get(200, "{\"state\":\"delivery_complete\"}"));
        assertEquals(Outcome.REFUSED_TOO_LATE, r.outcome());
    }

    @Test
    void anUntouchedJobAfterAnAcceptedPutRetries() {
        // 2xx PUT, job still sitting at pending: the write changed nothing. Retryable — not
        // CONFIRMED (money would leave on no evidence) and not NEEDS_HUMAN (the next attempt may
        // simply work).
        CancelResult r = QuiqupCancelService.interpretVerification(true,
                get(200, "{\"state\":\"pending\"}"));
        assertEquals(Outcome.UNCONFIRMED, r.outcome());
        assertFalse(r.refundAllowed());
    }

    @Test
    void anUntouchedJobAfterARejectedPutEscalates() {
        // 4xx PUT and the job is untouched: a refusal we do not understand. Retrying the same
        // request is not a plan; a person is.
        CancelResult r = QuiqupCancelService.interpretVerification(false,
                get(200, "{\"state\":\"ready_for_collection\"}"));
        assertEquals(Outcome.NEEDS_HUMAN, r.outcome());
    }

    // ── A missing job is never proof of anything ─────────────────────────────

    @Test
    void a404OnTheReReadNeverConfirms() {
        // A missing job is as likely a wrong id as a cancelled one. CONFIRMED here releases the
        // customer's refund on no evidence at all.
        CancelResult afterOkPut = QuiqupCancelService.interpretVerification(true,
                new QuiqupApiResult(404, false, "not found"));
        assertEquals(Outcome.UNCONFIRMED, afterOkPut.outcome());
        assertFalse(afterOkPut.refundAllowed());

        CancelResult afterBadPut = QuiqupCancelService.interpretVerification(false,
                new QuiqupApiResult(404, false, "not found"));
        assertEquals(Outcome.NEEDS_HUMAN, afterBadPut.outcome());
    }

    @Test
    void aReturnedJobIsAHumansCall() {
        // "returned" maps to FAILED in the status mapper: the parcel WAS collected and is coming
        // back. Not CONFIRMED — the goods are in flight, and whether to refund before they arrive
        // is a decision, not a default.
        CancelResult r = QuiqupCancelService.interpretVerification(false,
                get(200, "{\"state\":\"returned\"}"));
        assertEquals(Outcome.NEEDS_HUMAN, r.outcome());
    }

    // ── Where the state hides in the payload ─────────────────────────────────

    @Test
    void findsTheStateWhereverThisEndpointNestsIt() {
        assertEquals("cancelled", QuiqupCancelService.extractState(
                get(200, "{\"state\":\"cancelled\"}")));
        assertEquals("cancelled", QuiqupCancelService.extractState(
                get(200, "{\"order\":{\"state\":\"cancelled\"}}")));
        assertEquals("cancelled", QuiqupCancelService.extractState(
                get(200, "{\"data\":{\"status\":\"cancelled\"}}")));
        assertNull(QuiqupCancelService.extractState(
                get(200, "{\"order\":{\"id\":1}}")));
        assertNull(QuiqupCancelService.extractState(
                new QuiqupApiResult(200, true, "plain text body")));
    }

    // ── The gate itself ──────────────────────────────────────────────────────

    @Test
    void onlyAVerifiedStopOrNoJobReleasesMoney() {
        for (Outcome o : Outcome.values()) {
            boolean allowed = new CancelResult(o, "").refundAllowed();
            if (o == Outcome.CONFIRMED || o == Outcome.NOTHING_TO_CANCEL) {
                assertTrue(allowed, o + " must release the refund");
            } else {
                assertFalse(allowed, o + " must hold the refund — the parcel may be moving");
            }
        }
    }
}
