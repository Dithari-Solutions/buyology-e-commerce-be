package com.buyology.ecommerce.membership.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "b2b_membership_applications", indexes = {
        @Index(name = "idx_bma_status", columnList = "status"),
        @Index(name = "idx_bma_contact_email", columnList = "contact_email"),
        @Index(name = "idx_bma_created", columnList = "created_at")
})
public class B2bMembershipApplication {

    public enum ApplicationStatus { PENDING, APPROVED, REJECTED, UNDER_REVIEW }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    // Company Details
    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "trade_license_number", nullable = false, length = 100)
    private String tradeLicenseNumber;

    @Column(name = "industry_type", nullable = false, length = 100)
    private String industryType;

    @Column(name = "number_of_employees", nullable = false)
    private int numberOfEmployees;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "website", length = 300)
    private String website;

    // Contact Person
    @Column(name = "contact_full_name", nullable = false, length = 200)
    private String contactFullName;

    @Column(name = "contact_designation", nullable = false, length = 100)
    private String contactDesignation;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_mobile", nullable = false, length = 50)
    private String contactMobile;

    // Business needs stored as comma-separated values
    @Column(name = "business_needs", columnDefinition = "text")
    private String businessNeeds;

    // File uploads (URLs/paths)
    @Column(name = "trade_license_file_url", length = 500)
    private String tradeLicenseFileUrl;

    @Column(name = "vat_certificate_file_url", length = 500)
    private String vatCertificateFileUrl;

    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "rejected_by", length = 200)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "reviewed_by", length = 200)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "approved_by", length = 200)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
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
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
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
    public String getBusinessNeeds() { return businessNeeds; }
    public void setBusinessNeeds(String businessNeeds) { this.businessNeeds = businessNeeds; }
    public String getTradeLicenseFileUrl() { return tradeLicenseFileUrl; }
    public void setTradeLicenseFileUrl(String tradeLicenseFileUrl) { this.tradeLicenseFileUrl = tradeLicenseFileUrl; }
    public String getVatCertificateFileUrl() { return vatCertificateFileUrl; }
    public void setVatCertificateFileUrl(String vatCertificateFileUrl) { this.vatCertificateFileUrl = vatCertificateFileUrl; }
    public boolean isTermsAccepted() { return termsAccepted; }
    public void setTermsAccepted(boolean termsAccepted) { this.termsAccepted = termsAccepted; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
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
    public Instant getUpdatedAt() { return updatedAt; }
}
