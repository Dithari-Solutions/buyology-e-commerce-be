package com.buyology.ecommerce.admin.dto;

import java.time.Instant;
import java.util.UUID;

public class AdminUserSummaryResponse {

    private UUID userId;
    private UUID authCredentialId;
    private String email;
    private String firstName;
    private String lastName;
    private String userType;
    private String status;
    private Instant joinedAt;

    public AdminUserSummaryResponse() {
    }

    public AdminUserSummaryResponse(UUID userId, UUID authCredentialId, String email,
                                    String firstName, String lastName,
                                    String userType, String status, Instant joinedAt) {
        this.userId = userId;
        this.authCredentialId = authCredentialId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.userType = userType;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(UUID authCredentialId) { this.authCredentialId = authCredentialId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
