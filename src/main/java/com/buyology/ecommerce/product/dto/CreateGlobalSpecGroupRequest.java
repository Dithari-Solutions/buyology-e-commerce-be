package com.buyology.ecommerce.product.dto;

import com.buyology.ecommerce.common.enums.SpecUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request to create a global spec group with its predefined options")
public class CreateGlobalSpecGroupRequest {

    @NotBlank(message = "Code is required (e.g. ram, storage, processor)")
    @Size(max = 50)
    @Schema(example = "ram", description = "Unique machine-readable code — must match filter param names")
    private String code;

    @NotBlank(message = "Name (AZ) is required")
    @Schema(example = "RAM")
    private String nameAz;

    @NotBlank(message = "Name (EN) is required")
    @Schema(example = "RAM")
    private String nameEn;

    @NotBlank(message = "Name (AR) is required")
    @Schema(example = "ذاكرة الوصول العشوائي")
    private String nameAr;

    @Schema(description = "Initial list of options to add to this group (can also be added later)")
    private List<OptionRequest> options;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNameAz() { return nameAz; }
    public void setNameAz(String nameAz) { this.nameAz = nameAz; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public String getNameAr() { return nameAr; }
    public void setNameAr(String nameAr) { this.nameAr = nameAr; }

    public List<OptionRequest> getOptions() { return options; }
    public void setOptions(List<OptionRequest> options) { this.options = options; }

    public static class OptionRequest {

        @NotBlank
        private String valueAz;

        @NotBlank
        private String valueEn;

        @NotBlank
        private String valueAr;

        private SpecUnit unit;

        public String getValueAz() { return valueAz; }
        public void setValueAz(String valueAz) { this.valueAz = valueAz; }

        public String getValueEn() { return valueEn; }
        public void setValueEn(String valueEn) { this.valueEn = valueEn; }

        public String getValueAr() { return valueAr; }
        public void setValueAr(String valueAr) { this.valueAr = valueAr; }

        public SpecUnit getUnit() { return unit; }
        public void setUnit(SpecUnit unit) { this.unit = unit; }
    }
}
