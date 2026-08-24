package com.buyology.ecommerce.giveaway.repository;

import com.buyology.ecommerce.giveaway.domain.GiveawayEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GiveawayEntryRepository extends JpaRepository<GiveawayEntry, UUID> {

    Optional<GiveawayEntry> findByCampaignAndUserId(String campaign, UUID userId);

    Optional<GiveawayEntry> findByCampaignAndInstagramHandle(String campaign, String instagramHandle);

    Page<GiveawayEntry> findByCampaignOrderByCreatedAtDesc(String campaign, Pageable pageable);

    long countByCampaign(String campaign);
}
