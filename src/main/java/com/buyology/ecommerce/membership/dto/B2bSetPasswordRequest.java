package com.buyology.ecommerce.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class B2bSetPasswordRequest {

    @NotBlank
    private String token;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    private String confirmedPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmedPassword() { return confirmedPassword; }
    public void setConfirmedPassword(String confirmedPassword) { this.confirmedPassword = confirmedPassword; }
}
