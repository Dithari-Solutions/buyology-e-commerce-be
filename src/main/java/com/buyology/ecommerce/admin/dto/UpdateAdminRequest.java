package com.buyology.ecommerce.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * SUPERADMIN edit of an admin account's identity.
 *
 * <p>Every field is optional; only the ones present are applied, so the form can send a partial
 * update without clearing what it did not touch. Roles and password have their own endpoints
 * because each carries its own safety rules.
 */
public class UpdateAdminRequest {

    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Email(message = "A valid email is required")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
