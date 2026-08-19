package com.buyology.ecommerce.assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A product the assistant referred to, in the shape the chat widget renders as a card.
 *
 * <p>Prices come from the same country/currency resolution the storefront uses, so a price shown in
 * the chat bubble is the price on the product page — the assistant never restates a number the
 * model produced.
 */
@Schema(description = "A product referenced by the assistant, ready to render as a card")
public class AssistantProductCard {

    private UUID id;
    private String title;
    private String slug;
    private String brandName;
    private String availabilityStatus;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal price;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal originalPrice;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String currency;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String imageUrl;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal averageRating;

    private Boolean isRefurbished;

    public AssistantProductCard() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }

    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public BigDecimal getAverageRating() { return averageRating; }
    public void setAverageRating(BigDecimal averageRating) { this.averageRating = averageRating; }

    public Boolean getIsRefurbished() { return isRefurbished; }
    public void setIsRefurbished(Boolean isRefurbished) { this.isRefurbished = isRefurbished; }
}
