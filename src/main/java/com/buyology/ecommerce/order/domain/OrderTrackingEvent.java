package com.buyology.ecommerce.order.domain;

import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail for an order's lifecycle changes.
 *
 * Each status transition — whether triggered by a courier, admin, or the system —
 * produces a new row. Rows are never updated or deleted.
 *
 * For EXPRESS orders, latitude/longitude give real-time position of the courier.
 * For REGULAR orders, locationDescription holds the carrier checkpoint note.
 * Both fields support future real-time tracking use cases.
 */
@Entity
@Table(name = "order_tracking_events", indexes = {
        @Index(name = "idx_tracking_events_order_id", columnList = "order_id"),
        @Index(name = "idx_tracking_events_created_at", columnList = "created_at")
})
public class OrderTrackingEvent {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** The order status at the time of this event. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** GPS latitude — provided by courier for EXPRESS deliveries. */
    @Column(name = "latitude")
    private Double latitude;

    /** GPS longitude — provided by courier for EXPRESS deliveries. */
    @Column(name = "longitude")
    private Double longitude;

    /** Human-readable location description (e.g., "Arrived at sorting hub"). */
    @Column(name = "location_description", length = 500)
    private String locationDescription;

    /** UUID of the actor that triggered this event (courier, admin, or system UUID). */
    @Column(name = "actor_id")
    private UUID actorId;

    /** Role of the actor: CUSTOMER, COURIER, ADMIN, or SYSTEM. */
    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getLocationDescription() { return locationDescription; }
    public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }

    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public Instant getCreatedAt() { return createdAt; }
}
