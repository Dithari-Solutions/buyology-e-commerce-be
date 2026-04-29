package com.buyology.ecommerce.membership.dto;

import com.buyology.ecommerce.membership.domain.B2bMembershipApplication;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class MembershipApplicationResponse {

    private UUID id;
    private UUID userId;
    private String companyName;
    private String tradeLicenseNumber;
    private String industryType;
    private int numberOfEmployees;
    private String country;
    private String city;
    private String website;
    private String contactFullName;
    private String contactDesignation;
    private String contactEmail;
    private String contactMobile;
    private List<String> businessNeeds;
    private String tradeLicenseFileUrl;
    private String vatCertificateFileUrl;
    private boolean termsAccepted;
    private B2bMembershipApplication.ApplicationStatus status;
    private String rejectionReason;
    private String rejectedBy;
    private Instant rejectedAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private String approvedBy;
    private Instant approvedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
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
    public String getTradeLicenseFileUrl() { return tradeLicenseFileUrl; }
    public void setTradeLicenseFileUrl(String tradeLicenseFileUrl) { this.tradeLicenseFileUrl = tradeLicenseFileUrl; }
    public String getVatCertificateFileUrl() { return vatCertificateFileUrl; }
    public void setVatCertificateFileUrl(String vatCertificateFileUrl) { this.vatCertificateFileUrl = vatCertificateFileUrl; }
    public boolean isTermsAccepted() { return termsAccepted; }
    public void setTermsAccepted(boolean termsAccepted) { this.termsAccepted = termsAccepted; }
    public B2bMembershipApplication.ApplicationStatus getStatus() { return status; }
    public void setStatus(B2bMembershipApplication.ApplicationStatus status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }
    public Instant getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(Instant rejectedAt) { this.rejectedAt = rejectedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
