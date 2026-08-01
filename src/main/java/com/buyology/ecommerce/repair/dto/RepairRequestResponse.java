package com.buyology.ecommerce.repair.dto;

import com.buyology.ecommerce.repair.domain.RepairDeliveryMethod;
import com.buyology.ecommerce.repair.domain.RepairRequest;
import com.buyology.ecommerce.repair.domain.RepairStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A repair request as returned to the customer and to the repair team.
 * {@code imageUrls} are presigned GET urls derived from the stored keys on read
 * (the raw keys are never exposed). {@code storeBranchName} is resolved from the chosen
 * store location, when any.
 */
public class RepairRequestResponse {

    private UUID id;
    private String reference;
    private String productName;
    private String brand;
    private String model;
    private LocalDate purchaseDate;
    private String description;
    private List<String> imageUrls;

    private RepairStatus status;
    private RepairDeliveryMethod inboundDeliveryMethod;
    private UUID storeLocationId;
    private String storeBranchName;
    private String storeAddress;
    private RepairDeliveryMethod returnDeliveryMethod;

    private BigDecimal courierFeeAmount;
    private String courierFeeCurrency;
    private boolean courierFeePaid;
    /** Money taken for a courier pickup the customer then swapped for a store drop-off. */
    private boolean courierFeeRefundDue;
    private RepairDeliveryMethod previousInboundDeliveryMethod;
    private Instant inboundDeliveryChangedAt;

    private BigDecimal estimatedPrice;
    private String estimatedPriceCurrency;
    private String estimatedTime;

    // ── AI preliminary estimate (advisory, never the binding quote) ──────────
    /** Always priced in AED for the UAE market; see aiEstimateCurrency. */
    private BigDecimal aiEstimateMinPrice;
    private BigDecimal aiEstimateMaxPrice;
    private String aiEstimateCurrency;
    private String aiEstimateConfidence;
    private String aiEstimateSummary;
    private String aiEstimateTime;
    private Instant aiEstimatedAt;
    /** The same range converted into the caller's currency, when one was requested. */
    private BigDecimal aiEstimateConvertedMinPrice;
    private BigDecimal aiEstimateConvertedMaxPrice;
    private String aiEstimateConvertedCurrency;

    private String adminNote;
    private String contactEmail;
    private String contactPhone;

    private boolean adminUnread;
    private boolean customerUnread;

    private Instant deviceReceivedAt;
    private Instant pricedAt;
    private Instant submittedAt;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Build a response. {@code imageUrls} are already-presigned urls; {@code storeBranchName}/
     * {@code storeAddress} are the resolved chosen-branch details (nullable).
     */
    public static RepairRequestResponse from(RepairRequest r, List<String> imageUrls,
                                             String storeBranchName, String storeAddress) {
        RepairRequestResponse dto = new RepairRequestResponse();
        dto.id = r.getId();
        dto.reference = r.getReference();
        dto.productName = r.getProductName();
        dto.brand = r.getBrand();
        dto.model = r.getModel();
        dto.purchaseDate = r.getPurchaseDate();
        dto.description = r.getDescription();
        dto.imageUrls = imageUrls;
        dto.status = r.getStatus();
        dto.inboundDeliveryMethod = r.getInboundDeliveryMethod();
        dto.storeLocationId = r.getStoreLocationId();
        dto.storeBranchName = storeBranchName;
        dto.storeAddress = storeAddress;
        dto.returnDeliveryMethod = r.getReturnDeliveryMethod();
        dto.courierFeeAmount = r.getCourierFeeAmount();
        dto.courierFeeCurrency = r.getCourierFeeCurrency();
        dto.courierFeePaid = r.isCourierFeePaid();
        dto.courierFeeRefundDue = r.isCourierFeeRefundDue();
        dto.previousInboundDeliveryMethod = r.getPreviousInboundDeliveryMethod();
        dto.inboundDeliveryChangedAt = r.getInboundDeliveryChangedAt();
        dto.estimatedPrice = r.getEstimatedPrice();
        dto.estimatedPriceCurrency = r.getEstimatedPriceCurrency();
        dto.estimatedTime = r.getEstimatedTime();
        dto.aiEstimateMinPrice = r.getAiEstimateMinPrice();
        dto.aiEstimateMaxPrice = r.getAiEstimateMaxPrice();
        dto.aiEstimateCurrency = r.getAiEstimateCurrency();
        dto.aiEstimateConfidence = r.getAiEstimateConfidence();
        dto.aiEstimateSummary = r.getAiEstimateSummary();
        dto.aiEstimateTime = r.getAiEstimateTime();
        dto.aiEstimatedAt = r.getAiEstimatedAt();
        dto.adminNote = r.getAdminNote();
        dto.contactEmail = r.getContactEmail();
        dto.contactPhone = r.getContactPhone();
        dto.adminUnread = r.isAdminUnread();
        dto.customerUnread = r.isCustomerUnread();
        dto.deviceReceivedAt = r.getDeviceReceivedAt();
        dto.pricedAt = r.getPricedAt();
        dto.submittedAt = r.getSubmittedAt();
        dto.createdAt = r.getCreatedAt();
        dto.updatedAt = r.getUpdatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public RepairStatus getStatus() { return status; }
    public void setStatus(RepairStatus status) { this.status = status; }
    public RepairDeliveryMethod getInboundDeliveryMethod() { return inboundDeliveryMethod; }
    public void setInboundDeliveryMethod(RepairDeliveryMethod inboundDeliveryMethod) { this.inboundDeliveryMethod = inboundDeliveryMethod; }
    public UUID getStoreLocationId() { return storeLocationId; }
    public void setStoreLocationId(UUID storeLocationId) { this.storeLocationId = storeLocationId; }
    public String getStoreBranchName() { return storeBranchName; }
    public void setStoreBranchName(String storeBranchName) { this.storeBranchName = storeBranchName; }
    public String getStoreAddress() { return storeAddress; }
    public void setStoreAddress(String storeAddress) { this.storeAddress = storeAddress; }
    public RepairDeliveryMethod getReturnDeliveryMethod() { return returnDeliveryMethod; }
    public void setReturnDeliveryMethod(RepairDeliveryMethod returnDeliveryMethod) { this.returnDeliveryMethod = returnDeliveryMethod; }
    public BigDecimal getCourierFeeAmount() { return courierFeeAmount; }
    public void setCourierFeeAmount(BigDecimal courierFeeAmount) { this.courierFeeAmount = courierFeeAmount; }
    public String getCourierFeeCurrency() { return courierFeeCurrency; }
    public void setCourierFeeCurrency(String courierFeeCurrency) { this.courierFeeCurrency = courierFeeCurrency; }
    public boolean isCourierFeePaid() { return courierFeePaid; }
    public void setCourierFeePaid(boolean courierFeePaid) { this.courierFeePaid = courierFeePaid; }
    public boolean isCourierFeeRefundDue() { return courierFeeRefundDue; }
    public void setCourierFeeRefundDue(boolean v) { this.courierFeeRefundDue = v; }
    public RepairDeliveryMethod getPreviousInboundDeliveryMethod() { return previousInboundDeliveryMethod; }
    public void setPreviousInboundDeliveryMethod(RepairDeliveryMethod v) { this.previousInboundDeliveryMethod = v; }
    public Instant getInboundDeliveryChangedAt() { return inboundDeliveryChangedAt; }
    public void setInboundDeliveryChangedAt(Instant v) { this.inboundDeliveryChangedAt = v; }
    public BigDecimal getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(BigDecimal estimatedPrice) { this.estimatedPrice = estimatedPrice; }
    public String getEstimatedPriceCurrency() { return estimatedPriceCurrency; }
    public void setEstimatedPriceCurrency(String estimatedPriceCurrency) { this.estimatedPriceCurrency = estimatedPriceCurrency; }
    public String getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; }

