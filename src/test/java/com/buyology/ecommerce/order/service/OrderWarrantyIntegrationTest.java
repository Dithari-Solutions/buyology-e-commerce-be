package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.event.OrderDeliveredEvent;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.warranty.dto.OrderItemWarranty;
import com.buyology.ecommerce.warranty.service.OrderWarrantyReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Where warranties meet orders: delivery raises the event that issues cards, and an order's items
 * carry their serials. Built the way CheckoutCountryTest builds OrderService, every dependency mocked.
 */
class OrderWarrantyIntegrationTest {

    private final Object[] dependencies;
    private final OrderService service;
    private final UUID userId = UUID.randomUUID();

    OrderWarrantyIntegrationTest() throws Exception {
        Constructor<?> ctor = OrderService.class.getDeclaredConstructors()[0];
        dependencies = new Object[ctor.getParameterCount()];
        for (int i = 0; i < dependencies.length; i++) {
            Class<?> type = ctor.getParameterTypes()[i];
            dependencies[i] = type == ObjectMapper.class ? new ObjectMapper()
                    : type.isPrimitive() ? java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0) : mock(type);
        }
        service = (OrderService) ctor.newInstance(dependencies);
    }

    @SuppressWarnings("unchecked")
    private <T> T dependency(Class<T> type) {
        return (T) Arrays.stream(dependencies).filter(type::isInstance).findFirst().orElseThrow();
    }

    private OrderItem item() {
        OrderItem i = new OrderItem();
        i.setId(UUID.randomUUID());
        i.setProductId(UUID.randomUUID());
        i.setProductSku("LAPTOP");
        i.setQuantity(1);
        i.setUnitPrice(new BigDecimal("1000"));
        i.setTotalPrice(new BigDecimal("1000"));
        return i;
    }

    private Order order(OrderStatus status, Instant deliveredAt, OrderItem... items) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setUserId(userId);
        o.setStatus(status);
        o.setDeliveredAt(deliveredAt);
        o.setItems(new ArrayList<>(List.of(items)));
        when(dependency(OrderRepository.class).findByIdAndUserId(o.getId(), userId)).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void aDeliveredOrdersItemsCarryTheirSerials() {
        OrderItem laptop = item();
        OrderItem mouse = item();
        Order order = order(OrderStatus.DELIVERED, Instant.parse("2026-06-24T10:00:00Z"), laptop, mouse);
        OrderItemWarranty card = new OrderItemWarranty("DTH-7K4Q-2M9X-QH3T", "ACTIVE", "2026-06-24", "2027-06-24");
        when(dependency(OrderWarrantyReader.class).forOrder(order.getId())).thenReturn(Map.of(laptop.getId(), List.of(card)));

        var response = service.getOrderForCustomer(order.getId(), userId);

        var byId = response.getItems().stream().collect(java.util.stream.Collectors.toMap(i -> i.getId(), i -> i));
        assertEquals(List.of(card), byId.get(laptop.getId()).getWarranties());
        assertNull(byId.get(mouse.getId()).getWarranties(), "an item without a warranty lists none");
    }

    @Test
    void anUndeliveredOrderDoesNotLookForCards() {
        Order order = order(OrderStatus.IN_TRANSIT, null, item());

        var response = service.getOrderForCustomer(order.getId(), userId);

        assertNull(response.getItems().get(0).getWarranties());
        verifyNoInteractions(dependency(OrderWarrantyReader.class));
    }

    @Test
    void anItemWithoutAnIdYetDoesNotBreakTheResponse() {
        // An order is answered before its items are flushed in some paths; Map.of() rejects null keys.
        OrderItem unsaved = item();
        unsaved.setId(null);
        Order order = order(OrderStatus.PAID, null, unsaved);
        assertDoesNotThrow(() -> service.getOrderForCustomer(order.getId(), userId));
    }

    @Test
    void deliveringAnOrderRaisesTheEventThatIssuesCards() throws Exception {
        Order order = order(OrderStatus.IN_TRANSIT, null, item());
        Method transitionTo = OrderService.class.getDeclaredMethod("transitionTo", Order.class, OrderStatus.class);
        transitionTo.setAccessible(true);

        transitionTo.invoke(service, order, OrderStatus.DELIVERED);

        assertNotNull(order.getDeliveredAt());
        verify(dependency(ApplicationEventPublisher.class)).publishEvent(argThat(
                (Object e) -> e instanceof OrderDeliveredEvent d && d.getOrderId().equals(order.getId())));
    }

    @Test
    void otherTransitionsDoNotIssueCards() throws Exception {
        Order order = order(OrderStatus.PACKAGING, null, item());
        Method transitionTo = OrderService.class.getDeclaredMethod("transitionTo", Order.class, OrderStatus.class);
        transitionTo.setAccessible(true);

        transitionTo.invoke(service, order, OrderStatus.IN_COURIER);

        verify(dependency(ApplicationEventPublisher.class), never()).publishEvent(any(OrderDeliveredEvent.class));
    }
}
