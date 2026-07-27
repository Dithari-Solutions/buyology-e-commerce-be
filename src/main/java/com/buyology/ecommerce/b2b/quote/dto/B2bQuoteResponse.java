package com.buyology.ecommerce.b2b.quote.dto;

import com.buyology.ecommerce.b2b.quote.domain.B2bQuote;
import com.buyology.ecommerce.b2b.quote.domain.B2bQuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A B2B quote (or the DRAFT B2B cart) with its lines. Carries the RFQ constant
 * {@code minQtyPerLine} and a convenience {@code belowMinimum} flag so the storefront
 * can render the per-line warning and gate submit/checkout without re-deriving the rule.
 */
public class B2bQuoteResponse {

    private UUID id;
    private B2bQuoteStatus status;
    private String countryCode;
    private String currency;
    private String memberNote;
    private String procurementNote;
    private String paymentTerms;
    private String termsAndConditions;
    private Instant submittedAt;
    private Instant quotedAt;
    private Instant validUntil;
    private Instant acceptedAt;
    private UUID orderId;

    /** How the member paid: "BANK_TRANSFER" once they submit a bank-transfer proof. */
    private String paymentMethod;
    /** Presigned URL of the uploaded bank-transfer proof (set by the service). Null if none. */
    private String proofOfPaymentFileUrl;
    private Instant proofUploadedAt;
    private Instant paymentVerifiedAt;

    private Instant createdAt;
    private Instant updatedAt;

    private List<B2bQuoteItemResponse> items;

    /** RFQ rule surfaced to the client — B2B_MIN_QTY_PER_LINE (5). */
    private int minQtyPerLine;

    /** True when any line is below minQtyPerLine — blocks submit/checkout on the client. */
    private boolean belowMinimum;

    /** Sum of quoted line totals once the quote is priced; null while any line is unpriced. */
    private BigDecimal quotedSubtotal;

    public static B2bQuoteResponse from(B2bQuote q, List<com.buyology.ecommerce.b2b.quote.domain.B2bQuoteItem> lines,
                                        int minQtyPerLine) {
        B2bQuoteResponse r = new B2bQuoteResponse();
        r.id = q.getId();
        r.status = q.getStatus();
        r.countryCode = q.getCountryCode();
        r.currency = q.getCurrency();
        r.memberNote = q.getMemberNote();
        r.procurementNote = q.getProcurementNote();
        r.paymentTerms = q.getPaymentTerms();
        r.termsAndConditions = q.getTermsAndConditions();
        r.submittedAt = q.getSubmittedAt();
        r.quotedAt = q.getQuotedAt();
        r.validUntil = q.getValidUntil();
        r.acceptedAt = q.getAcceptedAt();
        r.orderId = q.getOrderId();
        r.paymentMethod = q.getPaymentMethod();
        r.proofUploadedAt = q.getProofUploadedAt();
        r.paymentVerifiedAt = q.getPaymentVerifiedAt();
        // proofOfPaymentFileUrl is presigned and set by the service layer (needs Contabo).
        r.createdAt = q.getCreatedAt();
        r.updatedAt = q.getUpdatedAt();
        r.minQtyPerLine = minQtyPerLine;
        r.items = lines.stream()
                .map(i -> B2bQuoteItemResponse.from(i, minQtyPerLine))
                .collect(Collectors.toList());
        r.belowMinimum = r.items.stream().anyMatch(B2bQuoteItemResponse::isBelowMinimum);

        // Quoted subtotal — only meaningful once every line has a quoted price.
        boolean allPriced = !lines.isEmpty() && lines.stream().allMatch(i -> i.getQuotedUnitPrice() != null);
        if (allPriced) {
            r.quotedSubtotal = lines.stream()
                    .map(com.buyology.ecommerce.b2b.quote.domain.B2bQuoteItem::quotedLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return r;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public B2bQuoteStatus getStatus() { return status; }
    public void setStatus(B2bQuoteStatus status) { this.status = status; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMemberNote() { return memberNote; }
    public void setMemberNote(String memberNote) { this.memberNote = memberNote; }
    public String getProcurementNote() { return procurementNote; }
    public void setProcurementNote(String procurementNote) { this.procurementNote = procurementNote; }
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    public String getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getProofOfPaymentFileUrl() { return proofOfPaymentFileUrl; }
    public void setProofOfPaymentFileUrl(String proofOfPaymentFileUrl) { this.proofOfPaymentFileUrl = proofOfPaymentFileUrl; }
    public Instant getProofUploadedAt() { return proofUploadedAt; }
    public void setProofUploadedAt(Instant proofUploadedAt) { this.proofUploadedAt = proofUploadedAt; }
    public Instant getPaymentVerifiedAt() { return paymentVerifiedAt; }
    public void setPaymentVerifiedAt(Instant paymentVerifiedAt) { this.paymentVerifiedAt = paymentVerifiedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getQuotedAt() { return quotedAt; }
    public void setQuotedAt(Instant quotedAt) { this.quotedAt = quotedAt; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<B2bQuoteItemResponse> getItems() { return items; }
    public void setItems(List<B2bQuoteItemResponse> items) { this.items = items; }
    public int getMinQtyPerLine() { return minQtyPerLine; }
    public void setMinQtyPerLine(int minQtyPerLine) { this.minQtyPerLine = minQtyPerLine; }
    public boolean isBelowMinimum() { return belowMinimum; }
    public void setBelowMinimum(boolean belowMinimum) { this.belowMinimum = belowMinimum; }
    public BigDecimal getQuotedSubtotal() { return quotedSubtotal; }
    public void setQuotedSubtotal(BigDecimal quotedSubtotal) { this.quotedSubtotal = quotedSubtotal; }
}
