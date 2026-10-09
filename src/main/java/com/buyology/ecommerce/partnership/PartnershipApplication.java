package com.buyology.ecommerce.partnership;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.*;

public record PartnershipApplication(
    @NotNull UUID requestId,
    @NotBlank @Size(max=200) String name,
    @NotBlank @Size(max=200) String company,
    @NotBlank @Size(max=200) String cityCountry,
    @NotBlank @Pattern(regexp="(?=.*[0-9])[+()0-9 .-]{5,50}") String phone,
    @NotBlank @Email @Size(max=255) String email,
    @Size(max=500) String website,
    @NotNull @Size(min=19, max=19) Map<String, Boolean> answers,
    @NotBlank String investment,
    @NotEmpty @Size(min=1, max=1) List<@NotBlank String> partnerships,
    @NotBlank @Size(max=2000) String whyBuyology,
    @Size(max=0) String websiteAddress
) {
    public static final Set<String> ANSWER_KEYS = Set.of("registeredBusiness", "technologySales", "laptopsTablets", "refurbishedTechnology", "customerBase", "storage", "retailLocation", "localFulfilment", "returnsSupport", "onlineChannel", "team", "deployInvestment", "replenishment", "holdInventory", "initialSales", "growthSales", "activeMarketing", "longTerm", "finalDiscussion");
    public static final Set<String> INVESTMENTS = Set.of("USD 25,000–50,000", "USD 50,000–75,000", "USD 75,000–100,000", "Above USD 100,000", "Investment available subject to final business plan");
    public static final Set<String> PARTNERSHIPS = Set.of("STOCKIST", "STOCKIST_RETAILER", "STOCKIST_DISTRIBUTOR", "FUTURE_TERRITORY");

    @JsonIgnore
    @AssertTrue(message="All qualification questions require a Yes or No answer")
    public boolean isAnswersComplete() {
        return answers != null && answers.keySet().equals(ANSWER_KEYS) && answers.values().stream().allMatch(Objects::nonNull);
    }
    @JsonIgnore
    @AssertTrue(message="Select a valid investment range")
    public boolean isInvestmentValid() { return investment != null && INVESTMENTS.contains(investment); }
    @JsonIgnore
    @AssertTrue(message="Choose exactly one valid partnership")
    public boolean isPartnershipsValid() {
        return partnerships != null && partnerships.size() == 1 && partnerships.get(0) != null && PARTNERSHIPS.contains(partnerships.get(0));
    }
}
