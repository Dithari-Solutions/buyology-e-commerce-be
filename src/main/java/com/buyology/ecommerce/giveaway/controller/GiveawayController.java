package com.buyology.ecommerce.giveaway.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.giveaway.dto.EnterGiveawayRequest;
import com.buyology.ecommerce.giveaway.dto.GiveawayStatusResponse;
import com.buyology.ecommerce.giveaway.service.GiveawayService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Customer-facing giveaway endpoints. Entering requires an account — the draw is one entry per
 * ACCOUNT, so an anonymous entry has nothing to be unique against — but whether the campaign is
 * running at all is public, because the banner advertising it renders for signed-out visitors and
 * has to disappear for them too when the draw closes.
 */
@RestController
@RequestMapping("/api/giveaway")
public class GiveawayController {

    private final GiveawayService giveawayService;

    public GiveawayController(GiveawayService giveawayService) {
        this.giveawayService = giveawayService;
    }

    /**
     * Open/closed and the entry count, for anyone at all.
     *
     * <p>Deliberately unauthenticated. Every other giveaway surface hides on this flag, and the
     * home banner is the one a guest sees — gating this behind a login would leave the campaign
     * advertised to exactly the people who cannot be told it has ended.
     */
    @GetMapping("/campaign")
    public ResponseEntity<ApiResponse<GiveawayStatusResponse>> campaign() {
        return ApiResponse.success(giveawayService.publicStatus(), "Giveaway campaign fetched");
    }

    /** Whether the caller is entered, and whether they are allowed to enter. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<GiveawayStatusResponse>> me(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(giveawayService.status(userId), "Giveaway status fetched");
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<GiveawayStatusResponse>> enter(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody EnterGiveawayRequest req) {
        return ApiResponse.created(
                giveawayService.enter(userId, req.getInstagramHandle()), "You're in the giveaway");
    }
}
