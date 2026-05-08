package com.buyology.ecommerce.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Admin patch payload for an existing supplier's profile fields.
 * Lifecycle transitions (freeze / restore / trash) go through dedicated endpoints.
 */
public class SupplierUpdateRequest {

    @Size(max = 255)
    private String businessName;

    @Email
    @Size(max = 255)
    private String contactEmail;

    @Size(max = 30)
    private String contactPhone;

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
