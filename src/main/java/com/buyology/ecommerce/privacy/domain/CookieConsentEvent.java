package com.buyology.ecommerce.privacy.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** An anonymous preference decision, never a browser cookie dump or an identity proof. */
@Entity
@Table(name = "cookie_consent_events", indexes = {
        @Index(name = "idx_cookie_consent_recorded", columnList = "recorded_at"),
        @Index(name = "idx_cookie_consent_browser", columnList = "consent_id, recorded_at")
})
public class CookieConsentEvent {
    @Id private UUID id;
    @Column(name = "consent_id", nullable = false, updatable = false) private UUID consentId;
    @Column(nullable = false, updatable = false) private boolean analytics;
    @Column(nullable = false, updatable = false) private boolean marketing;
    @Column(name = "policy_version", nullable = false, updatable = false, length = 20) private String policyVersion;
    @Column(nullable = false, updatable = false, length = 16) private String source;
    @Column(nullable = false, updatable = false, length = 8) private String language;
    @Column(name = "source_host", updatable = false, length = 253) private String sourceHost;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt;

    public UUID getId() { return id; }
    public UUID getConsentId() { return consentId; }
    public boolean isAnalytics() { return analytics; }
    public boolean isMarketing() { return marketing; }
    public String getPolicyVersion() { return policyVersion; }
    public String getSource() { return source; }
    public String getLanguage() { return language; }
    public String getSourceHost() { return sourceHost; }
    public Instant getRecordedAt() { return recordedAt; }
}
