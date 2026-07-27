package com.buyology.ecommerce.b2b.quote.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single line on a {@link B2bQuote}. The (quote, store_product, variant) tuple is unique
 * (uq_b2b_quote_items_line) so adding the same product twice upserts the quantity instead of
 * duplicating. {@code quotedUnitPrice} is null until procurement prices the quote (QUOTED).
 *
 * Column names mirror V18 (b2b_quote_items) exactly.
 */
@Entity
@Table(name = "b2b_quote_items", indexes = {
        @Index(name = "idx_b2b_quote_items_quote", columnList = "quote_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_b2b_quote_items_line", columnNames = { "quote_id", "store_product_id", "variant_id" })
})
public class B2bQuoteItem {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private B2bQuote quote;

    @Column(name = "store_product_id", nullable = false)
    private UUID storeProductId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** Null until the quote is priced by procurement. */
    @Column(name = "quoted_unit_price", precision = 12, scale = 2)
    private BigDecimal quotedUnitPrice;

    /** Snapshot of the product title at add-to-cart time (for the procurement/member view). */
    @Column(name = "product_title", length = 255)
    private String productTitle;

    @Column(name = "sku", length = 255)
    private String sku;

    /** Lead time for this line, set by procurement at pricing (e.g. "2–3 weeks"). */
    @Column(name = "lead_time", length = 255)
    private String leadTime;

    /** Optional line description set by procurement at pricing (shown on the order email). */
    @Column(name = "description", columnDefinition = "text")
    private String description;

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

    /** Line total once quoted (quotedUnitPrice * quantity), or null while unpriced. */
    public BigDecimal quotedLineTotal() {
        if (quotedUnitPrice == null || quantity == null) return null;
        return quotedUnitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public B2bQuote getQuote() { return quote; }
    public void setQuote(B2bQuote quote) { this.quote = quote; }

    public UUID getStoreProductId() { return storeProductId; }
    public void setStoreProductId(UUID storeProductId) { this.storeProductId = storeProductId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getQuotedUnitPrice() { return quotedUnitPrice; }
    public void setQuotedUnitPrice(BigDecimal quotedUnitPrice) { this.quotedUnitPrice = quotedUnitPrice; }

    public String getProductTitle() { return productTitle; }
    public void setProductTitle(String productTitle) { this.productTitle = productTitle; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getLeadTime() { return leadTime; }
    public void setLeadTime(String leadTime) { this.leadTime = leadTime; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
