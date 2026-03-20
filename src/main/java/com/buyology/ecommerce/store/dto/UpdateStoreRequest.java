package com.buyology.ecommerce.store.dto;

import com.buyology.ecommerce.store.enums.StoreStatus;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class UpdateStoreRequest {

    private UUID countryId;

    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String slug;

    private StoreStatus status;

    private String bannerUrl;

    @Size(max = 255)
    private String contactEmail;

    @Size(max = 50)
    private String contactPhone;

    public UUID getCountryId() { return countryId; }
    public void setCountryId(UUID countryId) { this.countryId = countryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public StoreStatus getStatus() { return status; }
    public void setStatus(StoreStatus status) { this.status = status; }

    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