    public BigDecimal getAiEstimateMinPrice() { return aiEstimateMinPrice; }
    public void setAiEstimateMinPrice(BigDecimal aiEstimateMinPrice) { this.aiEstimateMinPrice = aiEstimateMinPrice; }
    public BigDecimal getAiEstimateMaxPrice() { return aiEstimateMaxPrice; }
    public void setAiEstimateMaxPrice(BigDecimal aiEstimateMaxPrice) { this.aiEstimateMaxPrice = aiEstimateMaxPrice; }
    public String getAiEstimateCurrency() { return aiEstimateCurrency; }
    public void setAiEstimateCurrency(String aiEstimateCurrency) { this.aiEstimateCurrency = aiEstimateCurrency; }
    public String getAiEstimateConfidence() { return aiEstimateConfidence; }
    public void setAiEstimateConfidence(String aiEstimateConfidence) { this.aiEstimateConfidence = aiEstimateConfidence; }
    public String getAiEstimateSummary() { return aiEstimateSummary; }
    public void setAiEstimateSummary(String aiEstimateSummary) { this.aiEstimateSummary = aiEstimateSummary; }
    public String getAiEstimateTime() { return aiEstimateTime; }
    public void setAiEstimateTime(String aiEstimateTime) { this.aiEstimateTime = aiEstimateTime; }
    public Instant getAiEstimatedAt() { return aiEstimatedAt; }
    public void setAiEstimatedAt(Instant aiEstimatedAt) { this.aiEstimatedAt = aiEstimatedAt; }
    public BigDecimal getAiEstimateConvertedMinPrice() { return aiEstimateConvertedMinPrice; }
    public void setAiEstimateConvertedMinPrice(BigDecimal v) { this.aiEstimateConvertedMinPrice = v; }
    public BigDecimal getAiEstimateConvertedMaxPrice() { return aiEstimateConvertedMaxPrice; }
    public void setAiEstimateConvertedMaxPrice(BigDecimal v) { this.aiEstimateConvertedMaxPrice = v; }
    public String getAiEstimateConvertedCurrency() { return aiEstimateConvertedCurrency; }
    public void setAiEstimateConvertedCurrency(String v) { this.aiEstimateConvertedCurrency = v; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public boolean isAdminUnread() { return adminUnread; }
    public void setAdminUnread(boolean adminUnread) { this.adminUnread = adminUnread; }
    public boolean isCustomerUnread() { return customerUnread; }
    public void setCustomerUnread(boolean customerUnread) { this.customerUnread = customerUnread; }
    public Instant getDeviceReceivedAt() { return deviceReceivedAt; }
    public void setDeviceReceivedAt(Instant deviceReceivedAt) { this.deviceReceivedAt = deviceReceivedAt; }
    public Instant getPricedAt() { return pricedAt; }
    public void setPricedAt(Instant pricedAt) { this.pricedAt = pricedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
