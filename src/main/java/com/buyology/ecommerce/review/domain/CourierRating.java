package com.buyology.ecommerce.review.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores a customer's 5-star rating for the courier who delivered their order.
 * One rating per order per user — enforced by the unique constraint.
 */
@Entity
@Table(
        name = "courier_ratings",
        indexes = {
                @Index(name = "idx_courier_rating_order_id",   columnList = "order_id"),
                @Index(name = "idx_courier_rating_courier_id", columnList = "courier_id"),
                @Index(name = "idx_courier_rating_user_id",    columnList = "user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_courier_rating_order_user",
                        columnNames = {"order_id", "user_id"})
        }
)
public class CourierRating {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The ecommerce order this rating belongs to. */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** The customer who submitted the rating. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * The courier's UUID as recorded on the ecommerce Order.courierUserId field.
     * Stored denormalised — no FK to courier service.
     */
    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    /** Star rating 1-5. */
    @Min(1) @Max(5)
    @Column(name = "stars", nullable = false)
    private int stars;

    /** Optional free-text feedback from the customer. */
    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID courierId) { this.courierId = courierId; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getCreatedAt() { return createdAt; }
}
