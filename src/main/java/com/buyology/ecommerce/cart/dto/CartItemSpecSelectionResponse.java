package com.buyology.ecommerce.cart.dto;

import java.util.UUID;

public class CartItemSpecSelectionResponse {
    private UUID specOptionId;
    private String groupCode;
    private String groupName;
    private String value;
    private String unit;
    private String colorCode;

    public CartItemSpecSelectionResponse() {}

    public CartItemSpecSelectionResponse(UUID specOptionId, String groupCode, String groupName, String value, String unit, String colorCode) {
        this.specOptionId = specOptionId;
        this.groupCode = groupCode;
        this.groupName = groupName;
        this.value = value;
        this.unit = unit;
        this.colorCode = colorCode;
    }

    public UUID getSpecOptionId() { return specOptionId; }
    public void setSpecOptionId(UUID specOptionId) { this.specOptionId = specOptionId; }

    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getColorCode() { return colorCode; }
    public void setColorCode(String colorCode) { this.colorCode = colorCode; }
}
