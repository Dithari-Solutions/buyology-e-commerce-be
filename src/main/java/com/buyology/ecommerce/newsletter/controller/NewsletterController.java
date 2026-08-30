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

import java.time.Instant;
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

    /** One published article, by its readable slug. */
    @GetMapping("/api/news/{slug}")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> getBySlug(@PathVariable String slug) {
        return ApiResponse.success(newsletterService.getPublishedBySlug(slug), "Article fetched");
    }

    /**
     * How many articles were published after {@code since} — the header badge.
     *
     * <p>The caller supplies the timestamp it last read, so the server holds no per-visitor state
     * for a count. That also makes it work signed out, which matters: most people reading news
     * have not logged in.
     */
    @GetMapping("/api/news/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> newCount(
            @RequestParam(value = "since", required = false) String since) {
        Instant from = null;
        if (since != null && !since.isBlank()) {
            try { from = Instant.parse(since); }
            catch (Exception ignored) { /* unparseable = treat as "everything is new" */ }
        }
        return ApiResponse.success(
                Map.of("count", newsletterService.countPublishedSince(from)), "New article count");
    }

    @PostMapping("/api/admin/news")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('newsletter:article:create') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> createArticle(
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestPart("request") CreateNewsArticleRequest req,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "gallery", required = false) List<MultipartFile> gallery) {
        return ApiResponse.created(newsletterService.createArticle(req, adminId, image, gallery),
                "Article created");
    }

    @GetMapping("/api/admin/news")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('newsletter:article:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<List<NewsArticleResponse>>> listAll() {
        return ApiResponse.success(newsletterService.listAllArticles(), "Articles fetched");
    }

    @GetMapping("/api/admin/newsletter/stats")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('newsletter:subscriber:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<Map<String, Long>>> stats() {
        return ApiResponse.success(Map.of("subscriberCount", newsletterService.countSubscribers()), "Stats fetched");
    }

    @PutMapping("/api/admin/news/{id}/publish")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('newsletter:article:moderate') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<Void>> publish(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean sendToSubscribers) {
        newsletterService.publishAndSend(id, sendToSubscribers);
        return ApiResponse.success(null, sendToSubscribers
                ? "Article published and newsletter is being sent"
                : "Article published");
    }
}
