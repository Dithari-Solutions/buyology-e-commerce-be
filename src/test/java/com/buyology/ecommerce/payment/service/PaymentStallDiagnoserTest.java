package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.dto.PaymentStallDiagnosis;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins what an order awaiting payment is actually told about itself.
 *
 * <p>The status alone is the same word for four different situations, and the wrong branch here
 * does not fail loudly — it emails a customer the wrong thing. The case that matters most is an
 * instalment payment awaiting settlement: Tabby and Tamara approve the shopper before Paymob
 * confirms, so the money is in and chasing them would be both wrong and alarming. That is asserted
 * first because it is the mistake with a real cost.
 */
class PaymentStallDiagnoserTest {

    private final PaymentStallDiagnoser diagnoser = new PaymentStallDiagnoser();

    private static PaymentTransaction tx(PaymentStatus status, PaymentMethodType method,
                                         String intentionId, String failureCode, Instant createdAt) {
        PaymentTransaction t = new PaymentTransaction();
        t.setStatus(status);
        t.setMethodType(method);
        t.setIntentionId(intentionId);
        t.setFailureCode(failureCode);
        // createdAt is @PrePersist-only, and "which attempt is the latest" is the whole ordering.
        // Setting it explicitly keeps the test from leaning on input order by accident.
        ReflectionTestUtils.setField(t, "createdAt", createdAt);
        return t;
    }

    @Test
    void instalmentAwaitingSettlementCountsAsPaidAndIsNeverChased() {
        for (PaymentMethodType bnpl : List.of(PaymentMethodType.TABBY, PaymentMethodType.TAMARA)) {
            PaymentStallDiagnosis d = diagnoser.diagnose(List.of(
                    tx(PaymentStatus.PROCESSING, bnpl, "int-1", null, Instant.parse("2026-08-26T10:00:00Z"))));

            assertEquals("INSTALMENT_AWAITING_SETTLEMENT", d.code(), bnpl.name());
            assertTrue(d.customerHasPaid(), bnpl + ": the money is in — this must never read as unpaid");
            assertFalse(d.contactRecommended(), bnpl + ": chasing a customer who paid is the whole bug");
            assertNull(d.suggestedMessage(), bnpl + ": there is no right message to send here");
        }
    }

    @Test
    void cardStuckOnBankVerificationIsNotPaidAndIsWorthContacting() {
        PaymentStallDiagnosis d = diagnoser.diagnose(List.of(
                tx(PaymentStatus.PROCESSING, PaymentMethodType.CARD, "int-1", null,
                        Instant.parse("2026-08-26T10:00:00Z"))));

        assertEquals("VERIFICATION_NOT_COMPLETED", d.code());
        assertFalse(d.customerHasPaid());
        assertTrue(d.contactRecommended());
        assertNotNull(d.suggestedMessage());
    }

    @Test
    void declineCodeBecomesTheReasonRatherThanTheGatewayProse() {
        PaymentStallDiagnosis d = diagnoser.diagnose(List.of(
                tx(PaymentStatus.FAILED, PaymentMethodType.CARD, "int-1", "51",
                        Instant.parse("2026-08-26T10:00:00Z"))));

        assertEquals("DECLINED", d.code());
        assertTrue(d.summary().contains("Insufficient funds"), d.summary());
        assertTrue(d.summary().contains("51"), "the admin needs the raw code too: " + d.summary());
        assertFalse(d.customerHasPaid());
    }

    @Test
    void unpaddedDeclineCodeStillResolves() {
        PaymentStallDiagnosis d = diagnoser.diagnose(List.of(
                tx(PaymentStatus.FAILED, PaymentMethodType.CARD, "int-1", "5",
                        Instant.parse("2026-08-26T10:00:00Z"))));

        assertTrue(d.summary().contains("without a stated reason"), d.summary());
    }

