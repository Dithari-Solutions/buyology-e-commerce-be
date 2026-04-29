package com.buyology.ecommerce.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class SupplierSubmitRequest {

    @NotNull
    private UUID applicationId;

    @NotBlank
    private String whyBuyology;

    @NotNull
    private Boolean declarationAccepted;

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public String getWhyBuyology() { return whyBuyology; }
    public void setWhyBuyology(String whyBuyology) { this.whyBuyology = whyBuyology; }
    public Boolean getDeclarationAccepted() { return declarationAccepted; }
    public void setDeclarationAccepted(Boolean declarationAccepted) { this.declarationAccepted = declarationAccepted; }
}
