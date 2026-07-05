package com.buyology.ecommerce.store.dto;

import java.util.UUID;

/**
 * Slim public projection for B2B-enabled countries.
 * Used by the storefront to decide whether to show the B2B section/banner in a region.
 */
public class B2bActiveCountryResponse {

    private UUID id;
    private String code;
    private String name;
    private String currency;
    private Boolean b2bEnabled;

    public B2bActiveCountryResponse() {}

    public B2bActiveCountryResponse(UUID id, String code, String name, String currency, Boolean b2bEnabled) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.currency = currency;
        this.b2bEnabled = b2bEnabled;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Boolean getB2bEnabled() { return b2bEnabled; }
    public void setB2bEnabled(Boolean b2bEnabled) { this.b2bEnabled = b2bEnabled; }
}
