package com.buyology.ecommerce.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreateStoreLocationRequest {

    @NotBlank(message = "Branch name is required")
    @Size(max = 255)
    private String branchName;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @NotBlank(message = "Country code is required (ISO 3-letter)")
    @Size(min = 2, max = 3)
    private String country;

    @Size(max = 20)
    private String postalCode;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    private Boolean isPrimary = false;

    @NotEmpty(message = "Operating hours are required (provide all 7 days)")
    @Valid
    private List<CreateOperatingHoursRequest> operatingHours;

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    public List<CreateOperatingHoursRequest> getOperatingHours() { return operatingHours; }
    public void setOperatingHours(List<CreateOperatingHoursRequest> operatingHours) { this.operatingHours = operatingHours; }
}
