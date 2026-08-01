package com.buyology.ecommerce.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What (if anything) currently holds an email address.
 *
 * <p>Backs the "this email already exists" conflict on the create-admin form: the message alone
 * left superadmins stuck, because the blocking account is frequently a <em>customer</em> and so is
 * invisible on the Admins page. This tells the UI exactly which account is in the way and whether
 * it can be promoted instead.
 */
public class AdminEmailLookupResponse {

    private String email;
    private boolean available;

    private UUID userId;
    private UUID authCredentialId;
    private String firstName;
    private String lastName;
    private String userType;
    private String status;
    private String provider;
    private Instant joinedAt;
    private List<String> roles;

    /** True when the holder is a live CUSTOMER that a superadmin may convert into an admin. */
    private boolean promotable;

    /** Human-readable explanation, safe to show verbatim in the form. */
    private String reason;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(UUID authCredentialId) { this.authCredentialId = authCredentialId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public boolean isPromotable() { return promotable; }
    public void setPromotable(boolean promotable) { this.promotable = promotable; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
