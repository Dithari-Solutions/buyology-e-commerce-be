package com.buyology.ecommerce.store.dto;

import jakarta.validation.constraints.Size;

public class UpdateCountryRequest {

    @Size(min = 2, max = 3, message = "Country code must be 2-3 characters")
    private String code;

    @Size(max = 100, message = "Country name must not exceed 100 characters")
    private String name;

    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO 4217 code")
    private String currency;

    private Boolean isActive;

    // Nullable: only applied when present (super-admin controlled B2B region toggle).
    private Boolean b2bEnabled;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getB2bEnabled() { return b2bEnabled; }
    public void setB2bEnabled(Boolean b2bEnabled) { this.b2bEnabled = b2bEnabled; }
}
