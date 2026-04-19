package com.buyology.ecommerce.promo.dto;

import java.util.List;
import java.util.UUID;

public class SendPromoRequest {
    /** null = send to all customers */
    private List<UUID> targetUserIds;
    private boolean sendEmail = true;
    private boolean sendPush = true;

    public List<UUID> getTargetUserIds() { return targetUserIds; }
    public void setTargetUserIds(List<UUID> targetUserIds) { this.targetUserIds = targetUserIds; }
    public boolean isSendEmail() { return sendEmail; }
    public void setSendEmail(boolean sendEmail) { this.sendEmail = sendEmail; }
    public boolean isSendPush() { return sendPush; }
    public void setSendPush(boolean sendPush) { this.sendPush = sendPush; }
}
