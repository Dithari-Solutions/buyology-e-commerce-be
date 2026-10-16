package com.buyology.ecommerce.privacy.repository;

import com.buyology.ecommerce.privacy.domain.CookieConsentEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface CookieConsentRepository extends JpaRepository<CookieConsentEvent, UUID>, JpaSpecificationExecutor<CookieConsentEvent> {
    /** Browser retries may arrive at both replicas; the primary key makes them idempotent. */
    @Modifying
    @Query(value = """
            INSERT INTO cookie_consent_events
              (id, consent_id, analytics, marketing, policy_version, source, language, source_host, recorded_at)
            VALUES (:id, :consentId, :analytics, :marketing, :version, :source, :language, :host, :recordedAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertDecision(@Param("id") UUID id, @Param("consentId") UUID consentId,
            @Param("analytics") boolean analytics, @Param("marketing") boolean marketing,
            @Param("version") String version, @Param("source") String source,
            @Param("language") String language, @Param("host") String host,
            @Param("recordedAt") Instant recordedAt);

    long deleteByRecordedAtBefore(Instant cutoff);
}
