package com.buyology.ecommerce.supplier.dto;

import com.buyology.ecommerce.supplier.domain.SupplierApplication;
import java.time.Instant;
import java.util.UUID;

public class SupplierApplicationResponse {

    private UUID id;
    private String fullName;
    private String businessName;
    private String sellerType;
    private String country;
    private String city;
    private String email;
    private String phoneNumber;
    private String preferredContact;
    private Boolean otpVerified;
    private String productCategories;
    private String mainBrands;
    private String productCondition;
    private String initialListingRange;
    private String sellsElsewhere;
    private String canProvideImages;
    private String avgDispatchTime;
    private String handlesReturns;
    private String hasTradeLicense;
    private String tradeLicenseUrl;
    private String websiteOrSocialLink;
    private String whyBuyology;
    private Boolean declarationAccepted;
    private String status;
    private String rejectionReason;
    private Instant createdAt;

    public static SupplierApplicationResponse from(SupplierApplication app, String tradeLicenseUrl) {
        SupplierApplicationResponse r = new SupplierApplicationResponse();
        r.id = app.getId();
        r.fullName = app.getFullName();
        r.businessName = app.getBusinessName();
        r.sellerType = app.getSellerType() != null ? app.getSellerType().name() : null;
        r.country = app.getCountry();
        r.city = app.getCity();
        r.email = app.getEmail();
        r.phoneNumber = app.getPhoneNumber();
        r.preferredContact = app.getPreferredContact() != null ? app.getPreferredContact().name() : null;
        r.otpVerified = app.getOtpVerified();
        r.productCategories = app.getProductCategories();
        r.mainBrands = app.getMainBrands();
        r.productCondition = app.getProductCondition() != null ? app.getProductCondition().name() : null;
        r.initialListingRange = app.getInitialListingRange() != null ? app.getInitialListingRange().name() : null;
        r.sellsElsewhere = app.getSellsElsewhere();
        r.canProvideImages = app.getCanProvideImages() != null ? app.getCanProvideImages().name() : null;
        r.avgDispatchTime = app.getAvgDispatchTime() != null ? app.getAvgDispatchTime().name() : null;
        r.handlesReturns = app.getHandlesReturns() != null ? app.getHandlesReturns().name() : null;
        r.hasTradeLicense = app.getHasTradeLicense() != null ? app.getHasTradeLicense().name() : null;
        r.tradeLicenseUrl = tradeLicenseUrl;
        r.websiteOrSocialLink = app.getWebsiteOrSocialLink();
        r.whyBuyology = app.getWhyBuyology();
        r.declarationAccepted = app.getDeclarationAccepted();
        r.status = app.getStatus().name();
        r.rejectionReason = app.getRejectionReason();
        r.createdAt = app.getCreatedAt();
        return r;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getBusinessName() { return businessName; }
    public String getSellerType() { return sellerType; }
    public String getCountry() { return country; }
    public String getCity() { return city; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getPreferredContact() { return preferredContact; }
    public Boolean getOtpVerified() { return otpVerified; }
    public String getProductCategories() { return productCategories; }
    public String getMainBrands() { return mainBrands; }
    public String getProductCondition() { return productCondition; }
    public String getInitialListingRange() { return initialListingRange; }
    public String getSellsElsewhere() { return sellsElsewhere; }
    public String getCanProvideImages() { return canProvideImages; }
    public String getAvgDispatchTime() { return avgDispatchTime; }
    public String getHandlesReturns() { return handlesReturns; }
    public String getHasTradeLicense() { return hasTradeLicense; }
    public String getTradeLicenseUrl() { return tradeLicenseUrl; }
    public String getWebsiteOrSocialLink() { return websiteOrSocialLink; }
    public String getWhyBuyology() { return whyBuyology; }
    public Boolean getDeclarationAccepted() { return declarationAccepted; }
    public String getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
}
