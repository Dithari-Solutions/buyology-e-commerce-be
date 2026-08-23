package com.buyology.ecommerce.support.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.support.dto.AddSupportMessageRequest;
import com.buyology.ecommerce.support.dto.SupportTicketResponse;
import com.buyology.ecommerce.support.service.SupportTicketService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Customer-facing support-ticket endpoints. All require authentication; the service keys
 * ownership on the caller's credential (sub). The JWT filter sets the principal to users.id (uid).
 */
@RestController
@RequestMapping("/api/support/tickets")
@PreAuthorize("isAuthenticated()")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    public SupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    /**
     * Open a ticket. Multipart so up to four screenshots can be attached; scalar fields are plain
     * {@code @RequestParam} (NOT a JSON @RequestPart) to avoid browser FormData 415 issues.
     * Screenshots and the page URL are optional.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SupportTicketResponse>> create(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("category") String category,
            @RequestParam("subject") String subject,
            @RequestParam("description") String description,
            @RequestParam(value = "pageUrl", required = false) String pageUrl,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ApiResponse.created(
                supportTicketService.create(userId, category, subject, description, pageUrl, images),
                "Support ticket submitted");
    }

    /** The caller's tickets, newest first. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SupportTicketResponse>>> listMine(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(supportTicketService.listOwn(userId), "Support tickets fetched");
    }

    /** One ticket with its full conversation; opening it clears the unread pill. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> get(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return ApiResponse.success(supportTicketService.getOwn(userId, id), "Support ticket fetched");
    }

    /** Customer reply on their own ticket. */
    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> addMessage(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody AddSupportMessageRequest req) {
        return ApiResponse.success(
                supportTicketService.addCustomerMessage(userId, id, req.getBody()),
                "Reply added");
    }
}
