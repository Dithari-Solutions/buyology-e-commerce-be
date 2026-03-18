package com.buyology.ecommerce.store.dto;

import java.time.LocalTime;

public class UpdateOperatingHoursRequest {

    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean isClosed;

    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }

    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }

    public Boolean getIsClosed() { return isClosed; }
    public void setIsClosed(Boolean isClosed) { this.isClosed = isClosed; }
}
