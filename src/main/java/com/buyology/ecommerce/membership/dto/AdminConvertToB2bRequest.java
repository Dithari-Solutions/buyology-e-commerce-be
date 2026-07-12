package com.buyology.ecommerce.membership.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Admin-initiated conversion of an existing (B2C) user into a B2B member.
 *
 * <p>Mirrors {@link MembershipApplicationRequest} minus the account credentials:
 * the user already has an account, so no password is captured and their existing
 * login (email/OAuth) is left untouched. The contact email/name/mobile are
 * pre-filled from the existing profile on the dashboard. The resulting
 * application is created as {@code PENDING} and flows through the normal admin
 * review queue exactly like a public sign-up.</p>
 */
public class AdminConvertToB2bRequest {

    // Company Details
    @NotBlank @Size(max = 200)
    private String companyName;

    @NotBlank @Size(max = 100)
    private String tradeLicenseNumber;

    @NotBlank @Size(max = 100)
    private String industryType;

    // Company-size bucket selected from a fixed dropdown — same server-side
    // allowlist as the public application so only the known buckets are accepted.
    @NotBlank
    @Pattern(
            regexp = "^(1-10|11-50|51-200|201-500|501-1000|1001-5000|5001-10000|10001\\+)$",
            message = "Please select a valid company size")
    private String numberOfEmployees;

    @NotBlank @Size(max = 100)
    private String country;

    @Size(max = 2)
    private String countryCode;

    @NotBlank @Size(max = 100)
    private String city;

    @Size(max = 300)
    private String website;

    // Contact Person — pre-filled from the user's existing profile on the dashboard.
    @NotBlank @Size(max = 200)
    private String contactFullName;

    @NotBlank @Size(max = 100)
    private String contactDesignation;

    @NotBlank @Email @Size(max = 255)
    private String contactEmail;

    @NotBlank @Size(max = 50)
    private String contactMobile;

    // Business Needs (multi-select)
    private List<String> businessNeeds;

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getTradeLicenseNumber() { return tradeLicenseNumber; }
    public void setTradeLicenseNumber(String tradeLicenseNumber) { this.tradeLicenseNumber = tradeLicenseNumber; }
    public String getIndustryType() { return industryType; }
    public void setIndustryType(String industryType) { this.industryType = industryType; }
    public String getNumberOfEmployees() { return numberOfEmployees; }
    public void setNumberOfEmployees(String numberOfEmployees) { this.numberOfEmployees = numberOfEmployees; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getContactFullName() { return contactFullName; }
    public void setContactFullName(String contactFullName) { this.contactFullName = contactFullName; }
    public String getContactDesignation() { return contactDesignation; }
    public void setContactDesignation(String contactDesignation) { this.contactDesignation = contactDesignation; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactMobile() { return contactMobile; }
    public void setContactMobile(String contactMobile) { this.contactMobile = contactMobile; }
    public List<String> getBusinessNeeds() { return businessNeeds; }
    public void setBusinessNeeds(List<String> businessNeeds) { this.businessNeeds = businessNeeds; }
}
