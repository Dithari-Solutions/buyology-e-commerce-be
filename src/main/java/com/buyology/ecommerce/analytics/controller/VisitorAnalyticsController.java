package com.buyology.ecommerce.analytics.controller;

import com.buyology.ecommerce.analytics.dto.TrackVisitRequest;
import com.buyology.ecommerce.analytics.dto.VisitorMetricsResponse;
import com.buyology.ecommerce.analytics.service.VisitorAnalyticsService;
import com.buyology.ecommerce.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisitorAnalyticsController {

    private final VisitorAnalyticsService service;

    public VisitorAnalyticsController(VisitorAnalyticsService service) {
        this.service = service;
    }

    // ── Public: storefront beacon ────────────────────────────────────────────

    /**
     * Records one storefront page view. Unauthenticated by design — most visitors are not logged in,
     * which is exactly the population being counted. Throttled by the ANALYTICS_BEACON rate-limit
     * tier (240 req/min per IP), so a single client cannot inflate the numbers without being cut off.
     *
     * <p>Answers 202 whether or not the page view was actually stored — a beacon that is dropped
     * because tracking is off or the caller is a bot is not the storefront's problem, and a counter
     * that reports failures back to a page load is a counter that can break the page. The one
     * exception is a missing or malformed visitor/session id, which is a 400: such a beacon cannot be
     * counted as either a unique visitor or a visit, and silently accepting it would mean answering
     * OK to a client whose page views are never going to appear.
     */
    @PostMapping("/api/analytics/visit")
    public ResponseEntity<ApiResponse<Void>> track(@RequestBody TrackVisitRequest request,
                                                   HttpServletRequest http) {
        service.record(request,
                http.getHeader("User-Agent"),
                http.getHeader("X-Forwarded-For"),
                http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(HttpStatus.ACCEPTED.value(), "Recorded", null));
    }

    // ── Admin: dashboard metric ──────────────────────────────────────────────

    /**
     * Unique visitors, visits and page views for the dashboard home page.
     *
     * @param days length of the daily trend series (1-180)
     */
    @GetMapping("/api/admin/analytics/visitors")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('analytics:visitor:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<VisitorMetricsResponse>> visitors(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(service.metrics(days), "Visitor metrics fetched");
    }
}
