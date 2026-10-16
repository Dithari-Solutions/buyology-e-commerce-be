package com.buyology.ecommerce.privacy.service;

import com.buyology.ecommerce.privacy.domain.CookieConsentEvent;
import com.buyology.ecommerce.privacy.dto.ConsentRequest;
import com.buyology.ecommerce.privacy.repository.CookieConsentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CookieConsentService {
    public static final String POLICY_VERSION = "2026-10-16";
    private final CookieConsentRepository repository;
    private final int retentionDays;

    public CookieConsentService(CookieConsentRepository repository,
            @Value("${app.privacy.consent-retention-days:365}") int retentionDays) {
        this.repository = repository;
        this.retentionDays = Math.max(180, retentionDays);
    }

    public record Decision(UUID decisionId, UUID consentId, boolean necessary, boolean analytics,
            boolean marketing, String policyVersion, String source, String language,
            String sourceHost, Instant recordedAt) {}
    public record DecisionPage(List<Decision> content, long totalElements, int totalPages, int page, int size) {}
    public record CookieDetail(String name, String category, String storage, String provider, String purpose, String duration) {}
    public record Policy(String version, int choiceLifetimeDays, int auditRetentionDays, List<CookieDetail> cookies) {}

    @Transactional
    public Decision record(ConsentRequest request, String origin) {
        if (request == null || request.decisionId() == null || request.consentId() == null
                || request.analytics() == null || request.marketing() == null
                || !Set.of("BANNER", "SETTINGS").contains(Objects.toString(request.source(), ""))
                || !Set.of("en", "az", "ar").contains(Objects.toString(request.language(), ""))) {
            throw new IllegalArgumentException("A valid cookie preference decision is required");
        }
        if (!POLICY_VERSION.equals(request.policyVersion())) {
            throw new IllegalArgumentException("Refresh the cookie preferences to use the current policy version");
        }
        String host = sourceHost(origin);
        Instant now = Instant.now();
        int inserted = repository.insertDecision(request.decisionId(), request.consentId(), request.analytics(),
                request.marketing(), POLICY_VERSION, request.source(), request.language(), host, now);
        if (inserted == 1) return new Decision(request.decisionId(), request.consentId(), true,
                request.analytics(), request.marketing(), POLICY_VERSION, request.source(), request.language(), host, now);
        CookieConsentEvent existing = repository.findById(request.decisionId())
                .orElseThrow(() -> new IllegalStateException("Cookie decision could not be read"));
        if (!existing.getConsentId().equals(request.consentId()) || existing.isAnalytics() != request.analytics()
                || existing.isMarketing() != request.marketing() || !existing.getPolicyVersion().equals(POLICY_VERSION)
                || !existing.getSource().equals(request.source()) || !existing.getLanguage().equals(request.language())) {
            throw new IllegalArgumentException("A decision ID cannot be reused for a different choice");
        }
        return view(existing);
    }

    @Transactional(readOnly = true)
    public DecisionPage list(int page, int size, String consentId, Boolean analytics, Boolean marketing) {
        Specification<CookieConsentEvent> filter = (root, query, cb) -> cb.conjunction();
        if (consentId != null && !consentId.isBlank()) {
            UUID id;
            try { id = UUID.fromString(consentId.trim()); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Enter a complete consent ID"); }
            filter = filter.and((root, query, cb) -> cb.equal(root.get("consentId"), id));
        }
        if (analytics != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("analytics"), analytics));
        if (marketing != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("marketing"), marketing));
        Page<CookieConsentEvent> rows = repository.findAll(filter, PageRequest.of(Math.max(0, page),
                Math.min(100, Math.max(1, size)), Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id"))));
        return new DecisionPage(rows.map(CookieConsentService::view).toList(), rows.getTotalElements(),
                rows.getTotalPages(), rows.getNumber(), rows.getSize());
    }

    private static Decision view(CookieConsentEvent row) {
        return new Decision(row.getId(), row.getConsentId(), true, row.isAnalytics(), row.isMarketing(),
                row.getPolicyVersion(), row.getSource(), row.getLanguage(), row.getSourceHost(), row.getRecordedAt());
    }

    /** Origin only: never persist the referrer path, query, IP, cookie values, or auth tokens. */
    static String sourceHost(String origin) {
        if (origin == null || origin.isBlank()) return null;
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (!Set.of("http", "https").contains(Objects.toString(uri.getScheme(), "")) || host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            return host.length() <= 253 && (host.equals("buyology.online") || host.endsWith(".buyology.online")
                    || host.equals("localhost") || host.equals("127.0.0.1")) ? host : null;
        } catch (IllegalArgumentException e) { return null; }
    }

    public Policy policy() {
        return new Policy(POLICY_VERSION, 180, retentionDays, List.of(
                new CookieDetail("buyology-consent", "Necessary", "Cookie", "Buyology", "Remembers cookie choices and an anonymous consent reference", "180 days"),
                new CookieDetail("refresh_token", "Necessary", "HttpOnly cookie", "Buyology API", "Restores your signed-in session; never exposed to the dashboard", "Account session policy"),
                new CookieDetail("buyology-lang / buyo-region-choice / buyo-signed-in", "Necessary", "Cookie", "Buyology", "Remembers your chosen language, delivery region, and session hint", "Up to 1 year; session hint is shorter"),
                new CookieDetail("buyo_cart_v1 / buyo_cart_saved_v1 / buyo_wishlist_v1", "Necessary", "Local storage", "Buyology", "Keeps the cart, saved items, and wishlist you request", "Until cleared or synchronized with your account"),
                new CookieDetail("buyology-theme / buy_assistant_cid / payment references", "Necessary", "Local / session storage", "Buyology", "Your chosen appearance, requested support conversation, and payment recovery", "Preference lifetime or the current tab/session"),
                new CookieDetail("buy_visitor_id / buy_visit_session", "Analytics", "Local / session storage", "Buyology", "Consented first-party visit counts; the assistant may separately use a browser ID when you request support", "Until cleared / current tab"),
                new CookieDetail("_ga / _ga_* / _gid / _gat*", "Analytics", "Cookie", "Google Analytics", "Consented page views and site usage", "Provider-controlled; up to 2 years"),
                new CookieDetail("_fbp / _fbc / _gcl_*", "Marketing", "Cookie", "Meta / Google", "Consented advertising attribution", "Provider-controlled"),
                new CookieDetail("buyo_meta_pending_purchases / buyo_meta_purchases_sent", "Marketing", "Session / local storage", "Buyology", "Consented purchase attribution and duplicate-event prevention", "Current tab / until cleared")
        ));
    }

    @Scheduled(cron = "0 35 3 * * *", zone = "Asia/Dubai")
    @Transactional
    public void purgeExpired() {
        repository.deleteByRecordedAtBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    }
}
