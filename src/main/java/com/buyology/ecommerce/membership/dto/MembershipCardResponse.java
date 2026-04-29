package com.buyology.ecommerce.membership.dto;

import com.buyology.ecommerce.membership.domain.B2bMembership;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class MembershipCardResponse {

    private UUID id;
    private String membershipId;
    private UUID userId;
    private String companyName;
    private String memberName;
    private B2bMembership.MembershipStatus status;
    private B2bMembership.MembershipTier tier;
    private BigDecimal walletBalance;
    private String walletCurrency;
    private Instant validUntil;
    private Instant createdAt;
    private String qrCodePlaceholder;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getMembershipId() { return membershipId; }
    public void setMembershipId(String membershipId) { this.membershipId = membershipId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public B2bMembership.MembershipStatus getStatus() { return status; }
    public void setStatus(B2bMembership.MembershipStatus status) { this.status = status; }
    public B2bMembership.MembershipTier getTier() { return tier; }
    public void setTier(B2bMembership.MembershipTier tier) { this.tier = tier; }
    public BigDecimal getWalletBalance() { return walletBalance; }
    public void setWalletBalance(BigDecimal walletBalance) { this.walletBalance = walletBalance; }
    public String getWalletCurrency() { return walletCurrency; }
    public void setWalletCurrency(String walletCurrency) { this.walletCurrency = walletCurrency; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getQrCodePlaceholder() { return qrCodePlaceholder; }
    public void setQrCodePlaceholder(String qrCodePlaceholder) { this.qrCodePlaceholder = qrCodePlaceholder; }
}
