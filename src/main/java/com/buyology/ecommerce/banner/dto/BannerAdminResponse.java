package com.buyology.ecommerce.banner.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class BannerAdminResponse {

    private UUID id;
    private String backgroundImageUrl;
    private String buttonUrl;
    private Integer sortOrder;
    private String status;
    private String platform;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BannerTranslationRequest translation;

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }
    public String getButtonUrl() { return buttonUrl; }
    public void setButtonUrl(String buttonUrl) { this.buttonUrl = buttonUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public BannerTranslationRequest getTranslation() { return translation; }
    public void setTranslation(BannerTranslationRequest translation) { this.translation = translation; }
}
