package com.buyology.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class CartItemSpecSelectionResponse {

    private UUID specOptionId;
    private String groupCode;
    private String value;
    private String unit;
    private BigDecimal additionalPrice;
    private String colorCode;

    public CartItemSpecSelectionResponse() {
    }

    public UUID getSpecOptionId() { return specOptionId; }
    public void setSpecOptionId(UUID specOptionId) { this.specOptionId = specOptionId; }

    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getAdditionalPrice() { return additionalPrice; }
    public void setAdditionalPrice(BigDecimal additionalPrice) { this.additionalPrice = additionalPrice; }

    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
}
