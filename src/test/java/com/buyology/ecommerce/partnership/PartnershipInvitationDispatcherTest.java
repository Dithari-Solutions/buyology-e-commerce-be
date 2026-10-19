package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.common.service.EmailService;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.mockito.Mockito.*;

class PartnershipInvitationDispatcherTest {
    final PartnershipInvitationRepository repository = mock(PartnershipInvitationRepository.class);
    final EmailService emails = mock(EmailService.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final PartnershipInvitationRepository.Claimed claimed = new PartnershipInvitationRepository.Claimed(UUID.randomUUID(), "a@example.com", "Subject", "<p>Preview</p>");
    PartnershipInvitationDispatcher dispatcher;
    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(repository.claimNext()).thenReturn(Optional.of(claimed), Optional.empty());
        dispatcher = new PartnershipInvitationDispatcher(repository, emails, manager);
    }
    @Test void durableClaimCommitsBeforeTheProviderIsCalledAndAcceptanceIsRecorded() {
        when(emails.sendPartnershipInvitationEmail(any(),any(),any())).thenReturn(EmailService.InvitationOutcome.ACCEPTED);
        dispatcher.sendPending();
        var order = inOrder(repository, manager, emails);
        order.verify(repository).claimNext(); order.verify(manager).commit(any());
        order.verify(emails).sendPartnershipInvitationEmail(claimed.email(), claimed.subject(), claimed.html());
        order.verify(repository).finish(claimed, "ACCEPTED");
    }
    @Test void uncertainOrRejectedOutcomesAreRecordedWithoutAutomaticResend() {
        when(emails.sendPartnershipInvitationEmail(any(),any(),any())).thenReturn(EmailService.InvitationOutcome.UNKNOWN);
        dispatcher.sendPending(); dispatcher.sendPending();
        verify(emails, times(1)).sendPartnershipInvitationEmail(any(),any(),any());
        verify(repository).finish(claimed,"UNKNOWN");
    }
    @Test void unexpectedProviderExceptionDoesNotLoseTheDecision() {
        when(emails.sendPartnershipInvitationEmail(any(),any(),any())).thenThrow(new RuntimeException("timeout"));
        dispatcher.sendPending(); verify(repository).finish(claimed,"UNKNOWN");
    }
}
