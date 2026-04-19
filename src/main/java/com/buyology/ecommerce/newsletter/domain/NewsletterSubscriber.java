package com.buyology.ecommerce.newsletter.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "newsletter_subscribers", indexes = {
        @Index(name = "idx_ns_email", columnList = "email"),
        @Index(name = "idx_ns_token", columnList = "unsubscribe_token")
})
public class NewsletterSubscriber {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "unsubscribe_token", nullable = false, unique = true, updatable = false)
    private UUID unsubscribeToken;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "subscribed_at", nullable = false, updatable = false)
    private Instant subscribedAt;

    @PrePersist
    public void prePersist() {
        this.subscribedAt = Instant.now();
        if (this.unsubscribeToken == null) {
            this.unsubscribeToken = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public UUID getUnsubscribeToken() { return unsubscribeToken; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public Instant getSubscribedAt() { return subscribedAt; }
}
