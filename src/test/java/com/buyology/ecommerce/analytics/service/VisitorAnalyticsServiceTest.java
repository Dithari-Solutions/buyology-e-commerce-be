package com.buyology.ecommerce.analytics.service;

import com.buyology.ecommerce.analytics.domain.SiteVisit;
import com.buyology.ecommerce.analytics.dto.TrackVisitRequest;
import com.buyology.ecommerce.analytics.repository.SiteVisitRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the beacon write path, which is the only place untrusted input reaches this module. The
 * endpoint is public, so "nothing gets stored that we did not intend to store" is the guarantee
 * under test — page views arriving with a query string, a bot user-agent, a junk visitor id or a
 * spoofed forwarded-for header.
 */
class VisitorAnalyticsServiceTest {

    private static final String VISITOR = "visitorAAA123";
    private static final String SESSION = "sessionS111222";

    private final SiteVisitRepository repository = mock(SiteVisitRepository.class);

    /** Tracking on, no IP salt, forwarded headers untrusted — the shipped defaults. */
    private VisitorAnalyticsService service() {
        return new VisitorAnalyticsService(repository, true, 400, 30, 5, "", false);
    }

    private SiteVisit recordAndCapture(VisitorAnalyticsService service, TrackVisitRequest request,
                                       String userAgent, String forwardedFor, String remoteAddr) {
        assertTrue(service.record(request, userAgent, forwardedFor, remoteAddr));
        ArgumentCaptor<SiteVisit> captor = ArgumentCaptor.forClass(SiteVisit.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void storesOnePageViewWithTheQueryStringStripped() {
        SiteVisit saved = recordAndCapture(service(),
                new TrackVisitRequest(VISITOR, SESSION,
                        // A reset token in the query string must never be persisted.
                        "/en/reset-password?token=secret-value#top", "https://www.google.com/search?q=buyology", "en"),
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Safari/604.1", null, "203.0.113.7");

        assertEquals(VISITOR, saved.getVisitorId());
        assertEquals(SESSION, saved.getSessionId());
        assertEquals("/en/reset-password", saved.getPath());
        assertEquals("www.google.com", saved.getReferrerHost(), "only the referrer host is kept");
        assertEquals("en", saved.getLanguage());
        assertNull(saved.getIpHash(), "no IP-derived value without a configured salt");
        assertEquals(LocalDate.now(ZoneId.of("Asia/Dubai")), saved.getVisitDay());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void dropsBotTrafficWithoutStoringIt() {
        assertFalse(service().record(new TrackVisitRequest(VISITOR, SESSION, "/en", null, "en"),
                "Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)", null, "203.0.113.7"));
        verify(repository, never()).save(any());
    }

    @Test
    void dropsEverythingWhileTrackingIsDisabled() {
        VisitorAnalyticsService disabled =
                new VisitorAnalyticsService(repository, false, 400, 30, 5, "", false);

        assertFalse(disabled.record(new TrackVisitRequest(VISITOR, SESSION, "/en", null, "en"),
                "Mozilla/5.0", null, "203.0.113.7"));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMissingOrMalformedIds() {
        VisitorAnalyticsService service = service();

        assertThrows(IllegalArgumentException.class, () -> service.record(
                new TrackVisitRequest(null, SESSION, "/en", null, "en"), "Mozilla/5.0", null, "203.0.113.7"));
        assertThrows(IllegalArgumentException.class, () -> service.record(
                new TrackVisitRequest(VISITOR, "  ", "/en", null, "en"), "Mozilla/5.0", null, "203.0.113.7"));
        assertThrows(IllegalArgumentException.class, () -> service.record(
                // Not URL-safe — would otherwise land verbatim in the visitor_id column.
                new TrackVisitRequest("visitor'; drop--", SESSION, "/en", null, "en"),
                "Mozilla/5.0", null, "203.0.113.7"));
        assertThrows(IllegalArgumentException.class, () -> service.record(
                new TrackVisitRequest("short", SESSION, "/en", null, "en"),
                "Mozilla/5.0", null, "203.0.113.7"));
        verify(repository, never()).save(any());
    }

    @Test
    void normalisesAnEmptyOrRelativePathAndAnUnparseableReferrer() {
        SiteVisit saved = recordAndCapture(service(),
                new TrackVisitRequest(VISITOR, SESSION, "en/shop", "not a url", "  "),
                "Mozilla/5.0", null, "203.0.113.7");

        assertEquals("/en/shop", saved.getPath(), "paths are root-anchored");
        assertNull(saved.getReferrerHost());
        assertNull(saved.getLanguage(), "blank language is stored as null, not an empty string");
    }

    @Test
    void hashesTheClientIpOnlyWhenSaltedAndIgnoresForwardedForByDefault() {
        VisitorAnalyticsService untrusting =
                new VisitorAnalyticsService(repository, true, 400, 30, 5, "pepper", false);
        SiteVisit fromRemoteAddr = recordAndCapture(untrusting,
                new TrackVisitRequest(VISITOR, SESSION, "/en", null, "en"),
                "Mozilla/5.0", "198.51.100.9", "203.0.113.7");

        assertNotNull(fromRemoteAddr.getIpHash());
        assertEquals(64, fromRemoteAddr.getIpHash().length(), "SHA-256 hex digest");
        assertFalse(fromRemoteAddr.getIpHash().contains("203.0.113.7"));

        // Behind a trusted proxy the forwarded client IP wins, so the hash must differ.
        SiteVisitRepository trustingRepo = mock(SiteVisitRepository.class);
        VisitorAnalyticsService trusting =
                new VisitorAnalyticsService(trustingRepo, true, 400, 30, 5, "pepper", true);
        assertTrue(trusting.record(new TrackVisitRequest(VISITOR, SESSION, "/en", null, "en"),
                "Mozilla/5.0", "198.51.100.9, 10.0.0.1", "203.0.113.7"));
        ArgumentCaptor<SiteVisit> captor = ArgumentCaptor.forClass(SiteVisit.class);
        verify(trustingRepo).save(captor.capture());

        assertNotEquals(fromRemoteAddr.getIpHash(), captor.getValue().getIpHash());
    }

    @Test
    void stripsControlCharactersRatherThanLettingThemReachPostgres() {
        // A JSON body may legally carry an escaped NUL; Postgres rejects it outright, so the insert
        // would fail and lose the page view instead of storing a sanitised one.
        SiteVisit saved = recordAndCapture(service(),
                new TrackVisitRequest(VISITOR, SESSION, "/en/sh\0op\nnext", "https://x.example/\0", "e\0n"),
                "Mozilla/5.0", null, "203.0.113.7");

        assertEquals("/en/shopnext", saved.getPath());
        assertEquals("en", saved.getLanguage());
        assertEquals("x.example", saved.getReferrerHost());
    }

    @Test
    void truncatesAnOverlongPathToTheColumnWidth() {
        SiteVisit saved = recordAndCapture(service(),
                new TrackVisitRequest(VISITOR, SESSION, "/en/" + "a".repeat(500), null, "en"),
                "Mozilla/5.0", null, "203.0.113.7");

        assertEquals(300, saved.getPath().length(), "must fit path VARCHAR(300)");
    }
}
