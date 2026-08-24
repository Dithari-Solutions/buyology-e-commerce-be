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
 * Customer-facing giveaway endpoints. Authentication is mandatory — the draw is one entry
 * per ACCOUNT, so an anonymous entry has nothing to be unique against.
 */
@RestController
@RequestMapping("/api/giveaway")
@PreAuthorize("isAuthenticated()")
public class GiveawayController {

    private final GiveawayService giveawayService;

    public GiveawayController(GiveawayService giveawayService) {
        this.giveawayService = giveawayService;
    }

    /** Whether the caller is entered, and whether they are allowed to enter. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<GiveawayStatusResponse>> me(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(giveawayService.status(userId), "Giveaway status fetched");
    }

    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<GiveawayStatusResponse>> enter(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody EnterGiveawayRequest req) {
        return ApiResponse.created(
                giveawayService.enter(userId, req.getInstagramHandle()), "You're in the giveaway");
    }
}
