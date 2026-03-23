package com.buyology.ecommerce.favorite.domain;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.product.domain.Product;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "favorites", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"auth_credential_id", "product_id"})
})
public class Favorite {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auth_credential_id", nullable = false)
    private AuthCredentials authCredential;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Favorite() {
    }

    public Favorite(AuthCredentials authCredential, Product product) {
        this.authCredential = authCredential;
        this.product = product;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AuthCredentials getAuthCredential() { return authCredential; }
    public void setAuthCredential(AuthCredentials authCredential) { this.authCredential = authCredential; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Instant getCreatedAt() { return createdAt; }
}
