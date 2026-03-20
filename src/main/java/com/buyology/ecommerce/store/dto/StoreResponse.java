package com.buyology.ecommerce.store.dto;

import com.buyology.ecommerce.store.enums.StoreStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class StoreResponse {

    private UUID id;
    private UUID countryId;
    private String countryName;
    private String name;
    private String slug;
    private StoreStatus status;
    private String bannerUrl;
    private String contactEmail;
    private String contactPhone;
    private List<StoreTranslationResponse> translations;
    private List<StoreLocationResponse> locations;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCountryId() { return countryId; }
    public void setCountryId(UUID countryId) { this.countryId = countryId; }

    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }

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

    public List<StoreTranslationResponse> getTranslations() { return translations; }
    public void setTranslations(List<StoreTranslationResponse> translations) { this.translations = translations; }

    public List<StoreLocationResponse> getLocations() { return locations; }
    public void setLocations(List<StoreLocationResponse> locations) { this.locations = locations; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
