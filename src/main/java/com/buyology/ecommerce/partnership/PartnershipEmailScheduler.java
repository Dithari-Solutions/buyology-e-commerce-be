package com.buyology.ecommerce.partnership;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PartnershipEmailScheduler {
    private final PartnershipRequestService service;
    public PartnershipEmailScheduler(PartnershipRequestService service) { this.service=service; }
    @Scheduled(fixedDelayString="${app.partnership.email-delay-ms:15000}")
    public void sendPending() { service.sendPendingEmails(); }
}
