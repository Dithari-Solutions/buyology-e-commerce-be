package com.buyology.ecommerce.repair.dto;

import com.buyology.ecommerce.store.domain.StoreLocation;

import java.util.UUID;

/** A store branch the customer can choose to drop the device at (or collect it from). */
public class StoreLocationOptionResponse {

    private UUID id;
    private String branchName;
    private String address;
    private String city;
    private String country;

    public static StoreLocationOptionResponse from(StoreLocation l) {
        StoreLocationOptionResponse dto = new StoreLocationOptionResponse();
        dto.id = l.getId();
        dto.branchName = l.getBranchName();
        dto.address = l.getAddress();
        dto.city = l.getCity();
        dto.country = l.getCountry();
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
