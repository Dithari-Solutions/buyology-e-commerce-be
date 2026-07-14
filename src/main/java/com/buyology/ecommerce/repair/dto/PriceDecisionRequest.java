package com.buyology.ecommerce.repair.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Customer's response to the quoted fixing price. {@code accept=true} starts the repair
 * (→ IN_REPAIR); {@code accept=false} declines it (→ DECLINED, device must be returned).
 */
public class PriceDecisionRequest {

    @NotNull
    private Boolean accept;

    public Boolean getAccept() { return accept; }
    public void setAccept(Boolean accept) { this.accept = accept; }
}
