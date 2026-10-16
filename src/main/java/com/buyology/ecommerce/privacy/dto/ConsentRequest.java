package com.buyology.ecommerce.privacy.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ConsentRequest(
        @NotNull UUID decisionId,
        @NotNull UUID consentId,
        @NotNull Boolean analytics,
        @NotNull Boolean marketing,
        @NotBlank @Size(max = 20) String policyVersion,
        @NotBlank @Pattern(regexp = "BANNER|SETTINGS") String source,
        @NotBlank @Pattern(regexp = "en|az|ar") String language) {}
