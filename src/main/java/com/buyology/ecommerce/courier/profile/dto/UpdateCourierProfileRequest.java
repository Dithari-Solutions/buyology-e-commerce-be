package com.buyology.ecommerce.courier.profile.dto;

public class UpdateCourierProfileRequest {

    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String vehicleType;
    private Boolean active;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
