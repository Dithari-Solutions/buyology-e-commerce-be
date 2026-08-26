package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.OrderPaymentMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderPaymentMessageRepository extends JpaRepository<OrderPaymentMessage, UUID> {

    List<OrderPaymentMessage> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    long countByOrderId(UUID orderId);
}
