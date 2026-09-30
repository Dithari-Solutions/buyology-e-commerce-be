package com.buyology.ecommerce.store.dto;

import com.buyology.ecommerce.product.domain.Product.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Put a batch of products on the flash sale: a set of store listings, a discount, and an end.
 *
 * <p>The discount and the window are given ONCE for the whole batch, because that is what a flash
 * sale is — one campaign over many products. Any item may override them, which is what makes
 * "everything 20% off, but the laptop at a fixed 3,499" a single call instead of two.
 *
 * <p>Two ways to give each date, and exactly one of them is the normal one:
 * <ul>
 *   <li>{@code endsOn} — a CALENDAR DATE, read in the shop's own timezone (Asia/Dubai). "Ends
 *       31 March" means the end of that day in Dubai, which is what an admin typing it means and
 *       what the dashboard should send.</li>
 *   <li>{@code endsAt} — an exact instant, for a sale that ends at 8pm rather than at midnight.</li>
 * </ul>
 * When both are sent the instant wins, since it is the more specific of the two.
 */
@Schema(description = "Put a batch of store products on the flash sale with a discount and an end date")
public class FlashSaleRequest {

    // Bean-validation rather than a check in the service, for the two things a validator can decide on
    // its own: that there is something to discount, and that a single call cannot ask for an unbounded
    // batch (every item is resolved, validated and saved inside ONE transaction). What is left to
    // FlashSalePolicy is everything that needs the listing's own store price, the request's other
    // fields or the clock — a percentage over 100, a fixed price above the item's price, an end before
    // its start. Those cannot be expressed here, and splitting the rules across both would be how one
    // of them ends up disagreeing with the other.
    @NotEmpty(message = "items must contain at least one product")
    @Size(max = 500, message = "A single flash sale may cover at most 500 products")
    @Valid
    @ArraySchema(schema = @Schema(description = "The store listings to put on sale"))
    private List<FlashSaleItemRequest> items;

    @Schema(description = "Discount type for every item that does not override it — FIXED sets the sale price, "
            + "PERCENTAGE takes that many percent off the store price",
            allowableValues = {"FIXED", "PERCENTAGE"}, example = "PERCENTAGE")
    private DiscountType discountType;

    // Not @NotNull: a batch may legitimately omit both discount fields when every item overrides them.
    // inclusive = false because a discount of zero discounts nothing — it would put a full-price
    // product in the rail with a countdown on it.
    @DecimalMin(value = "0.00", inclusive = false, message = "discountValue must be greater than zero")
    @Schema(description = "Discount amount for every item that does not override it: the sale price when FIXED, "
            + "a percentage 0–100 when PERCENTAGE", example = "20")
    private BigDecimal discountValue;

    @Schema(description = "Calendar date the sale starts, in Asia/Dubai. Omit to start it immediately.",
            example = "2026-03-25")
    private LocalDate startsOn;

    @Schema(description = "Exact instant the sale starts. Wins over startsOn when both are sent.")
    private Instant startsAt;

    @Schema(description = "Calendar date the sale ends, in Asia/Dubai — inclusive, so the whole of that day "
            + "is on sale. Required unless endsAt is sent.", example = "2026-03-31")
    private LocalDate endsOn;

    @Schema(description = "Exact instant the sale ends (inclusive). Wins over endsOn when both are sent.")
    private Instant endsAt;

    // Getters & Setters

    public List<FlashSaleItemRequest> getItems() { return items; }
    public void setItems(List<FlashSaleItemRequest> items) { this.items = items; }

    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }

    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }

    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }

    /** One store listing on the sale — identified directly, or by the store/product pair. */
    @Schema(description = "One store listing to put on the flash sale")
    public static class FlashSaleItemRequest {

        @Schema(description = "The store_products row to discount. Send this, or storeId + productId.")
        private UUID storeProductId;

        @Schema(description = "Store holding the listing — with productId, an alternative to storeProductId. "
                + "This is the pair the dashboard already has on screen.")
        private UUID storeId;

        @Schema(description = "Product to discount in that store")
        private UUID productId;

        @Schema(description = "Overrides the batch discountType for this one item — send discountValue too")
        private DiscountType discountType;

        @DecimalMin(value = "0.00", inclusive = false, message = "discountValue must be greater than zero")
        @Schema(description = "Overrides the batch discountValue for this one item. Send it WITH discountType: "
                + "the two are one decision, and an item that overrides only one of them is refused.",
                example = "3499.00")
        private BigDecimal discountValue;

        @Schema(description = "Overrides the batch end date for this one item (Asia/Dubai calendar date)")
        private LocalDate endsOn;

        @Schema(description = "Overrides the batch end instant for this one item")
        private Instant endsAt;

        public UUID getStoreProductId() { return storeProductId; }
        public void setStoreProductId(UUID storeProductId) { this.storeProductId = storeProductId; }

        public UUID getStoreId() { return storeId; }
        public void setStoreId(UUID storeId) { this.storeId = storeId; }

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public DiscountType getDiscountType() { return discountType; }
        public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }

        public BigDecimal getDiscountValue() { return discountValue; }
        public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

        public LocalDate getEndsOn() { return endsOn; }
        public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }

        public Instant getEndsAt() { return endsAt; }
        public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    }
}
