package com.buyology.ecommerce.product.dto;

import com.buyology.ecommerce.product.domain.SpecCode;

import java.util.UUID;

public class SpecCodeResponse {
    private UUID id;
    private String code;
    private String labelEn;
    private String labelAz;
    private String labelAr;
    private boolean filterable;
    private int displayOrder;

    public static SpecCodeResponse from(SpecCode c) {
        SpecCodeResponse r = new SpecCodeResponse();
        r.id = c.getId();
        r.code = c.getCode();
        r.labelEn = c.getLabelEn();
        r.labelAz = c.getLabelAz();
        r.labelAr = c.getLabelAr();
        r.filterable = c.isFilterable();
        r.displayOrder = c.getDisplayOrder();
        return r;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getLabelEn() { return labelEn; }
    public String getLabelAz() { return labelAz; }
    public String getLabelAr() { return labelAr; }
    public boolean isFilterable() { return filterable; }
    public int getDisplayOrder() { return displayOrder; }
}
