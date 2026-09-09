package com.buyology.ecommerce.customeremail.dto;

import com.buyology.ecommerce.customeremail.domain.CustomerEmailCampaign;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class CreateCampaignRequest {

    @NotBlank
    private String subject;

    @NotBlank
    private String bodyHtml;

    /**
     * Required, with no default.
     *
     * <p>A nullable audience that fell back to "everyone" would mean a dropped field, a
     * serialisation change or a filtered-to-nothing selection could silently escalate into a full
     * broadcast. The expensive reading must never be the implicit one.
     */
    @NotNull
    private CustomerEmailCampaign.Audience audience;

    /** Only for SELECTED. Sending these alongside ALL_CUSTOMERS is rejected as ambiguous. */
    private List<UUID> userIds;

    /**
     * The recipient count the admin was shown by /preview. The send is refused unless it still
     * matches, which is what catches "I meant to select five".
     */
    private int confirmRecipientCount;

    /** Snapshotted onto the campaign so the record survives the admin leaving. */
    private String adminName;

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBodyHtml() { return bodyHtml; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
    public CustomerEmailCampaign.Audience getAudience() { return audience; }
    public void setAudience(CustomerEmailCampaign.Audience audience) { this.audience = audience; }
    public List<UUID> getUserIds() { return userIds; }
    public void setUserIds(List<UUID> userIds) { this.userIds = userIds; }
    public int getConfirmRecipientCount() { return confirmRecipientCount; }
    public void setConfirmRecipientCount(int v) { this.confirmRecipientCount = v; }
    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }
}
