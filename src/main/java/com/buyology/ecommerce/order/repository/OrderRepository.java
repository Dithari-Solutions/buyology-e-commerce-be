package com.buyology.ecommerce.order.repository;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findAllByUserId(UUID userId, Pageable pageable);

    Page<Order> findAll(Pageable pageable);

    Page<Order> findAllByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findAllByDeliveryMethod(DeliveryMethod deliveryMethod, Pageable pageable);

    Page<Order> findAllByStatusAndDeliveryMethod(OrderStatus status, DeliveryMethod deliveryMethod, Pageable pageable);

    List<Order> findAllByCourierUserIdAndDeliveryMethod(UUID courierUserId, DeliveryMethod deliveryMethod);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);
}
