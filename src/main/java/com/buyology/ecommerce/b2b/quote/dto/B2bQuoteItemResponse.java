package com.buyology.ecommerce.b2b.quote.dto;

import com.buyology.ecommerce.b2b.quote.domain.B2bQuoteItem;

import java.math.BigDecimal;
import java.util.UUID;

/** A single B2B quote/cart line as returned to the member and procurement. */
public class B2bQuoteItemResponse {

    private UUID id;
    private UUID storeProductId;
    private UUID productId;
    private UUID variantId;
    private Integer quantity;
    private String productTitle;
    private String sku;

    /** Null until the quote is priced. */
    private BigDecimal quotedUnitPrice;

    /** quotedUnitPrice * quantity, or null while unpriced. */
    private BigDecimal quotedLineTotal;

    /** True when quantity < minQtyPerLine — the storefront shows a warning and blocks submit. */
    private boolean belowMinimum;

    public static B2bQuoteItemResponse from(B2bQuoteItem i, int minQtyPerLine) {
        B2bQuoteItemResponse r = new B2bQuoteItemResponse();
        r.id = i.getId();
        r.storeProductId = i.getStoreProductId();
        r.productId = i.getProductId();
        r.variantId = i.getVariantId();
        r.quantity = i.getQuantity();
        r.productTitle = i.getProductTitle();
        r.sku = i.getSku();
        r.quotedUnitPrice = i.getQuotedUnitPrice();
        r.quotedLineTotal = i.quotedLineTotal();
        r.belowMinimum = i.getQuantity() != null && i.getQuantity() < minQtyPerLine;
        return r;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getStoreProductId() { return storeProductId; }
    public void setStoreProductId(UUID storeProductId) { this.storeProductId = storeProductId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getProductTitle() { return productTitle; }
    public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public BigDecimal getQuotedUnitPrice() { return quotedUnitPrice; }
    public void setQuotedUnitPrice(BigDecimal quotedUnitPrice) { this.quotedUnitPrice = quotedUnitPrice; }
    public BigDecimal getQuotedLineTotal() { return quotedLineTotal; }
    public void setQuotedLineTotal(BigDecimal quotedLineTotal) { this.quotedLineTotal = quotedLineTotal; }
    public boolean isBelowMinimum() { return belowMinimum; }
    public void setBelowMinimum(boolean belowMinimum) { this.belowMinimum = belowMinimum; }
}
