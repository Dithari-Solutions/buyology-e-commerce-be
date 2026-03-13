package com.buyology.ecommerce.cart.domain;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart {

    public enum CartStatus {
        ACTIVE, CHECKED_OUT, ABANDONED
    }

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auth_credential_id", nullable = false)
    private AuthCredentials authCredential;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CartStatus status = CartStatus.ACTIVE;

    @Column(name = "total_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Cart() {
    }

    public Cart(AuthCredentials authCredential) {
        this.authCredential = authCredential;
        this.status = CartStatus.ACTIVE;
        this.totalPrice = BigDecimal.ZERO;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = CartStatus.ACTIVE;
        if (this.totalPrice == null) this.totalPrice = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AuthCredentials getAuthCredential() { return authCredential; }
    public void setAuthCredential(AuthCredentials authCredential) { this.authCredential = authCredential; }

    public CartStatus getStatus() { return status; }
    public void setStatus(CartStatus status) { this.status = status; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
