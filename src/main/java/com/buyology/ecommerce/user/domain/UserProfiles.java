package com.buyology.ecommerce.user.domain;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "user_profiles")
public class UserProfiles {

    @Id
    @GeneratedValue
    private UUID id; // matches DB UUID

    // =========================
    // One-to-One relationship with Users
    // =========================
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    private Users user;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /** True once the phone number has been verified via Twilio Verify. Reset to false whenever the number changes. */
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "address")
    private String address;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /**
     * ISO 3166-1 alpha-3 country code the user has selected for browsing stores (e.g. "UAE", "AZE").
     * Defaults to the country detected from the user's location at signup or explicitly set by the user.
     */
    @Column(name = "selected_country_code", length = 3)
    private String selectedCountryCode;

    /**
     * ISO 4217 currency code the user prefers for price display (e.g. "AZN", "AED", "USD").
     * Defaults to the native currency of selectedCountryCode. Can be changed independently.
     */
    @Column(name = "preferred_currency", length = 3)
    private String preferredCurrency;

    /**
     * UI language preference (e.g. "EN", "AZ", "AR").
     */
    @Column(name = "preferred_language", length = 5)
    private String preferredLanguage;

    @Column(name = "tokens", nullable = false)
    private int tokens = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // =========================
    // Constructors
    // =========================
    public UserProfiles() {
    }

    public UserProfiles(Users user, String phoneNumber, String address, LocalDate dateOfBirth, String avatarUrl) {
        this.user = user;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.dateOfBirth = dateOfBirth;
        this.avatarUrl = avatarUrl;
    }

    // =========================
    // JPA lifecycle hooks
    // =========================
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // =========================
    // Getters & Setters
    // =========================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getSelectedCountryCode() {
        return selectedCountryCode;
    }

    public void setSelectedCountryCode(String selectedCountryCode) {
        this.selectedCountryCode = selectedCountryCode;
    }

    public String getPreferredCurrency() {
        return preferredCurrency;
    }

    public void setPreferredCurrency(String preferredCurrency) {
        this.preferredCurrency = preferredCurrency;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
