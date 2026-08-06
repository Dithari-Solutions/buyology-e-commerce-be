package com.buyology.ecommerce.sell.repository;

import com.buyology.ecommerce.sell.domain.SellRequest;
import com.buyology.ecommerce.sell.domain.SellStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SellRequestRepository extends JpaRepository<SellRequest, UUID> {

    /** A customer's own sell requests, newest first. */
    List<SellRequest> findByCredentialIdOrderByCreatedAtDesc(UUID credentialId);

    /** Procurement queue — everything, newest first. */
    List<SellRequest> findAllByOrderByCreatedAtDesc();

    /** Procurement queue filtered by status, newest first. */
    List<SellRequest> findByStatusOrderByCreatedAtDesc(SellStatus status);

    /** Drives the dashboard "new updates" badge. */
    long countByAdminUnreadTrue();
}
