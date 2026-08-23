package com.buyology.ecommerce.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reply on a ticket — from the customer (storefront) or the team (dashboard). */
public class AddSupportMessageRequest {

    @NotBlank(message = "A message is required.")
    @Size(max = 4000, message = "Messages are limited to 4000 characters.")
    private String body;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
