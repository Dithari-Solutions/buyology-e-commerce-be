package com.buyology.ecommerce.refund.repository;

import com.buyology.ecommerce.refund.domain.RefundRequest;
import com.buyology.ecommerce.refund.enums.RefundRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    Page<RefundRequest> findAllByUserId(UUID userId, Pageable pageable);

    Page<RefundRequest> findAllByStatus(RefundRequestStatus status, Pageable pageable);

    Optional<RefundRequest> findByIdAndUserId(UUID id, UUID userId);

    List<RefundRequest> findAllByOrderId(UUID orderId);
}
