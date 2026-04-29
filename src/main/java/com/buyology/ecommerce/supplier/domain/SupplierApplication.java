package com.buyology.ecommerce.supplier.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_applications", indexes = {
        @Index(name = "idx_supplier_app_email", columnList = "email"),
        @Index(name = "idx_supplier_app_status", columnList = "status")
})
public class SupplierApplication {

    public enum SellerType {
        REGISTERED_BUSINESS, RETAIL_STORE, ONLINE_SELLER,
        REFURBISHER, INDIVIDUAL_SELLER, SMALL_BRAND, OTHER
    }

    public enum PreferredContact {
        WHATSAPP, EMAIL, PHONE_CALL
    }

    public enum ProductCondition {
        BRAND_NEW, REFURBISHED, OPEN_BOX, USED, MIXED
    }

    public enum InitialListingRange {
        ONE_TO_TEN, ELEVEN_TO_TWENTYFIVE, TWENTYSIX_TO_HUNDRED, OVER_HUNDRED
    }

    public enum CanProvideImages {
        YES, NEED_HELP
    }

    public enum AvgDispatchTime {
        SAME_DAY, ONE_TWO_DAYS, THREE_FIVE_DAYS, MORE_THAN_FIVE
    }

    public enum HandlesReturns {
        YES, NO, NEED_GUIDANCE
    }

    public enum TradeLicenseStatus {
        YES, NO, IN_PROCESS
    }

    public enum ApplicationStatus {
        PENDING, APPROVED, REJECTED
    }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Section 1 - About You
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "business_name", length = 255)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_type", length = 30)
    private SellerType sellerType;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "city", length = 100)
    private String city;

    // Section 2 - Contact Info
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact", nullable = false, length = 20)
    private PreferredContact preferredContact;

    @Column(name = "otp_verified", nullable = false)
    private Boolean otpVerified = false;

    // Section 3 - What Will You Sell
    @Column(name = "product_categories", columnDefinition = "TEXT")
    private String productCategories; // JSON array

    @Column(name = "main_brands", length = 500)
    private String mainBrands;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_condition", length = 20)
    private ProductCondition productCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "initial_listing_range", length = 30)
    private InitialListingRange initialListingRange;

    // Section 4 - Operations
    @Column(name = "sells_elsewhere", columnDefinition = "TEXT")
    private String sellsElsewhere; // JSON array

    @Enumerated(EnumType.STRING)
    @Column(name = "can_provide_images", length = 20)
    private CanProvideImages canProvideImages;

    @Enumerated(EnumType.STRING)
    @Column(name = "avg_dispatch_time", length = 20)
    private AvgDispatchTime avgDispatchTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "handles_returns", length = 20)
    private HandlesReturns handlesReturns;

    // Section 5 - Verification
    @Enumerated(EnumType.STRING)
    @Column(name = "has_trade_license", length = 20)
    private TradeLicenseStatus hasTradeLicense;

    @Column(name = "trade_license_key", columnDefinition = "TEXT")
    private String tradeLicenseKey;

    @Column(name = "website_or_social_link", length = 500)
    private String websiteOrSocialLink;

    // Section 6 - Final
    @Column(name = "why_buyology", columnDefinition = "TEXT")
    private String whyBuyology;

    @Column(name = "declaration_accepted", nullable = false)
    private Boolean declarationAccepted = false;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (otpVerified == null) otpVerified = false;
        if (declarationAccepted == null) declarationAccepted = false;
        if (status == null) status = ApplicationStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public SellerType getSellerType() { return sellerType; }
    public void setSellerType(SellerType sellerType) { this.sellerType = sellerType; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public PreferredContact getPreferredContact() { return preferredContact; }
    public void setPreferredContact(PreferredContact preferredContact) { this.preferredContact = preferredContact; }
    public Boolean getOtpVerified() { return otpVerified; }
    public void setOtpVerified(Boolean otpVerified) { this.otpVerified = otpVerified; }
    public String getProductCategories() { return productCategories; }
    public void setProductCategories(String productCategories) { this.productCategories = productCategories; }
    public String getMainBrands() { return mainBrands; }
    public void setMainBrands(String mainBrands) { this.mainBrands = mainBrands; }
    public ProductCondition getProductCondition() { return productCondition; }
    public void setProductCondition(ProductCondition productCondition) { this.productCondition = productCondition; }
    public InitialListingRange getInitialListingRange() { return initialListingRange; }
    public void setInitialListingRange(InitialListingRange initialListingRange) { this.initialListingRange = initialListingRange; }
    public String getSellsElsewhere() { return sellsElsewhere; }
    public void setSellsElsewhere(String sellsElsewhere) { this.sellsElsewhere = sellsElsewhere; }
    public CanProvideImages getCanProvideImages() { return canProvideImages; }
    public void setCanProvideImages(CanProvideImages canProvideImages) { this.canProvideImages = canProvideImages; }
    public AvgDispatchTime getAvgDispatchTime() { return avgDispatchTime; }
    public void setAvgDispatchTime(AvgDispatchTime avgDispatchTime) { this.avgDispatchTime = avgDispatchTime; }
    public HandlesReturns getHandlesReturns() { return handlesReturns; }
    public void setHandlesReturns(HandlesReturns handlesReturns) { this.handlesReturns = handlesReturns; }
    public TradeLicenseStatus getHasTradeLicense() { return hasTradeLicense; }
    public void setHasTradeLicense(TradeLicenseStatus hasTradeLicense) { this.hasTradeLicense = hasTradeLicense; }
    public String getTradeLicenseKey() { return tradeLicenseKey; }
    public void setTradeLicenseKey(String tradeLicenseKey) { this.tradeLicenseKey = tradeLicenseKey; }
    public String getWebsiteOrSocialLink() { return websiteOrSocialLink; }
    public void setWebsiteOrSocialLink(String websiteOrSocialLink) { this.websiteOrSocialLink = websiteOrSocialLink; }
    public String getWhyBuyology() { return whyBuyology; }
    public void setWhyBuyology(String whyBuyology) { this.whyBuyology = whyBuyology; }
    public Boolean getDeclarationAccepted() { return declarationAccepted; }
    public void setDeclarationAccepted(Boolean declarationAccepted) { this.declarationAccepted = declarationAccepted; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
