package com.buyology.ecommerce.banner.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "banners")
public class Banner {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private BannerStatus status = BannerStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private BannerPlatform platform = BannerPlatform.WEB;

    @Column(name = "background_image_url", columnDefinition = "TEXT")
    private String backgroundImageUrl;

    @Column(name = "button_url", columnDefinition = "TEXT")
    private String buttonUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "banner",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<BannerTranslation> translations = new ArrayList<>();

    protected Banner() {
    }

    public Banner(UUID createdBy) {
        this.createdBy = createdBy;
        this.status = BannerStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = BannerStatus.ACTIVE;
        }
        if (this.platform == null) {
            this.platform = BannerPlatform.WEB;
        }
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addTranslation(BannerTranslation translation) {
        translation.setBanner(this);
        this.translations.add(translation);
    }

    public void clearTranslations() {
        for (BannerTranslation t : new ArrayList<>(this.translations)) {
            t.setBanner(null);
        }
        this.translations.clear();
    }

    public UUID getId() { return id; }

    public BannerStatus getStatus() { return status; }
    public void setStatus(BannerStatus status) { this.status = status; }

    public BannerPlatform getPlatform() { return platform; }
    public void setPlatform(BannerPlatform platform) { this.platform = platform; }

    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }

    public String getButtonUrl() { return buttonUrl; }
    public void setButtonUrl(String buttonUrl) { this.buttonUrl = buttonUrl; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public UUID getCreatedBy() { return createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public List<BannerTranslation> getTranslations() { return translations; }

    public void activate() { this.status = BannerStatus.ACTIVE; }
    public void deactivate() { this.status = BannerStatus.INACTIVE; }
}
