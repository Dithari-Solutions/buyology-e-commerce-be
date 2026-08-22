package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.membership.service.CreditReturnService;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.payment.service.PaymentService;
import com.buyology.ecommerce.promo.service.PromoCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pins the courier gate on a cancellation's money.
 *
 * <p>{@code refundAllowed=false} means the order's Quiqup job could not be verified as stopped: the
 * parcel may be moving toward the customer right now. Everything of VALUE — the gateway refund, the
 * B2B credit return, and the "your money is coming" emails — must be unreachable in that state,
 * because paying it out while the parcel arrives is the double loss (goods AND money) this whole
 * mechanism exists to prevent. The promo release is deliberately NOT gated: a reservation is
 * bookkeeping, not value handed to the customer.
 */
class CancellationRefundGateTest {

    private final Object[] mocks;
    private final OrderService service;

    private final CreditReturnService creditReturnService;
    private final PromoCodeService promoCodeService;
    private final EmailService emailService;
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PaymentService> paymentServiceProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    CancellationRefundGateTest() {
        // Every constructor dependency mocked by type — the method under test touches only a
        // handful, and Mockito's defaults (empty Optional, empty List) keep the rest inert.
        Constructor<?> ctor = OrderService.class.getDeclaredConstructors()[0];
        Class<?>[] types = ctor.getParameterTypes();
        mocks = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            mocks[i] = mock(types[i]);
        }
        try {
            service = (OrderService) ctor.newInstance(mocks);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        creditReturnService = firstOfType(CreditReturnService.class);
        promoCodeService = firstOfType(PromoCodeService.class);
        emailService = firstOfType(EmailService.class);
        // Swap the provider mock in place of whichever positional ObjectProvider carries
        // PaymentService — providers are erased, so identify by re-injecting through the field.
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "paymentServiceProvider", paymentServiceProvider);
    }

    @SuppressWarnings("unchecked")
    private <T> T firstOfType(Class<T> type) {
        for (Object m : mocks) {
            if (type.isInstance(m)) {
                return (T) m;
            }
        }
        throw new IllegalStateException("no ctor param of type " + type);
    }

    private static Order cancelledDispatchedOrder() {
        Order o = new Order();
        o.setId(UUID.fromString("3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607"));
        o.setCreditApplied(new BigDecimal("20000.00"));
        o.setPaymentTransactionId(UUID.randomUUID());
        o.setQuiqupOrderId("26012997");
        o.setQuiqupCancelStatus("UNCONFIRMED");
        return o;
    }

    // ── The gate holds the money ─────────────────────────────────────────────

    @Test
    void anUnconfirmedCourierHoldsEverythingOfValue() {
        service.applyCancellationSideEffects(cancelledDispatchedOrder(), "changed my mind", false);

        // No refund: the provider is never even asked for the PaymentService.
        verify(paymentServiceProvider, never()).getObject();
        // No B2B credit: same loss in a different currency.
        verifyNoInteractions(creditReturnService);
        // No emails: the customer must not be told money is coming while a courier may be
        // delivering their parcel.
        verifyNoInteractions(emailService);
    }

    @Test
    void theGateStillReleasesThePromoReservation() {
        // A held reservation is pure bookkeeping — releasing it costs nothing and the stale-order
        // and payment-failed paths depend on the release always firing.
        service.applyCancellationSideEffects(cancelledDispatchedOrder(), "changed my mind", false);

        verify(promoCodeService).releaseReservationIndependently(
                UUID.fromString("3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607"));
    }

    // ── And opens for the safe cases ─────────────────────────────────────────

    @Test
    void aConfirmedStopReleasesTheCreditLeg() {
        Order order = cancelledDispatchedOrder();
        order.setQuiqupCancelStatus("CONFIRMED");

        service.applyCancellationSideEffects(order, "changed my mind", true);

        verify(creditReturnService).returnForCancelledOrder(order.getId(), order.getCreditApplied());
    }

    @Test
    void anUndispatchedOrderIsNeverGated() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreditApplied(new BigDecimal("500.00"));

        service.applyCancellationSideEffects(order, "cancelled before dispatch", true);

        verify(creditReturnService).returnForCancelledOrder(order.getId(), order.getCreditApplied());
    }
}
