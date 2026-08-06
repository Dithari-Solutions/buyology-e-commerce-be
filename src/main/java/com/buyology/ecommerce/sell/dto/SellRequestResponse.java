package com.buyology.ecommerce.sell.dto;

import com.buyology.ecommerce.sell.domain.DeviceCondition;
import com.buyology.ecommerce.sell.domain.SellDeliveryMethod;
import com.buyology.ecommerce.sell.domain.SellPayoutMethod;
import com.buyology.ecommerce.sell.domain.SellRequest;
import com.buyology.ecommerce.sell.domain.SellStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A sell (trade-in) request as returned to the customer and to procurement.
 * {@code imageUrls} are presigned GET urls derived from the stored keys on read (the raw keys are
 * never exposed). {@code storeBranchName} is resolved from the chosen store location, when any.
 */
public class SellRequestResponse {

    private UUID id;
    private String reference;
    private String productName;
    private String brand;
    private String model;
    private LocalDate purchaseDate;
    private DeviceCondition deviceCondition;
    private String description;
    private List<String> imageUrls;

    private SellStatus status;
    private SellDeliveryMethod inboundDeliveryMethod;
    private UUID storeLocationId;
    private String storeBranchName;
    private String storeAddress;
    private SellDeliveryMethod returnDeliveryMethod;

    private BigDecimal courierFeeAmount;
    private String courierFeeCurrency;
    private boolean courierFeePaid;
    /** Money taken for a courier pickup the customer then swapped for a store drop-off. */
    private boolean courierFeeRefundDue;
    private SellDeliveryMethod previousInboundDeliveryMethod;
    private Instant inboundDeliveryChangedAt;

    private BigDecimal offerPrice;
    private String offerPriceCurrency;
    private String offerValidFor;
    private DeviceCondition inspectedCondition;

    private SellPayoutMethod payoutMethod;
    private Instant paidOutAt;

