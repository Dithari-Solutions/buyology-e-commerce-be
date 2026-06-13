package com.buyology.ecommerce.promo.repository;

import com.buyology.ecommerce.promo.domain.TokenRedemptionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TokenRedemptionConfigRepository extends JpaRepository<TokenRedemptionConfig, UUID> {
    Optional<TokenRedemptionConfig> findTopByOrderByUpdatedAtAsc();
}
