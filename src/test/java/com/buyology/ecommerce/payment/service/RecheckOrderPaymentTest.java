package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.order.event.PaymentFailedEvent;
import com.buyology.ecommerce.order.event.PaymentSucceededEvent;
import com.buyology.ecommerce.payment.domain.PaymentProvider;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.enums.PaymentPurpose;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.exception.PaymentGatewayException;
import com.buyology.ecommerce.payment.repository.PaymentProviderRepository;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Pins the settlement of a payment that no Paymob callback ever reached us about — the Tabby and
 * Tamara case.
 *
 * <p>Paymob showed these payments as paid while the order sat in PENDING_PAYMENT. No callback had
 * arrived, so we never learned Paymob's transaction id, and the automatic sweep asked Paymob by our
 * own reference only, which found nothing. Staff typed the id in by hand. The sweep now asks by
 * Paymob's own order id, which we store for every attempt.
 */
class RecheckOrderPaymentTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID ORDER = UUID.fromString("0a0b0c0d-0000-4000-8000-000000000001");
    private static final long PAYMOB_ORDER = 412345678L;

    private final Object[] mocks;
    private final PaymentService service;
    private final PaymentTransactionRepository txRepo;
    private final PaymobClient paymob;
    private final ApplicationEventPublisher events;
    private final EntityManager em = mock(EntityManager.class);
    private final List<PaymentTransaction> attempts = new ArrayList<>();

    RecheckOrderPaymentTest() {
        Constructor<?> ctor = PaymentService.class.getDeclaredConstructors()[0];
        Class<?>[] types = ctor.getParameterTypes();
        mocks = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            mocks[i] = types[i].isPrimitive()
                    ? java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(types[i], 1), 0)
                    : types[i] == ObjectMapper.class ? JSON : mock(types[i]);
        }
        try {
            service = (PaymentService) ctor.newInstance(mocks);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        ReflectionTestUtils.setField(service, "entityManager", em);
        txRepo = firstOfType(PaymentTransactionRepository.class);
        paymob = firstOfType(PaymobClient.class);
        events = firstOfType(ApplicationEventPublisher.class);

        PaymentProvider provider = new PaymentProvider();
        provider.setApiKey("api-key");
        provider.setSecretKey("secret");
        provider.setBaseUrl("https://uae.paymob.com");
        when(firstOfType(PaymentProviderRepository.class).findFirstByIsActiveTrue()).thenReturn(Optional.of(provider));
        when(txRepo.findAllByAppOrderId(ORDER)).thenReturn(attempts);
        when(txRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private <T> T firstOfType(Class<T> type) {
        for (Object m : mocks) {
            if (type.isInstance(m)) return (T) m;
        }
        throw new IllegalStateException("no ctor param of type " + type);
    }

    private PaymentTransaction attempt(PaymentMethodType method, Long paymobOrderId, Instant createdAt) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID());
        tx.setMerchantOrderId(tx.getId().toString());
        tx.setAppOrderId(ORDER);
        tx.setPurpose(PaymentPurpose.ORDER);
        tx.setMethodType(method);
        tx.setStatus(PaymentStatus.PENDING);
        tx.setAmountCents(1_899_00L);
        tx.setCurrency("AED");
        tx.setPaymobOrderId(paymobOrderId);
        ReflectionTestUtils.setField(tx, "createdAt", createdAt);
        attempts.add(tx);
        return tx;
    }

    private static JsonNode paymob(long txnId, long orderId, String moid, boolean success, boolean pending,
                                   long cents) throws Exception {
        return JSON.readTree("{\"id\":" + txnId + ",\"success\":" + success + ",\"pending\":" + pending
                + ",\"amount_cents\":" + cents + ",\"currency\":\"AED\",\"is_refunded\":false,\"is_voided\":false,"
                + "\"order\":{\"id\":" + orderId + (moid == null ? "" : ",\"merchant_order_id\":\"" + moid + "\"")
                + "}}");
    }

    private long publishedSuccesses() {
        return mockingDetails(events).getInvocations().stream()
                .filter(i -> i.getArguments().length == 1 && i.getArguments()[0] instanceof PaymentSucceededEvent)
                .count();
    }

    // ── The case that was stuck ──────────────────────────────────────────────

    @Test
    void aTabbyPaymentNoCallbackReachedIsFoundByPaymobsOrderIdAndSettled() throws Exception {
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        // Paymob never stored our reference (moid null) — only its own order id ties it to us.
        when(paymob.inquireTransaction("api-key", "https://uae.paymob.com", PAYMOB_ORDER, tabby.getMerchantOrderId()))
                .thenReturn(paymob(31723298L, PAYMOB_ORDER, null, true, false, 1_899_00L));

        PaymentService.RecheckResult result = service.recheckOrderPayment(ORDER, null);

        assertTrue(result.settled(), result.message());
        assertEquals(PaymentStatus.SUCCESS, tabby.getStatus());
        assertEquals(31723298L, tabby.getPaymobTransactionId());
        assertEquals(1, publishedSuccesses(), "the order is marked paid exactly once");
        verify(em).refresh(tabby, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void anAttemptWithNoPaymobOrderIdFallsBackToOurReference() throws Exception {
        PaymentTransaction tamara = attempt(PaymentMethodType.TAMARA, null, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), isNull(), eq(tamara.getMerchantOrderId())))
                .thenReturn(paymob(555L, 9L, tamara.getMerchantOrderId(), true, false, 1_899_00L));

        assertTrue(service.recheckOrderPayment(ORDER, null).settled());
    }

    @Test
    void ourReferenceIsOnlyAskedWhenPaymobsOrderIdIsUnknown() {
        // Asking by our reference on top of the order id found nothing and doubled every lookup.
        attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString())).thenReturn(null);

        PaymentService.RecheckResult result = service.recheckOrderPayment(ORDER, null);

        assertFalse(result.settled());
        assertFalse(result.gatewayUnreachable(), "an ordinary abandoned checkout");
        verify(paymob, never()).inquireTransaction(anyString(), anyString(), isNull(), anyString());
    }

    @Test
    void aFailedLookupIsReportedEvenWhenAnOlderAttemptWasSimplyAbandoned() {
        attempt(PaymentMethodType.CARD, 222L, Instant.now().minusSeconds(900));
        attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString()))
                .thenThrow(new PaymentGatewayException("Payment provider rejected the request (503)", null));
        when(paymob.inquireTransaction(anyString(), anyString(), eq(222L), anyString())).thenReturn(null);

        assertTrue(service.recheckOrderPayment(ORDER, null).gatewayUnreachable(),
                "the older attempt's 'not found' must not hide that Paymob could not be asked");
    }

    @Test
    void aPaymentAlreadyRecordedAsPendingIsNotRewritten() throws Exception {
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        tabby.setStatus(PaymentStatus.PROCESSING);
        tabby.setPaymobTransactionId(100L);
        when(paymob.getTransaction(anyString(), anyString(), anyString(), eq("100")))
                .thenReturn(paymob(100L, PAYMOB_ORDER, null, false, true, 1_899_00L));

        service.recheckOrderPayment(ORDER, null);

        verify(em, never()).refresh(any(), any(LockModeType.class));
        verify(txRepo, never()).saveAndFlush(any());
    }

    @Test
    void thePaidAttemptSettlesEvenWhenANewerOneWasAbandoned() throws Exception {
        // A Tabby payment, then the customer reloaded and opened a second attempt they never paid.
        PaymentTransaction paid = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now().minusSeconds(600));
        attempt(PaymentMethodType.CARD, 999L, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), eq(999L), anyString())).thenReturn(null);
        when(paymob.inquireTransaction(anyString(), anyString(), isNull(), anyString())).thenReturn(null);
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString()))
                .thenReturn(paymob(31723298L, PAYMOB_ORDER, null, true, false, 1_899_00L));

        assertTrue(service.recheckOrderPayment(ORDER, null).settled());
        assertEquals(PaymentStatus.SUCCESS, paid.getStatus());
    }

    @Test
    void aPendingApprovalIsRecordedWithItsIdAndPublishesNothing() throws Exception {
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString()))
                .thenReturn(paymob(31723298L, PAYMOB_ORDER, null, false, true, 1_899_00L));

        PaymentService.RecheckResult result = service.recheckOrderPayment(ORDER, null);

        assertFalse(result.settled());
        assertEquals(PaymentStatus.PROCESSING, tabby.getStatus());
        assertEquals(31723298L, tabby.getPaymobTransactionId(), "next time the id is asked about directly");
        verifyNoInteractions(events);
    }

    @Test
    void aStoredPendingIdIsCheckedAgainstTheOrderToo() throws Exception {
        // The instalment captured as a separate transaction under the same Paymob order.
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        tabby.setStatus(PaymentStatus.PROCESSING);
        tabby.setPaymobTransactionId(100L);
        when(paymob.getTransaction("secret", "api-key", "https://uae.paymob.com", "100"))
                .thenReturn(paymob(100L, PAYMOB_ORDER, null, false, true, 1_899_00L));
        when(paymob.inquireTransaction("api-key", "https://uae.paymob.com", PAYMOB_ORDER, null))
                .thenReturn(paymob(101L, PAYMOB_ORDER, null, true, false, 1_899_00L));

        assertTrue(service.recheckOrderPayment(ORDER, null).settled());
        assertEquals(101L, tabby.getPaymobTransactionId());
    }

    // ── What it must never do on its own ─────────────────────────────────────

    @Test
    void anUntypedRecheckNeverFailsAnAttempt() throws Exception {
        // Failing an order releases its promo code and ends it; only an admin naming the exact
        // transaction may do that.
        PaymentTransaction card = attempt(PaymentMethodType.CARD, PAYMOB_ORDER, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString()))
                .thenReturn(paymob(7L, PAYMOB_ORDER, null, false, false, 1_899_00L));

        assertFalse(service.recheckOrderPayment(ORDER, null).settled());
        assertEquals(PaymentStatus.PENDING, card.getStatus());
        verifyNoInteractions(events);
    }

    @Test
    void aPaymentForADifferentAmountIsNotSettledNorFailed() throws Exception {
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString()))
                .thenReturn(paymob(8L, PAYMOB_ORDER, null, true, false, 100L));

        assertFalse(service.recheckOrderPayment(ORDER, null).settled());
        assertEquals(PaymentStatus.PENDING, tabby.getStatus());
        verifyNoInteractions(events);
    }

    @Test
    void aReplyNamingAnotherOrderIsRefused() throws Exception {
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString()))
                .thenReturn(paymob(9L, 777L, "someone-elses-reference", true, false, 1_899_00L));

        PaymentService.RecheckResult result = service.recheckOrderPayment(ORDER, null);

        assertFalse(result.settled());
        assertTrue(result.message().contains("different order"), result.message());
        assertEquals(PaymentStatus.PENDING, tabby.getStatus());
    }

    @Test
    void aRefundedPaymentIsNeverGroundsForMarkingAnOrderPaid() throws Exception {
        attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        JsonNode refunded = JSON.readTree("{\"id\":10,\"success\":true,\"pending\":false,\"amount_cents\":189900,"
                + "\"currency\":\"AED\",\"is_refunded\":true,\"order\":{\"id\":" + PAYMOB_ORDER + "}}");
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString())).thenReturn(refunded);

        assertFalse(service.recheckOrderPayment(ORDER, null).settled());
        verifyNoInteractions(events);
    }

    @Test
    void aLookupThatFailsSaysSoInsteadOfCallingTheCheckoutAbandoned() {
        attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString()))
                .thenThrow(new PaymentGatewayException("Payment provider rejected the request (401)", null));

        PaymentService.RecheckResult result = service.recheckOrderPayment(ORDER, null);

        assertFalse(result.settled());
        assertTrue(result.gatewayUnreachable(), "the sweep logs this loudly");
        assertTrue(result.message().contains("did not answer"), result.message());
    }

    @Test
    void theOtherReplicaSettlingItFirstMeansNothingIsPublishedTwice() throws Exception {
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        when(paymob.inquireTransaction(anyString(), anyString(), eq(PAYMOB_ORDER), anyString()))
                .thenReturn(paymob(31723298L, PAYMOB_ORDER, null, true, false, 1_899_00L));
        // By the time we hold the row lock, the webhook (or the other replica) has settled it.
        doAnswer(inv -> {
            tabby.setStatus(PaymentStatus.SUCCESS);
            return null;
        }).when(em).refresh(tabby, LockModeType.PESSIMISTIC_WRITE);

        assertTrue(service.recheckOrderPayment(ORDER, null).settled());
        verifyNoInteractions(events);
    }

    // ── The admin's typed id ─────────────────────────────────────────────────

    @Test
    void aTypedIdIsHonouredEvenWhenAnIdIsStoredAndSettlesTheAttemptItBelongsTo() throws Exception {
        PaymentTransaction card = attempt(PaymentMethodType.CARD, 111L, Instant.now());
        card.setPaymobTransactionId(5L);
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now().minusSeconds(60));
        when(paymob.getTransaction(anyString(), anyString(), anyString(), eq("31723298")))
                .thenReturn(paymob(31723298L, PAYMOB_ORDER, null, true, false, 1_899_00L));

        assertTrue(service.recheckOrderPayment(ORDER, " 31723298 ").settled());
        assertEquals(PaymentStatus.SUCCESS, tabby.getStatus());
        assertEquals(PaymentStatus.PENDING, card.getStatus());
    }

    @Test
    void aDeclinedSiblingPastedByHandCannotFailAPaymentThatIsGoingThrough() throws Exception {
        // A card was declined, then Tabby approved under the same Paymob order. Staff paste the
        // declined row off Paymob's order page.
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        tabby.setStatus(PaymentStatus.PROCESSING);
        tabby.setPaymobTransactionId(100L);
        when(paymob.getTransaction(anyString(), anyString(), anyString(), eq("12")))
                .thenReturn(paymob(12L, PAYMOB_ORDER, null, false, false, 1_899_00L));

        PaymentService.RecheckResult result = service.recheckOrderPayment(ORDER, "12");

        assertEquals(PaymentStatus.PROCESSING, tabby.getStatus());
        assertEquals(100L, tabby.getPaymobTransactionId());
        assertTrue(result.message().contains("recorded under transaction 100"), result.message());
        verifyNoInteractions(events);
    }

    @Test
    void aDeclineWithAnApprovalUnderTheSameOrderSettlesTheApproval() throws Exception {
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        when(paymob.getTransaction(anyString(), anyString(), anyString(), eq("12")))
                .thenReturn(paymob(12L, PAYMOB_ORDER, null, false, false, 1_899_00L));
        when(paymob.inquireTransaction("api-key", "https://uae.paymob.com", PAYMOB_ORDER, null))
                .thenReturn(paymob(31723298L, PAYMOB_ORDER, null, true, false, 1_899_00L));

        assertTrue(service.recheckOrderPayment(ORDER, "12").settled());
        assertEquals(PaymentStatus.SUCCESS, tabby.getStatus());
        assertEquals(31723298L, tabby.getPaymobTransactionId());
    }

    @Test
    void aTypedPaidPaymentForTheWrongAmountIsRefusedNotFailed() throws Exception {
        PaymentTransaction card = attempt(PaymentMethodType.CARD, PAYMOB_ORDER, Instant.now());
        when(paymob.getTransaction(anyString(), anyString(), anyString(), eq("13")))
                .thenReturn(paymob(13L, PAYMOB_ORDER, null, true, false, 500L));

        assertFalse(service.recheckOrderPayment(ORDER, "13").settled());
        assertEquals(PaymentStatus.PENDING, card.getStatus());
        verifyNoInteractions(events);
    }

    @Test
    void aTypedIdThatNamesNoAttemptOfThisOrderIsRefused() throws Exception {
        // Another customer's payment of the same price: the money guard alone would accept it.
        PaymentTransaction card = attempt(PaymentMethodType.CARD, PAYMOB_ORDER, Instant.now());
        when(paymob.getTransaction(anyString(), anyString(), anyString(), eq("14")))
                .thenReturn(paymob(14L, 999_999L, null, true, false, 1_899_00L));

        PaymentService.RecheckResult result = service.recheckOrderPayment(ORDER, "14");

        assertFalse(result.settled());
        assertEquals(PaymentStatus.PENDING, card.getStatus());
        assertTrue(result.message().contains("does not name any payment attempt"), result.message());
    }

    @Test
    void aTypedIdForAFailedPaymentStillFailsIt() throws Exception {
        // An admin naming the exact transaction keeps the full behaviour.
        PaymentTransaction card = attempt(PaymentMethodType.CARD, PAYMOB_ORDER, Instant.now());
        when(paymob.getTransaction(anyString(), anyString(), anyString(), eq("12")))
                .thenReturn(paymob(12L, PAYMOB_ORDER, null, false, false, 1_899_00L));

        service.recheckOrderPayment(ORDER, "12");

        assertEquals(PaymentStatus.FAILED, card.getStatus());
        verify(events).publishEvent(any(PaymentFailedEvent.class));
    }

    @Test
    void anOrderAlreadyPaidIsLeftAlone() {
        PaymentTransaction tabby = attempt(PaymentMethodType.TABBY, PAYMOB_ORDER, Instant.now());
        tabby.setStatus(PaymentStatus.SUCCESS);

        assertTrue(service.recheckOrderPayment(ORDER, null).settled());
        verifyNoInteractions(paymob);
    }
}
