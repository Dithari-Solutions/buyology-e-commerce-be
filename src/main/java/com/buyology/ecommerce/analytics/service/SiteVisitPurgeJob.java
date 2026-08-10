package com.buyology.ecommerce.analytics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Trims raw page-view rows past {@code app.analytics.retention-days}.
 *
 * <p>{@code site_visits} is the only table in the schema that grows with traffic rather than with
 * business events, so it needs a sweeper — a busy storefront writes far more rows here than it does
 * orders.
 */
@Component
public class SiteVisitPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(SiteVisitPurgeJob.class);

    private final VisitorAnalyticsService service;

    public SiteVisitPurgeJob(VisitorAnalyticsService service) {
        this.service = service;
    }

    /** Daily at 03:20 (offset from the account/membership/supplier purge jobs). */
    @Scheduled(cron = "0 20 3 * * *")
    public void purge() {
        try {
            int n = service.purgeExpired();
            if (n > 0) {
                log.info("[VISIT-PURGE] deleted {} page-view row(s) older than {} days",
                        n, service.getRetentionDays());
            }
        } catch (Exception e) {
            log.error("[VISIT-PURGE] failed: {}", e.getMessage(), e);
        }
    }
}
