package com.buyology.ecommerce.user.domain;

import com.buyology.ecommerce.user.enums.AddressLabel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Saved delivery address for a user.
 * phoneVerified: true only when the phone number was confirmed via SMS OTP.
 * addressVerified: true only when Google Maps Geocoding confirmed the address exists.
 * formattedAddress + latitude/longitude are populated from the geocoding response.
 */
@Entity
@Table(name = "user_addresses", indexes = {
        @Index(name = "idx_user_addresses_user_id", columnList = "user_id"),
        @Index(name = "idx_user_addresses_is_default", columnList = "is_default")
})
public class UserAddress {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // ── Recipient info ────────────────────────────────────────────────────────

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    // ── Address fields ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "label", nullable = false, length = 10)
    private AddressLabel label = AddressLabel.HOME;

    // Building name / number + street — enough for courier to locate the building
    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    // Apartment, floor, unit — enough for courier to reach the door
    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "country", nullable = false, length = 3)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    // ── Geocoding results (from Google Maps) ──────────────────────────────────

    // Google's canonical representation of the address
    @Column(name = "formatted_address", columnDefinition = "TEXT")
    private String formattedAddress;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // true once Google Geocoding confirmed the address resolves to a real location
    @Column(name = "address_verified", nullable = false)
    private boolean addressVerified = false;

    // ── Flags ─────────────────────────────────────────────────────────────────

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.label == null) this.label = AddressLabel.HOME;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public boolean isPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public AddressLabel getLabel() { return label; }
    public void setLabel(AddressLabel label) { this.label = label; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getFormattedAddress() { return formattedAddress; }
    public void setFormattedAddress(String formattedAddress) { this.formattedAddress = formattedAddress; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public boolean isAddressVerified() { return addressVerified; }
    public void setAddressVerified(boolean addressVerified) { this.addressVerified = addressVerified; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
