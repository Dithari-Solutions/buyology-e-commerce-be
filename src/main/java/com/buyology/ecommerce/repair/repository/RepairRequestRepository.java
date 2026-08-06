package com.buyology.ecommerce.repair.repository;

import com.buyology.ecommerce.repair.domain.RepairRequest;
import com.buyology.ecommerce.repair.domain.RepairStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RepairRequestRepository extends JpaRepository<RepairRequest, UUID> {

    /** A customer's own repairs, newest first. */
    List<RepairRequest> findByCredentialIdOrderByCreatedAtDesc(UUID credentialId);

    /** Repair-team queue — everything, newest first. */
    List<RepairRequest> findAllByOrderByCreatedAtDesc();

    /** Repair-team queue filtered by status, newest first. */
    List<RepairRequest> findByStatusOrderByCreatedAtDesc(RepairStatus status);

    /** Drives the dashboard "new updates" badge. */
    long countByAdminUnreadTrue();
}
