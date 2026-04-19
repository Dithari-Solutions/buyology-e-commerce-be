package com.buyology.ecommerce.b2b.dto;

import com.buyology.ecommerce.b2b.domain.B2bInquiry;
import java.time.Instant;
import java.util.UUID;

public class B2bInquiryResponse {
    private UUID id;
    private String company;
    private String contactPerson;
    private String email;
    private String phone;
    private int quantity;
    private String message;
    private B2bInquiry.InquiryStatus status;
    private String adminNotes;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public B2bInquiry.InquiryStatus getStatus() { return status; }
    public void setStatus(B2bInquiry.InquiryStatus status) { this.status = status; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
