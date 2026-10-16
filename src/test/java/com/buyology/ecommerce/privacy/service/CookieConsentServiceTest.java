package com.buyology.ecommerce.privacy.service;

import com.buyology.ecommerce.privacy.domain.CookieConsentEvent;
import com.buyology.ecommerce.privacy.dto.ConsentRequest;
import com.buyology.ecommerce.privacy.repository.CookieConsentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CookieConsentServiceTest {
    final CookieConsentRepository repository = mock(CookieConsentRepository.class);
    final CookieConsentService service = new CookieConsentService(repository, 365);
    final UUID decision = UUID.randomUUID(), browser = UUID.randomUUID();
    ConsentRequest request(boolean analytics, boolean marketing) {
        return new ConsentRequest(decision, browser, analytics, marketing, "2026-10-16", "BANNER", "en");
    }
    CookieConsentEvent existing(boolean analytics, boolean marketing) {
        CookieConsentEvent row = new CookieConsentEvent();
        Map<String, Object> fields = Map.of("id", decision, "consentId", browser, "analytics", analytics,
                "marketing", marketing, "policyVersion", "2026-10-16", "source", "BANNER", "language", "en",
                "sourceHost", "buyology.online", "recordedAt", Instant.parse("2026-10-16T09:00:00Z"));
        fields.forEach((key, value) -> ReflectionTestUtils.setField(row, key, value));
        return row;
    }
    void inserted() { when(repository.insertDecision(any(), any(), anyBoolean(), anyBoolean(), anyString(), anyString(), anyString(), any(), any())).thenReturn(1); }

    @Test void recordsAnonymousRejectionAndPreservesEssentialServices() {
        inserted();
        var result = service.record(request(false, false), "https://buyology.online/private?token=secret");
        assertTrue(result.necessary()); assertFalse(result.analytics()); assertFalse(result.marketing());
        assertEquals("buyology.online", result.sourceHost()); assertEquals(browser, result.consentId());
        assertNotNull(result.recordedAt());
    }
    @Test void retryDoesNotCreateAnotherEventEvenAfterChangingRegion() {
        when(repository.findById(decision)).thenReturn(Optional.of(existing(true, false)));
        var result = service.record(request(true, false), "https://az.buyology.online");
        assertEquals(Instant.parse("2026-10-16T09:00:00Z"), result.recordedAt());
        assertEquals("buyology.online", result.sourceHost());
    }
    @Test void anExistingDecisionCannotBeOverwrittenWithDifferentChoices() {
        when(repository.findById(decision)).thenReturn(Optional.of(existing(false, false)));
        assertThrows(IllegalArgumentException.class, () -> service.record(request(true, true), null));
    }
    @Test void rejectsStalePolicyAndIncompleteChoicesWithoutWriting() {
        assertThrows(IllegalArgumentException.class, () -> service.record(new ConsentRequest(decision, browser, true, true, "old", "BANNER", "en"), null));
        assertThrows(IllegalArgumentException.class, () -> service.record(new ConsentRequest(decision, browser, null, true, "2026-10-16", "BANNER", "en"), null));
        assertThrows(IllegalArgumentException.class, () -> service.record(null, null));
        verifyNoInteractions(repository);
    }
    @Test void rejectsUntrustedOriginNamesAndNeverKeepsAPath() {
        assertNull(CookieConsentService.sourceHost("https://buyology.online.attacker.example/"));
        assertNull(CookieConsentService.sourceHost("https://attacker.example/?host=buyology.online"));
        assertNull(CookieConsentService.sourceHost("javascript:alert(1)"));
        assertEquals("buyology.online", CookieConsentService.sourceHost("https://buyology.online/path?email=private"));
    }
    @Test void adminPaginationIsBounded() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0,100)));
        service.list(-5, 500000, null, false, true);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), page.capture());
        assertEquals(0, page.getValue().getPageNumber()); assertEquals(100, page.getValue().getPageSize());
    }
    @Test void invalidConsentSearchNeverRunsAQuery() {
        assertThrows(IllegalArgumentException.class, () -> service.list(0, 25, "not-a-uuid", null, null));
        verifyNoInteractions(repository);
    }
    @Test void validatesBrowserRequestAtTheHttpBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request(false, true)).isEmpty());
            assertFalse(validator.validate(new ConsentRequest(null, browser, null, true, "2026-10-16", "forged", "x" )).isEmpty());
        }
    }
    @Test void policyDescribesTrackingWithoutExposingCookieValues() {
        var policy = service.policy();
        assertEquals(180, policy.choiceLifetimeDays()); assertEquals(365, policy.auditRetentionDays());
        assertTrue(policy.cookies().stream().anyMatch(c -> c.category().equals("Marketing")));
        assertEquals("2026-10-16", policy.version());
    }
    @Test void auditRecordsExpireAfterTheConfiguredRetention() {
        service.purgeExpired();
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteByRecordedAtBefore(cutoff.capture());
        assertTrue(Math.abs(java.time.Duration.between(cutoff.getValue(), Instant.now()).toDays()-365)<2);
    }
}
