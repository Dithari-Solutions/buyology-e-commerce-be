package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.customeremail.service.CustomerEmailGuard;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnershipInvitationServiceTest {
    final PartnershipInvitationRepository repository = mock(PartnershipInvitationRepository.class);
    final CustomerEmailGuard guard = mock(CustomerEmailGuard.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final UUID id = UUID.randomUUID(), admin = UUID.randomUUID();
    PartnershipInvitationService service;
    @BeforeEach void setup() throws Exception {
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        service = new PartnershipInvitationService(repository, new PartnershipInvitationTemplate(), guard, manager);
        when(repository.find(id)).thenReturn(Optional.of(new PartnershipInvitationRepository.Batch(id, admin, Instant.now(), PartnershipInvitationTemplate.VERSION)));
        when(repository.deliveries(id)).thenReturn(List.of(new PartnershipInvitationRepository.Delivery("a@example.com", "QUEUED", null)));
    }
    @Test void whitespaceAndCaseDoNotSendDuplicateInvitations() {
        assertEquals(List.of("a@example.com", "b@example.com"), PartnershipInvitationService.normalize(" B@example.com, a@example.com, A@EXAMPLE.COM "));
    }
    @Test void wholeBatchIsRejectedBeforePersistenceIfAnyAddressIsInvalid() {
        for (String input : Arrays.asList(null, "", "a@example.com,", ",a@example.com", "a@example.com,,b@example.com", "Person <a@example.com>", "a@example.com\r\nBcc:b@example.com", "a..b@example.com", "a@localhost")) {
            assertThrows(IllegalArgumentException.class, () -> service.submit(id, admin, input, PartnershipInvitationTemplate.VERSION), input);
        }
        verifyNoInteractions(repository, guard);
    }
    @Test void enforcesUniqueRecipientLimitButNotDuplicateCount() {
        String fifty = java.util.stream.IntStream.range(0,50).mapToObj(i -> "partner"+i+"@example.com").collect(java.util.stream.Collectors.joining(","));
        assertEquals(50, PartnershipInvitationService.normalize(fifty+","+fifty).size());
        assertThrows(IllegalArgumentException.class, () -> PartnershipInvitationService.normalize(fifty+",extra@example.com"));
    }
    @Test void stalePreviewCannotQueueAnUnreviewedTemplate() {
        assertThrows(ResponseStatusException.class, () -> service.submit(id, admin, "a@example.com", "old"));
        verifyNoInteractions(repository, guard);
    }
    @Test void newSubmissionUsesExistingEmailLimitsAndQueuesOnlyOnce() {
        when(repository.insert(eq(id), eq(admin), any())).thenReturn(1);
        var result = service.submit(id, admin, "a@example.com,A@example.com", PartnershipInvitationTemplate.VERSION);
        assertEquals(1, result.recipients().size());
        verify(guard).checkCampaign(admin, 1);
        verify(repository).addRecipients(id, List.of("a@example.com"));
        verify(manager).commit(any());
    }
    @Test void networkRetryDoesNotSendAgainOrConsumeQuotaAgain() {
        when(repository.deliveries(id)).thenReturn(List.of(new PartnershipInvitationRepository.Delivery("a@example.com", "ACCEPTED", Instant.now())));
        assertEquals("ACCEPTED", service.submit(id, admin, "a@example.com", PartnershipInvitationTemplate.VERSION).recipients().get(0).status());
        verifyNoInteractions(guard);
        verify(repository, never()).addRecipients(any(), any());
    }
    @Test void referenceCannotBeReusedByAnotherAdminOrForDifferentRecipients() {
        assertThrows(ResponseStatusException.class, () -> service.submit(id, UUID.randomUUID(), "a@example.com", PartnershipInvitationTemplate.VERSION));
        assertThrows(ResponseStatusException.class, () -> service.submit(id, admin, "b@example.com", PartnershipInvitationTemplate.VERSION));
        verify(repository, never()).addRecipients(any(), any());
    }
    @Test void rateLimitFailureRollsBackBeforeRecipientsAreQueued() {
        when(repository.insert(eq(id), eq(admin), any())).thenReturn(1);
        doThrow(new IllegalStateException("Daily limit reached")).when(guard).checkCampaign(admin, 1);
        assertThrows(IllegalStateException.class, () -> service.submit(id, admin, "a@example.com", PartnershipInvitationTemplate.VERSION));
        verify(repository, never()).addRecipients(any(), any());
        verify(manager).rollback(any());
    }
}