    @Test
    void unknownDeclineCodeFallsBackWithoutInventingAReason() {
        PaymentTransaction t = tx(PaymentStatus.FAILED, PaymentMethodType.CARD, "int-1", "ZZ9",
                Instant.parse("2026-08-26T10:00:00Z"));
        t.setFailureReason("Acquirer said no");

        PaymentStallDiagnosis d = diagnoser.diagnose(List.of(t));

        assertEquals("DECLINED", d.code());
        assertTrue(d.summary().contains("Acquirer said no"), d.summary());
    }

    @Test
    void neverReachingTheGatewayIsReportedAsOurProblem() {
        PaymentStallDiagnosis d = diagnoser.diagnose(List.of(
                tx(PaymentStatus.PENDING, PaymentMethodType.CARD, null, null,
                        Instant.parse("2026-08-26T10:00:00Z"))));

        assertEquals("GATEWAY_UNREACHABLE", d.code());
        assertTrue(d.summary().contains("our side"), d.summary());
    }

    @Test
    void reachingThePaymentPageAndLeavingIsAbandonment() {
        PaymentStallDiagnosis d = diagnoser.diagnose(List.of(
                tx(PaymentStatus.PENDING, PaymentMethodType.CARD, "int-1", null,
                        Instant.parse("2026-08-26T10:00:00Z"))));

        assertEquals("ABANDONED_AT_GATEWAY", d.code());
        assertFalse(d.customerHasPaid());
    }

    @Test
    void anySuccessAnywhereWinsOverAnEarlierFailure() {
        PaymentStallDiagnosis d = diagnoser.diagnose(List.of(
                tx(PaymentStatus.FAILED, PaymentMethodType.CARD, "int-1", "51",
                        Instant.parse("2026-08-26T10:00:00Z")),
                tx(PaymentStatus.SUCCESS, PaymentMethodType.TABBY, "int-2", null,
                        Instant.parse("2026-08-26T11:00:00Z"))));

        assertEquals("PAID", d.code());
        assertTrue(d.customerHasPaid());
        assertEquals(2, d.attemptCount(), "the struggle stays visible after they pay");
    }

    @Test
    void repeatedDeclinesAcrossMethodsAskForAPersonInsteadOfAnotherEmail() {
        PaymentStallDiagnosis d = diagnoser.diagnose(List.of(
                tx(PaymentStatus.FAILED, PaymentMethodType.CARD, "int-1", "51",
                        Instant.parse("2026-08-26T10:00:00Z")),
                tx(PaymentStatus.FAILED, PaymentMethodType.TABBY, "int-2", "05",
                        Instant.parse("2026-08-26T11:00:00Z"))));

        assertEquals("REPEATEDLY_DECLINED", d.code());
        assertTrue(d.summary().contains("needs a person"), d.summary());
        assertEquals(List.of("TABBY", "CARD"), d.methodsTried(), "newest attempt first");
    }

    @Test
    void latestAttemptDecidesTheDiagnosisRegardlessOfInputOrder() {
        // Oldest listed last on purpose: ordering must come from the timestamps, not the caller.
        PaymentStallDiagnosis d = diagnoser.diagnose(List.of(
                tx(PaymentStatus.FAILED, PaymentMethodType.CARD, "int-1", "51",
                        Instant.parse("2026-08-26T09:00:00Z")),
                tx(PaymentStatus.PROCESSING, PaymentMethodType.TABBY, "int-2", null,
                        Instant.parse("2026-08-26T12:00:00Z"))));

        assertEquals("INSTALMENT_AWAITING_SETTLEMENT", d.code());
        assertTrue(d.customerHasPaid());
    }

    @Test
    void noAttemptAtAllIsItsOwnReason() {
        PaymentStallDiagnosis d = diagnoser.diagnose(List.of());

        assertEquals("NEVER_STARTED", d.code());
        assertEquals(0, d.attemptCount());
        assertFalse(d.customerHasPaid());
    }
}
