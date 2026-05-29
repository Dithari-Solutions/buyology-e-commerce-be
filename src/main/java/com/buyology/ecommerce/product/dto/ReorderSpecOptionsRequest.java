package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(description = "New ordering of a spec group's options. The list position becomes the display order.")
public class ReorderSpecOptionsRequest {

    @NotEmpty(message = "optionIds must not be empty")
    @Schema(description = "Option ids in the desired display order (index 0 shown first)")
    private List<UUID> optionIds;

    public List<UUID> getOptionIds() { return optionIds; }
    public void setOptionIds(List<UUID> optionIds) { this.optionIds = optionIds; }
}
