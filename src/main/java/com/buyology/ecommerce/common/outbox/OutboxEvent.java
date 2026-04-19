package com.buyology.ecommerce.common.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_events",
        indexes = @Index(name = "idx_outbox_events_status_created", columnList = "status, created_at")
)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "exchange", nullable = false, length = 150)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public OutboxEvent() {}

    private OutboxEvent(Builder b) {
        this.exchange     = b.exchange;
        this.routingKey   = b.routingKey;
        this.payload      = b.payload;
        this.eventVersion = b.eventVersion;
        this.status       = b.status;
        this.retryCount   = b.retryCount;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String exchange;
        private String routingKey;
        private String payload;
        private int eventVersion = 1;
        private OutboxStatus status = OutboxStatus.PENDING;
        private int retryCount = 0;

        public Builder exchange(String v)      { this.exchange = v;      return this; }
        public Builder routingKey(String v)    { this.routingKey = v;    return this; }
        public Builder payload(String v)       { this.payload = v;       return this; }
        public Builder eventVersion(int v)     { this.eventVersion = v;  return this; }
        public Builder status(OutboxStatus v)  { this.status = v;        return this; }
        public Builder retryCount(int v)       { this.retryCount = v;    return this; }
        public OutboxEvent build()             { return new OutboxEvent(this); }
    }

    public UUID getId()                       { return id; }
    public String getExchange()               { return exchange; }
    public String getRoutingKey()             { return routingKey; }
    public String getPayload()                { return payload; }
    public int getEventVersion()              { return eventVersion; }
    public OutboxStatus getStatus()           { return status; }
    public int getRetryCount()                { return retryCount; }
    public Instant getCreatedAt()             { return createdAt; }
    public Instant getPublishedAt()           { return publishedAt; }

    public void setStatus(OutboxStatus s)     { this.status = s; }
    public void setRetryCount(int n)          { this.retryCount = n; }
    public void setPublishedAt(Instant t)     { this.publishedAt = t; }
}
