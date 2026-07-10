package com.buyology.ecommerce.quiqup.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only log of Quiqup webhook callbacks received by the STAGING test module.
 *
 * <p>Persisted (not in-memory) so callbacks are visible on the admin "Quiqup Testing"
 * page regardless of which app instance behind the load balancer received them. This
 * table is unrelated to any real order — it is a throwaway test audit trail.
 */
@Entity
@Table(name = "quiqup_test_events", indexes = {
        @Index(name = "idx_quiqup_events_created_at", columnList = "created_at")
})
public class QuiqupTestEvent {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Direction of the event. Currently always INBOUND_WEBHOOK. */
    @Column(name = "direction", nullable = false, length = 40)
    private String direction = "INBOUND_WEBHOOK";

    /** Best-effort event name pulled from the payload (event/status/type). */
    @Column(name = "event_type", length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    private String headers;

    /** Null when no webhook secret is configured; otherwise the HMAC verification result. */
    @Column(name = "hmac_valid")
    private Boolean hmacValid;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }
    public Boolean getHmacValid() { return hmacValid; }
    public void setHmacValid(Boolean hmacValid) { this.hmacValid = hmacValid; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
