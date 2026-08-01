package com.buyology.ecommerce.sell.dto;

import com.buyology.ecommerce.sell.domain.SellPayoutMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Customer's response to procurement's buy-back offer. {@code accept=true} sells the device
 * (→ ACCEPTED, awaiting payout); {@code accept=false} declines it (→ DECLINED, device must be
 * returned).
 *
 * {@code payoutMethod} is required when accepting and must be STORE_CASH today — WALLET_CREDIT is
 * reserved for the future Buyology wallet and is rejected with a "coming soon" message.
 */
public class OfferDecisionRequest {

    @NotNull
    private Boolean accept;

    private SellPayoutMethod payoutMethod;

    public Boolean getAccept() { return accept; }
    public void setAccept(Boolean accept) { this.accept = accept; }
    public SellPayoutMethod getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(SellPayoutMethod payoutMethod) { this.payoutMethod = payoutMethod; }
}
