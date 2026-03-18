package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "Spec group to attach to a product — must reference an existing global spec group")
public class CreateSpecGroupRequest {

    @NotNull(message = "globalSpecGroupId is required")
    @Schema(description = "ID of the global spec group (e.g. RAM, Storage) to attach to this product", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID globalSpecGroupId;

    @Valid
    @NotEmpty(message = "At least one spec option is required per group")
    @Schema(description = "List of options belonging to this group")
    private List<CreateSpecOptionRequest> options;

    // ========================
    // Getters & Setters
    // ========================

    public UUID getGlobalSpecGroupId() {
        return globalSpecGroupId;
    }

    public void setGlobalSpecGroupId(UUID globalSpecGroupId) {
        this.globalSpecGroupId = globalSpecGroupId;
    }

    public List<CreateSpecOptionRequest> getOptions() {
        return options;
    }

    public void setOptions(List<CreateSpecOptionRequest> options) {
        this.options = options;
    }
}
