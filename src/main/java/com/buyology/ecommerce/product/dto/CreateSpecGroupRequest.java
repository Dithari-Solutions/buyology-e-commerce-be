package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Inline spec group to create during product creation (e.g. RAM, Storage)")
public class CreateSpecGroupRequest {

    @NotBlank(message = "Spec group code is required")
    @Schema(description = "Unique code for the spec group", example = "ram")
    private String code;

    @NotBlank(message = "Spec group name (AZ) is required")
    @Schema(description = "Group name in Azerbaijani", example = "RAM")
    private String nameAz;

    @NotBlank(message = "Spec group name (EN) is required")
    @Schema(description = "Group name in English", example = "RAM")
    private String nameEn;

    @NotBlank(message = "Spec group name (AR) is required")
    @Schema(description = "Group name in Arabic", example = "ذاكرة الوصول العشوائي")
    private String nameAr;

    @Valid
    @NotEmpty(message = "At least one spec option is required per group")
    @Schema(description = "List of options belonging to this group")
    private List<CreateSpecOptionRequest> options;

    // ========================
    // Getters & Setters
    // ========================

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
