package com.buyology.ecommerce.membership.dto;

import com.buyology.ecommerce.membership.domain.B2bMembership;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Rich detail view used by the admin "view" page for a single B2B member:
 * membership card + wallet + recent credit usage.
 */
public class B2bMembershipDetailResponse {

    private UUID id;
    private String membershipId;
    private UUID userId;
    private UUID applicationId;
    private String companyName;
    private String memberName;
    private B2bMembership.MembershipStatus status;
    private B2bMembership.MembershipTier tier;
    private Instant validUntil;
    private Instant frozenAt;
    private String frozenBy;
    private Instant deletedAt;
    private String deletedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean passwordSet;

    /** Resolved contact email — sourced from the application or the LOCAL credential row. */
    private String contactEmail;
    /** Resolved contact phone — from the application. */
    private String contactPhone;

    private WalletResponse wallet;
    private List<WalletTransactionResponse> recentTransactions;
    private List<CreditUsageResponse> recentCreditUsages;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getMembershipId() { return membershipId; }
    public void setMembershipId(String membershipId) { this.membershipId = membershipId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public B2bMembership.MembershipStatus getStatus() { return status; }
    public void setStatus(B2bMembership.MembershipStatus status) { this.status = status; }
    public B2bMembership.MembershipTier getTier() { return tier; }
    public void setTier(B2bMembership.MembershipTier tier) { this.tier = tier; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
    public Instant getFrozenAt() { return frozenAt; }
    public void setFrozenAt(Instant frozenAt) { this.frozenAt = frozenAt; }
    public String getFrozenBy() { return frozenBy; }
    public void setFrozenBy(String frozenBy) { this.frozenBy = frozenBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isPasswordSet() { return passwordSet; }
    public void setPasswordSet(boolean passwordSet) { this.passwordSet = passwordSet; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public WalletResponse getWallet() { return wallet; }
    public void setWallet(WalletResponse wallet) { this.wallet = wallet; }
    public List<WalletTransactionResponse> getRecentTransactions() { return recentTransactions; }
    public void setRecentTransactions(List<WalletTransactionResponse> recentTransactions) {
        this.recentTransactions = recentTransactions;
    }
    public List<CreditUsageResponse> getRecentCreditUsages() { return recentCreditUsages; }
    public void setRecentCreditUsages(List<CreditUsageResponse> recentCreditUsages) {
        this.recentCreditUsages = recentCreditUsages;
    }
}
