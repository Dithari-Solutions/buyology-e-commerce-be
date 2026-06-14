package com.buyology.ecommerce.product.dto;

import jakarta.validation.constraints.NotBlank;

/** Edit a global spec group's display names. The code is immutable. */
public class UpdateSpecGroupRequest {

    @NotBlank
    private String nameAz;

    @NotBlank
    private String nameEn;

    @NotBlank
    private String nameAr;

    public String getNameAz() { return nameAz; }
    public void setNameAz(String nameAz) { this.nameAz = nameAz; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public String getNameAr() { return nameAr; }
    public void setNameAr(String nameAr) { this.nameAr = nameAr; }
}
