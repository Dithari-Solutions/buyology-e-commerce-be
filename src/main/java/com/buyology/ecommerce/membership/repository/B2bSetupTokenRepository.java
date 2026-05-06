package com.buyology.ecommerce.membership.repository;

import com.buyology.ecommerce.membership.domain.B2bSetupToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2bSetupTokenRepository extends JpaRepository<B2bSetupToken, UUID> {
    Optional<B2bSetupToken> findByTokenAndUsedFalse(String token);

    List<B2bSetupToken> findByMembershipIdAndUsedFalse(UUID membershipId);
}
