package com.buyology.ecommerce.customeremail.repository;

import com.buyology.ecommerce.customeremail.domain.CustomerEmailCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface CustomerEmailCampaignRepository extends JpaRepository<CustomerEmailCampaign, UUID> {

    Page<CustomerEmailCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Claims a campaign for sending, exclusively.
     *
     * <p>The whole guard is the {@code status = 'DRAFT'} in the WHERE clause: a double-clicked
     * button, a retried request and the second application host all issue this UPDATE, and exactly
     * one of them gets a row back. A disabled button is not a guard — the existing newsletter send
     * re-sends in full on every call and relies on nothing but the UI hiding it.
     */
    @Modifying
    @Query("UPDATE CustomerEmailCampaign c SET c.status = 'SENDING', c.startedAt = :now "
            + "WHERE c.id = :id AND c.status = 'DRAFT'")
    int claimForSending(UUID id, Instant now);
}
