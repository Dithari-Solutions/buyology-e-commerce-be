package com.buyology.ecommerce.promo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ValidatePromoCodeRequest {

    @NotBlank
    private String code;

    @NotNull
    private BigDecimal orderAmount;

    private List<UUID> productIds;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BigDecimal getOrderAmount() { return orderAmount; }
    public void setOrderAmount(BigDecimal orderAmount) { this.orderAmount = orderAmount; }
    public List<UUID> getProductIds() { return productIds; }
    public void setProductIds(List<UUID> productIds) { this.productIds = productIds; }
}
