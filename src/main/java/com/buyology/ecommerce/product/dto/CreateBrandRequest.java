package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating a new brand")
public class CreateBrandRequest {

    @NotBlank(message = "Brand name (AZ) is required")
    @Size(max = 100)
    @Schema(example = "Apple")
    private String nameAz;

    @NotBlank(message = "Brand name (EN) is required")
    @Size(max = 100)
    @Schema(example = "Apple")
    private String nameEn;

    @NotBlank(message = "Brand name (AR) is required")
    @Size(max = 100)
    @Schema(example = "Apple")
    private String nameAr;

    public String getNameAz() { return nameAz; }
    public void setNameAz(String nameAz) { this.nameAz = nameAz; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public String getNameAr() { return nameAr; }
    public void setNameAr(String nameAr) { this.nameAr = nameAr; }
}
