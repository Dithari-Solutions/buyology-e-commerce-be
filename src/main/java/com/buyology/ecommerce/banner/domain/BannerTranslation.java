package com.buyology.ecommerce.banner.domain;

import com.buyology.ecommerce.common.enums.Language;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "banner_translations", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "banner_id", "language" })
})
public class BannerTranslation {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_id", nullable = false)
    private Banner banner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Language language;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "button_label", length = 100)
    private String buttonLabel;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public Banner getBanner() { return banner; }
    public void setBanner(Banner banner) { this.banner = banner; }
    public Language getLanguage() { return language; }
    public void setLanguage(Language language) { this.language = language; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getButtonLabel() { return buttonLabel; }
    public void setButtonLabel(String buttonLabel) { this.buttonLabel = buttonLabel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
