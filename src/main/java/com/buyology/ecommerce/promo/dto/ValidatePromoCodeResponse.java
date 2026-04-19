package com.buyology.ecommerce.promo.dto;

import com.buyology.ecommerce.promo.domain.DiscountType;
import java.math.BigDecimal;
import java.util.UUID;

public class ValidatePromoCodeResponse {
    private boolean valid;
    private String message;
    private UUID promoCodeId;
    private BigDecimal discountAmount;
    private DiscountType discountType;
    private BigDecimal discountValue;

    public static ValidatePromoCodeResponse invalid(String message) {
        ValidatePromoCodeResponse r = new ValidatePromoCodeResponse();
        r.valid = false;
        r.message = message;
        return r;
    }

    public static ValidatePromoCodeResponse valid(UUID promoCodeId, BigDecimal discountAmount, DiscountType type, BigDecimal value) {
        ValidatePromoCodeResponse r = new ValidatePromoCodeResponse();
        r.valid = true;
        r.message = "Promo code applied successfully";
        r.promoCodeId = promoCodeId;
        r.discountAmount = discountAmount;
        r.discountType = type;
        r.discountValue = value;
        return r;
    }

    public boolean isValid() { return valid; }
    public String getMessage() { return message; }
    public UUID getPromoCodeId() { return promoCodeId; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public DiscountType getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
}
