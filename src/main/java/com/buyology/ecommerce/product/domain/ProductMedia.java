package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_media")
public class ProductMedia {

    @Id
    @GeneratedValue
    private UUID id;

    // Many-to-one relationship to Product
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Media type enum
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Enum for media type
    public enum MediaType {
        IMAGE,
        VIDEO
    }

    // Constructors
    public ProductMedia() {
    }

    public ProductMedia(Product product, MediaType mediaType, String url, String thumbnailUrl, Boolean isPrimary,
            Integer orderIndex) {
        this.product = product;
        this.mediaType = mediaType;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.isPrimary = isPrimary != null ? isPrimary : false;
        this.orderIndex = orderIndex != null ? orderIndex : 0;
    }

    // Lifecycle hook
    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        if (this.isPrimary == null)
            this.isPrimary = false;
        if (this.orderIndex == null)
            this.orderIndex = 0;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
