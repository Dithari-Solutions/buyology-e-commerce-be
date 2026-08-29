package com.buyology.ecommerce.giveaway.repository;

import com.buyology.ecommerce.giveaway.domain.GiveawayCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GiveawayCampaignRepository extends JpaRepository<GiveawayCampaign, UUID> {
    Optional<GiveawayCampaign> findByCampaign(String campaign);
}
