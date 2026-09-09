package com.buyology.ecommerce.customeremail.dto;

import com.buyology.ecommerce.customeremail.domain.CustomerEmailCampaign;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Asks how many customers an audience actually reaches, after suppression. */
public class PreviewAudienceRequest {

    @NotNull
    private CustomerEmailCampaign.Audience audience;

    private List<UUID> userIds;

    public CustomerEmailCampaign.Audience getAudience() { return audience; }
    public void setAudience(CustomerEmailCampaign.Audience audience) { this.audience = audience; }
    public List<UUID> getUserIds() { return userIds; }
    public void setUserIds(List<UUID> userIds) { this.userIds = userIds; }
}
