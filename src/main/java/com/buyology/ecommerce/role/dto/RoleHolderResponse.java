package com.buyology.ecommerce.role.dto;

import java.time.Instant;
import java.util.UUID;

/** A user holding a given role — backs the "who has this role" view before a role is edited. */
public class RoleHolderResponse {

    private UUID userId;
    private UUID authCredentialId;
    private String firstName;
    private String lastName;
    private String email;
    private String userType;
    private String status;
    private Instant assignedAt;

    public RoleHolderResponse() {
    }

    public RoleHolderResponse(UUID userId, UUID authCredentialId, String firstName, String lastName,
                              String email, String userType, String status, Instant assignedAt) {
        this.userId = userId;
        this.authCredentialId = authCredentialId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.userType = userType;
        this.status = status;
        this.assignedAt = assignedAt;
    }

    public UUID getUserId() { return userId; }
    public UUID getAuthCredentialId() { return authCredentialId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getUserType() { return userType; }
    public String getStatus() { return status; }
    public Instant getAssignedAt() { return assignedAt; }
}
