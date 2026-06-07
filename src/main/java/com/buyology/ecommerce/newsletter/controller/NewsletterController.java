package com.buyology.ecommerce.newsletter.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.newsletter.dto.CreateNewsArticleRequest;
import com.buyology.ecommerce.newsletter.dto.NewsArticleResponse;
import com.buyology.ecommerce.newsletter.service.NewsletterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class NewsletterController {

    private final NewsletterService newsletterService;

    public NewsletterController(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    // ── Public endpoints ─────────────────────────────────────────────────────

    @PostMapping("/api/newsletter/subscribe")
    public ResponseEntity<ApiResponse<String>> subscribe(@RequestBody Map<String, String> body) {
        String email = body == null ? null : body.get("email");
        if (email == null || email.isBlank()) {
            return ApiResponse.failure(org.springframework.http.HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (!com.buyology.ecommerce.common.utils.EmailValidation.isValid(email)
                || email.length() > 254) {
            return ApiResponse.failure(org.springframework.http.HttpStatus.BAD_REQUEST, "Email is not valid");
        }
        return ApiResponse.success(newsletterService.subscribe(email.trim()), "OK");
    }

    @GetMapping("/api/newsletter/unsubscribe")
    public ResponseEntity<ApiResponse<String>> unsubscribe(@RequestParam UUID token) {
        return ApiResponse.success(newsletterService.unsubscribe(token), "OK");
    }

    @GetMapping("/api/news")
    public ResponseEntity<ApiResponse<List<NewsArticleResponse>>> listPublished() {
        return ApiResponse.success(newsletterService.listPublishedArticles(), "Published articles fetched");
    }

    // ── Admin endpoints ──────────────────────────────────────────────────────

    @PostMapping("/api/admin/news")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> createArticle(
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestPart("request") CreateNewsArticleRequest req,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ApiResponse.created(newsletterService.createArticle(req, adminId, image), "Article created");
    }

    @GetMapping("/api/admin/news")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<NewsArticleResponse>>> listAll() {
        return ApiResponse.success(newsletterService.listAllArticles(), "Articles fetched");
    }

    @GetMapping("/api/admin/newsletter/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> stats() {
        return ApiResponse.success(Map.of("subscriberCount", newsletterService.countSubscribers()), "Stats fetched");
    }

    @PutMapping("/api/admin/news/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> publish(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean sendToSubscribers) {
        newsletterService.publishAndSend(id, sendToSubscribers);
        return ApiResponse.success(null, sendToSubscribers
                ? "Article published and newsletter is being sent"
                : "Article published");
    }
}
