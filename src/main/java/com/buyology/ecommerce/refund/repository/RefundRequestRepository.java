package com.buyology.ecommerce.refund.repository;

import com.buyology.ecommerce.refund.domain.RefundRequest;
import com.buyology.ecommerce.refund.enums.RefundRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    Page<RefundRequest> findAllByUserId(UUID userId, Pageable pageable);

    Page<RefundRequest> findAllByStatus(RefundRequestStatus status, Pageable pageable);

    Optional<RefundRequest> findByIdAndUserId(UUID id, UUID userId);

    List<RefundRequest> findAllByOrderId(UUID orderId);

    /**
     * Refunds that touch a given supplier — i.e. the refund's order contains at least
     * one order item stamped with that supplierId. Read-only supplier visibility.
     */
    @Query("""
            SELECT DISTINCT r FROM RefundRequest r, OrderItem i
            WHERE i.order.id = r.orderId
              AND i.supplierId = :supplierId
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<RefundRequest> findAllForSupplier(@Param("supplierId") UUID supplierId,
                                           @Param("status") RefundRequestStatus status,
                                           Pageable pageable);

    /** Single refund visible to a supplier only if its order contains that supplier's item. */
    @Query("""
            SELECT DISTINCT r FROM RefundRequest r, OrderItem i
            WHERE r.id = :id
              AND i.order.id = r.orderId
              AND i.supplierId = :supplierId
            """)
    Optional<RefundRequest> findByIdForSupplier(@Param("id") UUID id,
                                                @Param("supplierId") UUID supplierId);
}