    // ── AI preliminary valuation (advisory, never the binding offer) ─────────
    /** Always priced in AED for the UAE second-hand market; see aiEstimateCurrency. */
    private BigDecimal aiEstimateMinPrice;
    private BigDecimal aiEstimateMaxPrice;
    private String aiEstimateCurrency;
    private String aiEstimateConfidence;
    private String aiEstimateSummary;
    private String aiEstimateCondition;
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
    private Instant offeredAt;
    private Instant submittedAt;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Build a response. {@code imageUrls} are already-presigned urls; {@code storeBranchName}/
     * {@code storeAddress} are the resolved chosen-branch details (nullable).
     */
    public static SellRequestResponse from(SellRequest r, List<String> imageUrls,
                                           String storeBranchName, String storeAddress) {
        SellRequestResponse dto = new SellRequestResponse();
        dto.id = r.getId();
        dto.reference = r.getReference();
        dto.productName = r.getProductName();
        dto.brand = r.getBrand();
        dto.model = r.getModel();
        dto.purchaseDate = r.getPurchaseDate();
        dto.deviceCondition = r.getDeviceCondition();
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
        dto.offerPrice = r.getOfferPrice();
        dto.offerPriceCurrency = r.getOfferPriceCurrency();
        dto.offerValidFor = r.getOfferValidFor();
        dto.inspectedCondition = r.getInspectedCondition();
        dto.payoutMethod = r.getPayoutMethod();
        dto.paidOutAt = r.getPaidOutAt();
        dto.aiEstimateMinPrice = r.getAiEstimateMinPrice();
        dto.aiEstimateMaxPrice = r.getAiEstimateMaxPrice();
        dto.aiEstimateCurrency = r.getAiEstimateCurrency();
        dto.aiEstimateConfidence = r.getAiEstimateConfidence();
        dto.aiEstimateSummary = r.getAiEstimateSummary();
        dto.aiEstimateCondition = r.getAiEstimateCondition();
        dto.aiEstimatedAt = r.getAiEstimatedAt();
        dto.adminNote = r.getAdminNote();
        dto.contactEmail = r.getContactEmail();
        dto.contactPhone = r.getContactPhone();
        dto.adminUnread = r.isAdminUnread();
        dto.customerUnread = r.isCustomerUnread();
        dto.deviceReceivedAt = r.getDeviceReceivedAt();
        dto.offeredAt = r.getOfferedAt();
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
    public DeviceCondition getDeviceCondition() { return deviceCondition; }
    public void setDeviceCondition(DeviceCondition deviceCondition) { this.deviceCondition = deviceCondition; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public SellStatus getStatus() { return status; }
    public void setStatus(SellStatus status) { this.status = status; }
    public SellDeliveryMethod getInboundDeliveryMethod() { return inboundDeliveryMethod; }
    public void setInboundDeliveryMethod(SellDeliveryMethod inboundDeliveryMethod) { this.inboundDeliveryMethod = inboundDeliveryMethod; }
    public UUID getStoreLocationId() { return storeLocationId; }
    public void setStoreLocationId(UUID storeLocationId) { this.storeLocationId = storeLocationId; }
    public String getStoreBranchName() { return storeBranchName; }
    public void setStoreBranchName(String storeBranchName) { this.storeBranchName = storeBranchName; }
    public String getStoreAddress() { return storeAddress; }
    public void setStoreAddress(String storeAddress) { this.storeAddress = storeAddress; }
    public SellDeliveryMethod getReturnDeliveryMethod() { return returnDeliveryMethod; }
    public void setReturnDeliveryMethod(SellDeliveryMethod returnDeliveryMethod) { this.returnDeliveryMethod = returnDeliveryMethod; }
    public BigDecimal getCourierFeeAmount() { return courierFeeAmount; }
    public void setCourierFeeAmount(BigDecimal courierFeeAmount) { this.courierFeeAmount = courierFeeAmount; }
    public String getCourierFeeCurrency() { return courierFeeCurrency; }
    public void setCourierFeeCurrency(String courierFeeCurrency) { this.courierFeeCurrency = courierFeeCurrency; }
    public boolean isCourierFeePaid() { return courierFeePaid; }
    public void setCourierFeePaid(boolean courierFeePaid) { this.courierFeePaid = courierFeePaid; }
    public boolean isCourierFeeRefundDue() { return courierFeeRefundDue; }
    public void setCourierFeeRefundDue(boolean v) { this.courierFeeRefundDue = v; }
    public SellDeliveryMethod getPreviousInboundDeliveryMethod() { return previousInboundDeliveryMethod; }
    public void setPreviousInboundDeliveryMethod(SellDeliveryMethod v) { this.previousInboundDeliveryMethod = v; }
    public Instant getInboundDeliveryChangedAt() { return inboundDeliveryChangedAt; }
    public void setInboundDeliveryChangedAt(Instant v) { this.inboundDeliveryChangedAt = v; }
    public BigDecimal getOfferPrice() { return offerPrice; }
    public void setOfferPrice(BigDecimal offerPrice) { this.offerPrice = offerPrice; }
    public String getOfferPriceCurrency() { return offerPriceCurrency; }
    public void setOfferPriceCurrency(String offerPriceCurrency) { this.offerPriceCurrency = offerPriceCurrency; }
    public String getOfferValidFor() { return offerValidFor; }
    public void setOfferValidFor(String offerValidFor) { this.offerValidFor = offerValidFor; }
    public DeviceCondition getInspectedCondition() { return inspectedCondition; }
    public void setInspectedCondition(DeviceCondition inspectedCondition) { this.inspectedCondition = inspectedCondition; }
    public SellPayoutMethod getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(SellPayoutMethod payoutMethod) { this.payoutMethod = payoutMethod; }
    public Instant getPaidOutAt() { return paidOutAt; }
    public void setPaidOutAt(Instant paidOutAt) { this.paidOutAt = paidOutAt; }

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
    public String getAiEstimateCondition() { return aiEstimateCondition; }
    public void setAiEstimateCondition(String aiEstimateCondition) { this.aiEstimateCondition = aiEstimateCondition; }
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
    public Instant getOfferedAt() { return offeredAt; }
    public void setOfferedAt(Instant offeredAt) { this.offeredAt = offeredAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
