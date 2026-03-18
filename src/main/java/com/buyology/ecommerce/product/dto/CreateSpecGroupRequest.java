package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(description = "Spec group to attach to a product. Provide globalSpecGroupId to reference an existing global spec group, " +
        "or provide code + nameAz/nameEn/nameAr to create (or reuse by code) a global spec group on the fly.")
public class CreateSpecGroupRequest {

    @Schema(description = "ID of an existing global spec group. If provided, the spec group is looked up by this ID. " +
            "If omitted, code + name fields are required to find or create the global spec group.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID globalSpecGroupId;

    @Schema(description = "Machine-readable code for the spec group (e.g. 'ram', 'storage'). " +
            "Required when globalSpecGroupId is omitted. If a global spec group with this code already exists it will be reused.",
            example = "battery_capacity")
    private String code;

    @Schema(description = "Spec group name in Azerbaijani. Required when globalSpecGroupId is omitted.", example = "Batareya tutumu")
    private String nameAz;

    @Schema(description = "Spec group name in English. Required when globalSpecGroupId is omitted.", example = "Battery Capacity")
    private String nameEn;

    @Schema(description = "Spec group name in Arabic. Required when globalSpecGroupId is omitted.", example = "سعة البطارية")
    private String nameAr;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNameAz() {
        return nameAz;
    }

    public void setNameAz(String nameAz) {
        this.nameAz = nameAz;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getNameAr() {
        return nameAr;
    }

    public void setNameAr(String nameAr) {
        this.nameAr = nameAr;
    }

    public List<CreateSpecOptionRequest> getOptions() {
        return options;
    }

    public void setOptions(List<CreateSpecOptionRequest> options) {
        this.options = options;
    }
}
