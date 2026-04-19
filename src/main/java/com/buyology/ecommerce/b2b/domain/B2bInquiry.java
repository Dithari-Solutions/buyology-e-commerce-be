package com.buyology.ecommerce.b2b.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "b2b_inquiries", indexes = {
        @Index(name = "idx_b2b_status", columnList = "status"),
        @Index(name = "idx_b2b_created", columnList = "created_at")
})
public class B2bInquiry {

    public enum InquiryStatus { NEW, REVIEWED, RESPONDED }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "company", nullable = false, length = 200)
    private String company;

    @Column(name = "contact_person", nullable = false, length = 200)
    private String contactPerson;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InquiryStatus status = InquiryStatus.NEW;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
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
    public InquiryStatus getStatus() { return status; }
    public void setStatus(InquiryStatus status) { this.status = status; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
