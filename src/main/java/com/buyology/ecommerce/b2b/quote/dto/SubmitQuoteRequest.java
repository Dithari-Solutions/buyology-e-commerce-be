package com.buyology.ecommerce.b2b.quote.dto;

import jakarta.validation.constraints.Size;

/** Optional note the member attaches when submitting the cart as a quote request. */
public class SubmitQuoteRequest {

    @Size(max = 2000)
    private String memberNote;

    public String getMemberNote() { return memberNote; }
    public void setMemberNote(String memberNote) { this.memberNote = memberNote; }
}
