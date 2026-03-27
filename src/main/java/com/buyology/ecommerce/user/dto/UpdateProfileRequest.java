package com.buyology.ecommerce.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * All fields are optional — send only what needs to change.
 * firstName and lastName live on the Users entity.
 * phoneNumber and dateOfBirth live on UserProfiles.
 */
public class UpdateProfileRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone number must be in E.164 format, e.g. +971501234567")
    private String phoneNumber;

    private LocalDate dateOfBirth;

    /** ISO 3166-1 alpha-3 country code for browsing stores (e.g. "UAE", "AZE"). */
    @Size(max = 3)
    private String selectedCountryCode;

    /** ISO 4217 currency code for price display (e.g. "AZN", "AED"). */
    @Size(max = 3)
    private String preferredCurrency;

    /** UI language preference ("EN", "AZ", "AR"). */
    @Size(max = 5)
    private String preferredLanguage;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getSelectedCountryCode() { return selectedCountryCode; }
    public void setSelectedCountryCode(String selectedCountryCode) { this.selectedCountryCode = selectedCountryCode; }

    public String getPreferredCurrency() { return preferredCurrency; }
    public void setPreferredCurrency(String preferredCurrency) { this.preferredCurrency = preferredCurrency; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
}
