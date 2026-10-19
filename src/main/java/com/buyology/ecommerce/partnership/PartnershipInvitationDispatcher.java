package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.common.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PartnershipInvitationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PartnershipInvitationDispatcher.class);
    private final PartnershipInvitationRepository repository;
    private final EmailService emails;
    private final TransactionTemplate transactions;
    public PartnershipInvitationDispatcher(PartnershipInvitationRepository repository, EmailService emails, PlatformTransactionManager manager) {
        this.repository = repository; this.emails = emails; this.transactions = new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString = "${app.partnership.invitation-delay-ms:5000}")
    public void sendPending() {
        repository.expireInterrupted();
        // Bounded work on the existing scheduled-job pool. Never resend an ambiguous provider outcome.
        for (int i = 0; i < 5; i++) {
            var next = transactions.execute(tx -> repository.claimNext());
            if (next == null || next.isEmpty()) return;
            var claimed = next.get();
            String outcome;
            try { outcome = emails.sendPartnershipInvitationEmail(claimed.email(), claimed.subject(), claimed.html()).name(); }
            catch (Exception e) { outcome = "UNKNOWN"; log.warn("Invitation send outcome unknown for batch {}", claimed.batchId()); }
            repository.finish(claimed, outcome);
        }
    }
}
