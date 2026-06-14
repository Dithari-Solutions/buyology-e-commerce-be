package com.buyology.ecommerce.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin payload to create/update a spec code. On update the {@code code} is ignored (immutable). */
public class SpecCodeRequest {

    @NotBlank
    @Size(max = 50)
    private String code;

    @Size(max = 100)
    private String labelEn;

    @Size(max = 100)
    private String labelAz;

    @Size(max = 100)
    private String labelAr;

    private boolean filterable = false;

    private Integer displayOrder;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabelEn() { return labelEn; }
    public void setLabelEn(String labelEn) { this.labelEn = labelEn; }
    public String getLabelAz() { return labelAz; }
    public void setLabelAz(String labelAz) { this.labelAz = labelAz; }
    public String getLabelAr() { return labelAr; }
    public void setLabelAr(String labelAr) { this.labelAr = labelAr; }
    public boolean isFilterable() { return filterable; }
    public void setFilterable(boolean filterable) { this.filterable = filterable; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
