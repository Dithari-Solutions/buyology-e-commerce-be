package com.buyology.ecommerce.b2b.quote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Procurement prices every line of a SUBMITTED quote and sets a validity window. */
public class PriceQuoteRequest {

    @NotEmpty
    @Valid
    private List<LinePrice> items;

    /** When the quoted prices expire — member must accept before this. */
    @NotNull
    private Instant validUntil;

    private String procurementNote;

    /** Payment terms shown to the member and on the order email (e.g. "50% advance…"). */
    private String paymentTerms;

    /** Terms & conditions shown to the member and on the order email. */
    private String termsAndConditions;

    public List<LinePrice> getItems() { return items; }
    public void setItems(List<LinePrice> items) { this.items = items; }

    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }

    public String getProcurementNote() { return procurementNote; }
    public void setProcurementNote(String procurementNote) { this.procurementNote = procurementNote; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public String getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; }

    /** Per-line price entry: which item, at what unit price, with an optional lead time / description. */
    public static class LinePrice {
        @NotNull
        private UUID itemId;

        @NotNull
        @DecimalMin("0.00")
        private BigDecimal unitPrice;

        private String leadTime;

        private String description;

        public UUID getItemId() { return itemId; }
        public void setItemId(UUID itemId) { this.itemId = itemId; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public String getLeadTime() { return leadTime; }
        public void setLeadTime(String leadTime) { this.leadTime = leadTime; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
