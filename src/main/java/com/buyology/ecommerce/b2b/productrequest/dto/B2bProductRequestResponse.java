package com.buyology.ecommerce.b2b.productrequest.dto;

import com.buyology.ecommerce.b2b.productrequest.domain.B2bProductRequest;
import com.buyology.ecommerce.b2b.productrequest.domain.B2bProductRequestStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A B2B product-sourcing request as returned to the member and to procurement.
 * {@code imageUrl} is a presigned GET url derived from the stored image key on read
 * (the raw key is never exposed).
 */
public class B2bProductRequestResponse {

    private UUID id;
    private String productName;
    private Integer quantity;
    private String message;
    private String imageUrl;
    private B2bProductRequestStatus status;
    private String procurementNote;
    private String memberName;
    private String contactEmail;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;

    /**
     * Build a response. {@code imageUrl} is the already-presigned url (caller resolves the
     * stored key via ContaboObjectService.getPresignedUrl); {@code memberName} is the resolved
     * membership name (nullable).
     */
    public static B2bProductRequestResponse from(B2bProductRequest r, String imageUrl, String memberName) {
        B2bProductRequestResponse dto = new B2bProductRequestResponse();
        dto.id = r.getId();
        dto.productName = r.getProductName();
        dto.quantity = r.getQuantity();
        dto.message = r.getMessage();
        dto.imageUrl = imageUrl;
        dto.status = r.getStatus();
        dto.procurementNote = r.getProcurementNote();
        dto.memberName = memberName;
        dto.contactEmail = r.getContactEmail();
        dto.createdAt = r.getCreatedAt();
        dto.updatedAt = r.getUpdatedAt();
        dto.submittedAt = r.getSubmittedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public B2bProductRequestStatus getStatus() { return status; }
    public void setStatus(B2bProductRequestStatus status) { this.status = status; }
    public String getProcurementNote() { return procurementNote; }
    public void setProcurementNote(String procurementNote) { this.procurementNote = procurementNote; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
