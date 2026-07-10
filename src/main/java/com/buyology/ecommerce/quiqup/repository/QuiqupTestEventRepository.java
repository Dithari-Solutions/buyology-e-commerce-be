package com.buyology.ecommerce.quiqup.repository;

import com.buyology.ecommerce.quiqup.domain.QuiqupTestEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuiqupTestEventRepository extends JpaRepository<QuiqupTestEvent, UUID> {

    /** Most recent webhook events first, capped for the admin testing view. */
    List<QuiqupTestEvent> findTop100ByOrderByCreatedAtDesc();
}
