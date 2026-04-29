package com.buyology.ecommerce.supplier.dto;

import com.buyology.ecommerce.supplier.domain.SupplierApplication.PreferredContact;
import com.buyology.ecommerce.supplier.domain.SupplierApplication.SellerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SupplierStep1Request {

    @NotBlank
    @Size(max = 200)
    private String fullName;

    @Size(max = 255)
    private String businessName;

    @NotNull
    private SellerType sellerType;

    @Size(max = 100)
    private String country;

    @Size(max = 100)
    private String city;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 30)
    private String phoneNumber;

//    @NotNull
    private PreferredContact preferredContact;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public SellerType getSellerType() { return sellerType; }
    public void setSellerType(SellerType sellerType) { this.sellerType = sellerType; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public PreferredContact getPreferredContact() { return preferredContact; }
    public void setPreferredContact(PreferredContact preferredContact) { this.preferredContact = preferredContact; }
}
