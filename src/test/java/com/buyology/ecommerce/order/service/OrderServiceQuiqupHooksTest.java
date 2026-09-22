package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.dto.AdminStatusUpdateRequest;
import com.buyology.ecommerce.order.event.OrderPackagingEvent;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.quiqup.service.QuiqupCancelService;
import com.buyology.ecommerce.quiqup.service.QuiqupCancelService.CancelResult;
import com.buyology.ecommerce.quiqup.service.QuiqupCancelService.Outcome;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins the order-side hooks of the Quiqup integration: what summons a courier, what stops one, and
 * the manual way back when stopping one did not work.
 *
 * <p>Built like {@link CancellationRefundGateTest}: every constructor dependency mocked by type.
 * No transaction is active, so the after-commit work runs inline and can be observed.
 */
class OrderServiceQuiqupHooksTest {

    private static final UUID ORDER_ID = UUID.fromString("0dd0dd00-0000-4000-8000-000000000001");

    private final Object[] mocks;
    private final OrderService service;
    private final OrderRepository orderRepo;
    private final QuiqupCancelService cancelService;
    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;
    private final OrderService self = mock(OrderService.class);
    private final Order order = new Order();

    @SuppressWarnings("unchecked")
    OrderServiceQuiqupHooksTest() {
        Constructor<?> ctor = OrderService.class.getDeclaredConstructors()[0];
        Class<?>[] types = ctor.getParameterTypes();
        mocks = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            mocks[i] = types[i].isPrimitive()
                    ? java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(types[i], 1), 0)
                    : mock(types[i]);
        }
        try {
            service = (OrderService) ctor.newInstance(mocks);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        orderRepo = firstOfType(OrderRepository.class);
        cancelService = firstOfType(QuiqupCancelService.class);
        events = firstOfType(ApplicationEventPublisher.class);
        messaging = firstOfType(SimpMessagingTemplate.class);

        ObjectProvider<OrderService> selfProvider = mock(ObjectProvider.class);
        when(selfProvider.getObject()).thenReturn(self);
        ReflectionTestUtils.setField(service, "selfProvider", selfProvider);

        order.setId(ORDER_ID);
        order.setUserId(UUID.randomUUID());
        order.setDeliveryMethod(DeliveryMethod.REGULAR);
        order.setCurrency("AED");
        order.setSubtotal(new BigDecimal("105.50"));
        order.setTotalAmount(new BigDecimal("105.50"));
        when(orderRepo.findById(ORDER_ID)).thenAnswer(inv -> Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private <T> T firstOfType(Class<T> type) {
        for (Object m : mocks) {
            if (type.isInstance(m)) return (T) m;
        }
        throw new IllegalStateException("no ctor param of type " + type);
    }

    private static AdminStatusUpdateRequest to(OrderStatus status) {
        AdminStatusUpdateRequest req = new AdminStatusUpdateRequest();
        req.setStatus(status);
        if (status == OrderStatus.CANCELLED) req.setCancellationReason("customer asked");
        return req;
    }

    // ── Summoning ────────────────────────────────────────────────────────────

    @Test
    void movingToPackagingAnnouncesIt() {
        order.setStatus(OrderStatus.PAID);

        service.adminUpdateStatus(ORDER_ID, UUID.randomUUID(), to(OrderStatus.PACKAGING));

        verify(events).publishEvent(any(OrderPackagingEvent.class));
    }

    @Test
    void otherMovesAnnounceNothing() {
        order.setStatus(OrderStatus.PACKAGING);
        order.setQuiqupOrderId("998877");
        order.setQuiqupReleasedAt(java.time.Instant.now());

        service.adminUpdateStatus(ORDER_ID, UUID.randomUUID(), to(OrderStatus.IN_COURIER));

        verify(events, never()).publishEvent(any(OrderPackagingEvent.class));
    }

    // ── Stopping ─────────────────────────────────────────────────────────────

    @Test
    void theCourierIsStoppedBeforeAnythingElseRunsAfterTheCancel() {
        // One exception ends the after-commit callback; a broadcast failing first used to be
        // enough to leave the courier driving.
        order.setStatus(OrderStatus.PACKAGING);
        order.setQuiqupOrderId("998877");
        when(cancelService.cancelForOrder(eq(ORDER_ID), anyString()))
                .thenReturn(new CancelResult(Outcome.CONFIRMED, "job reads as 'cancelled'"));

        service.adminUpdateStatus(ORDER_ID, UUID.randomUUID(), to(OrderStatus.CANCELLED));

        InOrder inOrder = inOrder(cancelService, self, messaging);
        inOrder.verify(cancelService).cancelForOrder(eq(ORDER_ID), anyString());
        inOrder.verify(self).applyCancellationSideEffects(any(Order.class), anyString(), eq(true));
        inOrder.verify(messaging).convertAndSend(anyString(), any(Object.class));
        assertEquals("PENDING", order.getQuiqupCancelStatus(), "the intent is written with the cancel");
    }

    @Test
    void aCancelThatThrowsStillTellsTheCustomer() {
        // The PENDING intent is committed, so the retry job finishes the courier cancel; nothing
        // would re-send the customer's notification or the live status update.
        order.setStatus(OrderStatus.PACKAGING);
        order.setQuiqupOrderId("998877");
        when(cancelService.cancelForOrder(eq(ORDER_ID), anyString()))
                .thenThrow(new IllegalStateException("database unavailable"));

        service.adminUpdateStatus(ORDER_ID, UUID.randomUUID(), to(OrderStatus.CANCELLED));

        verify(messaging).convertAndSend(anyString(), any(Object.class));
    }

    // ── The manual way back ──────────────────────────────────────────────────

    @Test
    void aRetryOnAnOrderThatIsNotCancelledIsRefused() {
        order.setStatus(OrderStatus.PACKAGING);
        order.setQuiqupOrderId("998877");

        assertThrows(IllegalStateException.class, () -> service.retryQuiqupCancel(ORDER_ID));
        verifyNoInteractions(cancelService);
    }

    @Test
    void aRetryThatConfirmsReleasesWhatTheCancelHeld() {
        order.setStatus(OrderStatus.CANCELLED);
        order.setQuiqupOrderId("998877");
        order.setCancellationReason("customer asked");
        when(cancelService.rearm(ORDER_ID)).thenReturn(true);
        when(cancelService.cancelForOrder(eq(ORDER_ID), any()))
                .thenReturn(new CancelResult(Outcome.CONFIRMED, "job reads as 'cancelled'"));

        CancelResult result = service.retryQuiqupCancel(ORDER_ID);

        assertEquals(Outcome.CONFIRMED, result.outcome());
        InOrder inOrder = inOrder(cancelService, self);
        inOrder.verify(cancelService).rearm(ORDER_ID);
        inOrder.verify(cancelService).cancelForOrder(ORDER_ID, "customer asked");
        inOrder.verify(self).applyCancellationSideEffects(order, "customer asked", true);
    }

    @Test
    void aRetryThatStillFailsReleasesNothing() {
        order.setStatus(OrderStatus.CANCELLED);
        order.setQuiqupOrderId("998877");
        when(cancelService.rearm(ORDER_ID)).thenReturn(true);
        when(cancelService.cancelForOrder(eq(ORDER_ID), any()))
                .thenReturn(new CancelResult(Outcome.NEEDS_HUMAN, "cancel refused"));

        service.retryQuiqupCancel(ORDER_ID);

        verify(self, never()).applyCancellationSideEffects(any(), any(), anyBoolean());
    }

    @Test
    void aStopConfirmedBySomeoneElseMeanwhileIsNotReleasedTwice() {
        // The retry job confirmed it between our re-arm and our call; it released the money.
        order.setStatus(OrderStatus.CANCELLED);
        order.setQuiqupOrderId("998877");
        when(cancelService.rearm(ORDER_ID)).thenReturn(true);
        when(cancelService.cancelForOrder(eq(ORDER_ID), any()))
                .thenReturn(new CancelResult(Outcome.CONFIRMED, QuiqupCancelService.ALREADY_CONFIRMED));

        service.retryQuiqupCancel(ORDER_ID);

        verify(self, never()).applyCancellationSideEffects(any(), any(), anyBoolean());
    }

    @Test
    void anOrderAlreadyConfirmedIsReportedNotReleasedAgain() {
        order.setStatus(OrderStatus.CANCELLED);
        order.setQuiqupOrderId("998877");
        when(cancelService.rearm(ORDER_ID)).thenReturn(false);
        when(cancelService.cancelForOrder(eq(ORDER_ID), any()))
                .thenReturn(new CancelResult(Outcome.CONFIRMED, QuiqupCancelService.ALREADY_CONFIRMED));

        assertEquals(Outcome.CONFIRMED, service.retryQuiqupCancel(ORDER_ID).outcome());
        verify(self, never()).applyCancellationSideEffects(any(), any(), anyBoolean());
    }

    @Test
    void aJobCreatedAfterTheCancelIsStoppedWithoutRefundingAgain() {
        // The cancel found no job and already refunded and emailed; only the late job needs stopping.
        java.time.Instant cancelledAt = java.time.Instant.parse("2026-09-22T09:00:00Z");
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(cancelledAt);
        order.setQuiqupOrderId("998877");
        order.setQuiqupDispatchedAt(cancelledAt.plusSeconds(2));
        when(cancelService.rearm(ORDER_ID)).thenReturn(true);
        when(cancelService.cancelForOrder(eq(ORDER_ID), any()))
                .thenReturn(new CancelResult(Outcome.CONFIRMED, "job reads as 'cancelled'"));

        service.retryQuiqupCancel(ORDER_ID);

        verify(self, never()).applyCancellationSideEffects(any(), any(), anyBoolean());
    }
}
