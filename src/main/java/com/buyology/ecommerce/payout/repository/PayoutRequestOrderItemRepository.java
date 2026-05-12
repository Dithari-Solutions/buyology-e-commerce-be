package com.buyology.ecommerce.payout.repository;

import com.buyology.ecommerce.payout.domain.PayoutRequestOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayoutRequestOrderItemRepository extends JpaRepository<PayoutRequestOrderItem, UUID> {
    List<PayoutRequestOrderItem> findAllByPayoutRequestId(UUID payoutRequestId);
}
