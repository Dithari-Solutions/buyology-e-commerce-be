package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.event.OrderPaidEvent;
import com.buyology.ecommerce.order.event.PaymentSucceededEvent;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.buyology.ecommerce.payment.service.PaidAmountPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentSettlementRecoveryTest {
    private final OrderService service;
    private final Object[] dependencies;
    private final EntityManager em = mock(EntityManager.class);
    private final Order order = new Order();
    private final PaymentTransaction payment = new PaymentTransaction();

    PaymentSettlementRecoveryTest() throws Exception {
        Constructor<?> ctor = OrderService.class.getDeclaredConstructors()[0];
        Class<?>[] types = ctor.getParameterTypes();
        dependencies = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            dependencies[i] = types[i] == ObjectMapper.class ? new ObjectMapper()
                    : types[i].isPrimitive() ? java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(types[i], 1), 0)
                    : mock(types[i]);
        }
        service = (OrderService) ctor.newInstance(dependencies);
        ReflectionTestUtils.setField(service, "entityManager", em);
        order.setId(UUID.randomUUID()); order.setStatus(OrderStatus.PENDING_PAYMENT);
        ReflectionTestUtils.setField(order, "createdAt", Instant.now().minusSeconds(3600));
        payment.setId(UUID.randomUUID()); payment.setAppOrderId(order.getId()); payment.setStatus(PaymentStatus.SUCCESS);
        // No profile or phone lookup is provided: historical captured money must still settle.
        when(dependency(OrderRepository.class).findById(order.getId())).thenReturn(Optional.of(order));
        when(dependency(PaymentTransactionRepository.class).findById(payment.getId())).thenReturn(Optional.of(payment));
        when(dependency(PaidAmountPolicy.class).covers(order, payment)).thenReturn(true);
    }

    private <T> T dependency(Class<T> type) {
        for (Object value : dependencies) if (type.isInstance(value)) return type.cast(value);
        throw new IllegalArgumentException(type.getName());
    }

    private long paidEvents() {
        return mockingDetails(dependency(ApplicationEventPublisher.class)).getInvocations().stream()
                .filter(i -> i.getArguments().length == 1 && i.getArguments()[0] instanceof OrderPaidEvent).count();
    }

    @Test
    void retryingLostOrderSettlementRunsPaidEffectsOnceWithoutRequiringPhone() {
        service.onPaymentSucceeded(new PaymentSucceededEvent(order.getId(), payment.getId()));
        service.onPaymentSucceeded(new PaymentSucceededEvent(order.getId(), payment.getId()));
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(payment.getId(), order.getPaymentTransactionId());
        assertEquals(1, paidEvents());
        verify(em, times(2)).refresh(order, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void automaticRecoveryRunsTheFullPaidOrderPath() {
        when(dependency(OrderRepository.class).findPendingWithSuccessfulPayment(any(), any())).thenReturn(List.of(order));
        when(dependency(PaymentTransactionRepository.class).findFirstByAppOrderIdAndStatusIn(eq(order.getId()), anyList()))
                .thenReturn(Optional.of(payment));
        service.reconcileStuckPayments();
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(1, paidEvents(), "Recovered orders must reach the ERP/fulfilment paid-event path too");
    }
}
