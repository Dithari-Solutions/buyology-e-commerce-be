package com.buyology.ecommerce.membership.repository;

import com.buyology.ecommerce.membership.domain.B2bMembership;
import com.buyology.ecommerce.membership.domain.B2bMembership.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2bMembershipRepository extends JpaRepository<B2bMembership, UUID> {

    Optional<B2bMembership> findByUserId(UUID userId);

    Optional<B2bMembership> findByMembershipId(String membershipId);

    List<B2bMembership> findAllByOrderByCreatedAtDesc();

    boolean existsByUserId(UUID userId);

    List<B2bMembership> findByStatusAndDeletedAtBefore(MembershipStatus status, Instant cutoff);

    List<B2bMembership> findByStatus(MembershipStatus status);
}
