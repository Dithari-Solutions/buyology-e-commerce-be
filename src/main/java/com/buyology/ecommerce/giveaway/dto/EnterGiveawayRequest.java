package com.buyology.ecommerce.giveaway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The customer's Instagram handle. Accepts '@handle', 'handle' or a profile URL. */
public class EnterGiveawayRequest {

    @NotBlank(message = "An Instagram username is required.")
    @Size(max = 200, message = "That does not look like an Instagram username.")
    private String instagramHandle;

    public String getInstagramHandle() { return instagramHandle; }
    public void setInstagramHandle(String instagramHandle) { this.instagramHandle = instagramHandle; }
}
