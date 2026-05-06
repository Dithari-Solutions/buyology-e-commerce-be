package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.membership.domain.CreditUsage;
import com.buyology.ecommerce.membership.repository.CreditUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Daily B2B membership maintenance:
 *  - Purge memberships in TRASHED state past the 30-day retention window.
 *  - Mark outstanding/partial credit usages OVERDUE when their dueAt has passed.
 */
@Component
public class B2bMembershipPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(B2bMembershipPurgeJob.class);

    private final B2bMembershipLifecycleService lifecycleService;
    private final CreditUsageRepository creditUsageRepository;

    public B2bMembershipPurgeJob(B2bMembershipLifecycleService lifecycleService,
                                 CreditUsageRepository creditUsageRepository) {
        this.lifecycleService = lifecycleService;
        this.creditUsageRepository = creditUsageRepository;
    }

    @Scheduled(cron = "0 15 2 * * *")
    public void purge() {
        try {
            int purged = lifecycleService.purgeExpiredTrash();
            if (purged > 0) {
                log.info("B2bMembershipPurgeJob: hard-deleted {} memberships past 30-day retention", purged);
            }
        } catch (Exception e) {
            log.error("B2bMembershipPurgeJob purge failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 20 2 * * *")
    @Transactional
    public void markOverdue() {
        try {
            Instant now = Instant.now();
            int marked = 0;
            for (CreditUsage.Status status : List.of(CreditUsage.Status.OUTSTANDING, CreditUsage.Status.PARTIAL)) {
                List<CreditUsage> due = creditUsageRepository.findByStatusAndDueAtBefore(status, now);
                for (CreditUsage u : due) {
                    u.setStatus(CreditUsage.Status.OVERDUE);
                    creditUsageRepository.save(u);
                    marked++;
                }
            }
            if (marked > 0) {
                log.info("B2bMembershipPurgeJob: marked {} credit usages as OVERDUE", marked);
            }
        } catch (Exception e) {
            log.error("B2bMembershipPurgeJob overdue scan failed: {}", e.getMessage(), e);
        }
    }
}
