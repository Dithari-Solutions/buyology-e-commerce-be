package com.buyology.ecommerce.banner.dto;

import com.buyology.ecommerce.banner.domain.BannerPlatform;
import com.buyology.ecommerce.banner.domain.BannerStatus;

public class UpdateBannerRequest {

    private BannerTranslationRequest translation;
    private String buttonUrl;
    private Integer sortOrder;
    private BannerStatus status;
    private BannerPlatform platform;

    public BannerPlatform getPlatform() { return platform; }
    public void setPlatform(BannerPlatform platform) { this.platform = platform; }

    public BannerTranslationRequest getTranslation() { return translation; }
    public void setTranslation(BannerTranslationRequest translation) { this.translation = translation; }
    public String getButtonUrl() { return buttonUrl; }
    public void setButtonUrl(String buttonUrl) { this.buttonUrl = buttonUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public BannerStatus getStatus() { return status; }
    public void setStatus(BannerStatus status) { this.status = status; }
}
