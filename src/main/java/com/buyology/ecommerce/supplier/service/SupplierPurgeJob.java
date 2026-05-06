package com.buyology.ecommerce.supplier.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SupplierPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierPurgeJob.class);

    private final SupplierLifecycleService lifecycleService;

    public SupplierPurgeJob(SupplierLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void purge() {
        try {
            int purged = lifecycleService.purgeExpiredTrash();
            if (purged > 0) {
                log.info("SupplierPurgeJob: hard-deleted {} suppliers past 30-day trash retention", purged);
            }
        } catch (Exception e) {
            log.error("SupplierPurgeJob failed: {}", e.getMessage(), e);
        }
    }
}
