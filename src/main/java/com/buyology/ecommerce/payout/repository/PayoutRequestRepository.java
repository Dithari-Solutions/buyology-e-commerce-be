package com.buyology.ecommerce.payout.repository;

import com.buyology.ecommerce.payout.domain.PayoutRequest;
import com.buyology.ecommerce.payout.enums.PayoutRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {

    Page<PayoutRequest> findAllByStatus(PayoutRequestStatus status, Pageable pageable);

    Page<PayoutRequest> findAllBySupplierId(UUID supplierId, Pageable pageable);

    Optional<PayoutRequest> findFirstBySupplierIdAndStatus(UUID supplierId, PayoutRequestStatus status);

    Optional<PayoutRequest> findFirstBySupplierIdAndStatusOrderByPaidAtDesc(
            UUID supplierId, PayoutRequestStatus status);
}
