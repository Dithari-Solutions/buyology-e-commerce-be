package com.buyology.ecommerce.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Finalizes customer account deletions once their 30-day grace window has elapsed. */
@Component
public class UserAccountPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(UserAccountPurgeJob.class);

    private final UserAccountService service;

    public UserAccountPurgeJob(UserAccountService service) {
        this.service = service;
    }

    /** Daily at 02:45 (offset from the membership/supplier purge jobs). */
    @Scheduled(cron = "0 45 2 * * *")
    public void purge() {
        try {
            int n = service.purgeExpired();
            if (n > 0) log.info("[ACCOUNT-PURGE] permanently deleted {} expired account(s)", n);
        } catch (Exception e) {
            log.error("[ACCOUNT-PURGE] failed: {}", e.getMessage(), e);
        }
    }
}
