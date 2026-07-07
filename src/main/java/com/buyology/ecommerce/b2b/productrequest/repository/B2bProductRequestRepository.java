package com.buyology.ecommerce.b2b.productrequest.repository;

import com.buyology.ecommerce.b2b.productrequest.domain.B2bProductRequest;
import com.buyology.ecommerce.b2b.productrequest.domain.B2bProductRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface B2bProductRequestRepository extends JpaRepository<B2bProductRequest, UUID> {

    /** A member's own requests, newest first. */
    List<B2bProductRequest> findByCredentialIdOrderByCreatedAtDesc(UUID credentialId);

    /** Procurement queue — everything, newest first. */
    List<B2bProductRequest> findAllByOrderByCreatedAtDesc();

    /** Procurement queue filtered by status, newest first. */
    List<B2bProductRequest> findByStatusOrderByCreatedAtDesc(B2bProductRequestStatus status);

    /** Count of requests in a given status — drives the NEW-count badge. */
    long countByStatus(B2bProductRequestStatus status);
}
