package com.buyology.ecommerce.order.repository;

import com.buyology.ecommerce.order.domain.OrderTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderTrackingEventRepository extends JpaRepository<OrderTrackingEvent, UUID> {

    List<OrderTrackingEvent> findAllByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
