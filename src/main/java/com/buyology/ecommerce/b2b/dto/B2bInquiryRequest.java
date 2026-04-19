package com.buyology.ecommerce.b2b.dto;

import jakarta.validation.constraints.*;

public class B2bInquiryRequest {
    @NotBlank @Size(max = 200) private String company;
    @NotBlank @Size(max = 200) private String contact;
    @NotBlank @Email @Size(max = 255) private String email;
    @Size(max = 50) private String phone;
    @Min(1) private int quantity;
    @Size(max = 2000) private String message;

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
