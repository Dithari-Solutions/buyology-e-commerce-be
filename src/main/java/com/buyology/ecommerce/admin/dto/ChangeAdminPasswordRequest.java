package com.buyology.ecommerce.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SUPERADMIN reset of another admin's password.
 *
 * <p>The current password is deliberately not required: this is the recovery path used when an
 * admin has lost access, so the superadmin will not have it. The account's sessions are revoked as
 * a result, which is what makes that safe.
 */
public class ChangeAdminPasswordRequest {

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String newPassword;

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
