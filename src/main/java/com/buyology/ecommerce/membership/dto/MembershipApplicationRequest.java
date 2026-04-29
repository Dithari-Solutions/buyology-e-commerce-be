package com.buyology.ecommerce.membership.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public class MembershipApplicationRequest {

    // Company Details
    @NotBlank @Size(max = 200)
    private String companyName;

    @NotBlank @Size(max = 100)
    private String tradeLicenseNumber;

    @NotBlank @Size(max = 100)
    private String industryType;

    @Min(1)
    private int numberOfEmployees;

    @NotBlank @Size(max = 100)
    private String country;

    @NotBlank @Size(max = 100)
    private String city;

    @Size(max = 300)
    private String website;

    // Contact Person
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

    @AssertTrue(message = "You must accept the terms and conditions")
    private boolean termsAccepted;

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getTradeLicenseNumber() { return tradeLicenseNumber; }
    public void setTradeLicenseNumber(String tradeLicenseNumber) { this.tradeLicenseNumber = tradeLicenseNumber; }
    public String getIndustryType() { return industryType; }
    public void setIndustryType(String industryType) { this.industryType = industryType; }
    public int getNumberOfEmployees() { return numberOfEmployees; }
    public void setNumberOfEmployees(int numberOfEmployees) { this.numberOfEmployees = numberOfEmployees; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
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
    public boolean isTermsAccepted() { return termsAccepted; }
    public void setTermsAccepted(boolean termsAccepted) { this.termsAccepted = termsAccepted; }
}
