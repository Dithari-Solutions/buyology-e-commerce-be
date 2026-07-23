package com.buyology.ecommerce.user.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ProfileResponse {

    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private boolean phoneVerified;
    private LocalDate dateOfBirth;
    private String avatarUrl;

    /**
     * true when the profile has all fields required to initiate a payment:
     * firstName, lastName, phoneNumber + at least one saved address.
     * false otherwise — the frontend can use this to show a "complete your profile" banner.
     */
    private boolean paymentReady;

    /**
     * If paymentReady is false, this list tells the frontend exactly what is missing.
     * Empty when paymentReady is true.
     */
    private List<String> missingFields;

    /** ISO 3166-1 alpha-3 country code for browsing stores (e.g. "UAE", "AZE"). */
    private String selectedCountryCode;

    /** ISO 4217 currency for price display (e.g. "AZN", "AED"). */
    private String preferredCurrency;

    /** UI language preference (e.g. "EN", "AZ", "AR"). */
    private String preferredLanguage;

    private Instant createdAt;
    private Instant updatedAt;

    /** True when the account is scheduled for deletion (within the 30-day grace window). */
    private boolean pendingDeletion;
    /** When the account will be permanently deleted (deletion request + 30 days). Null otherwise. */
    private Instant deletionScheduledAt;

    /**
     * Status of this user's B2B membership application — PENDING, UNDER_REVIEW,
     * APPROVED or REJECTED. Null when they have never applied.
     */
    private String b2bApplicationStatus;

    /**
     * True when this account was created through the B2B business sign-up and the
     * application has not been approved yet. Such accounts can sign in and browse,
     * but the storefront blocks every action behind an "awaiting approval" notice
     * until an admin approves. An existing customer who applies to upgrade is NOT
     * affected — they keep the access they already had.
     */
    private boolean b2bPendingApproval;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public boolean isPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isPaymentReady() { return paymentReady; }
    public void setPaymentReady(boolean paymentReady) { this.paymentReady = paymentReady; }

    public List<String> getMissingFields() { return missingFields; }
    public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }

    public String getSelectedCountryCode() { return selectedCountryCode; }
    public void setSelectedCountryCode(String selectedCountryCode) { this.selectedCountryCode = selectedCountryCode; }

    public String getPreferredCurrency() { return preferredCurrency; }
    public void setPreferredCurrency(String preferredCurrency) { this.preferredCurrency = preferredCurrency; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isPendingDeletion() { return pendingDeletion; }
    public void setPendingDeletion(boolean pendingDeletion) { this.pendingDeletion = pendingDeletion; }
    public Instant getDeletionScheduledAt() { return deletionScheduledAt; }
    public void setDeletionScheduledAt(Instant deletionScheduledAt) { this.deletionScheduledAt = deletionScheduledAt; }

    public String getB2bApplicationStatus() { return b2bApplicationStatus; }
    public void setB2bApplicationStatus(String b2bApplicationStatus) { this.b2bApplicationStatus = b2bApplicationStatus; }

    public boolean isB2bPendingApproval() { return b2bPendingApproval; }
    public void setB2bPendingApproval(boolean b2bPendingApproval) { this.b2bPendingApproval = b2bPendingApproval; }
}
