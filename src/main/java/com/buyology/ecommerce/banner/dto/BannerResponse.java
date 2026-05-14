package com.buyology.ecommerce.banner.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class BannerResponse {

    private UUID id;
    private String backgroundImageUrl;
    private String text;
    private String buttonLabel;
    private String buttonUrl;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;

    public BannerResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBackgroundImageUrl() { return backgroundImageUrl; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getButtonLabel() { return buttonLabel; }
    public void setButtonLabel(String buttonLabel) { this.buttonLabel = buttonLabel; }
    public String getButtonUrl() { return buttonUrl; }
    public void setButtonUrl(String buttonUrl) { this.buttonUrl = buttonUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
