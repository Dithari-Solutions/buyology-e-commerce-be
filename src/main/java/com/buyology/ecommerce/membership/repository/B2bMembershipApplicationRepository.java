package com.buyology.ecommerce.membership.repository;

import com.buyology.ecommerce.membership.domain.B2bMembershipApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2bMembershipApplicationRepository extends JpaRepository<B2bMembershipApplication, UUID> {

    List<B2bMembershipApplication> findAllByOrderByCreatedAtDesc();

    List<B2bMembershipApplication> findByStatusOrderByCreatedAtDesc(B2bMembershipApplication.ApplicationStatus status);

    Optional<B2bMembershipApplication> findByContactEmail(String contactEmail);

    Optional<B2bMembershipApplication> findByUserId(UUID userId);

    boolean existsByContactEmailAndStatusNot(String email, B2bMembershipApplication.ApplicationStatus status);
}
