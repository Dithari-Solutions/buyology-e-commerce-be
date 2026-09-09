package com.buyology.ecommerce.customeremail.repository;

import com.buyology.ecommerce.customeremail.domain.CustomerEmailRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerEmailRecipientRepository extends JpaRepository<CustomerEmailRecipient, UUID> {

    List<CustomerEmailRecipient> findByCampaignIdOrderByEmailAsc(UUID campaignId);

    long countByCampaignIdAndStatus(UUID campaignId, CustomerEmailRecipient.Status status);
}
