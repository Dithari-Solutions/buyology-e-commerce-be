package com.buyology.ecommerce.membership.dto;

import com.buyology.ecommerce.membership.domain.B2bMembership;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Admin patch payload for an existing B2B membership. Every field is optional;
 * only non-null values are applied.
 */
public class B2bMembershipUpdateRequest {

    @Size(max = 200)
    private String companyName;

    @Size(max = 200)
    private String memberName;

    private B2bMembership.MembershipTier tier;
    private Instant validUntil;

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public B2bMembership.MembershipTier getTier() { return tier; }
    public void setTier(B2bMembership.MembershipTier tier) { this.tier = tier; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
}
