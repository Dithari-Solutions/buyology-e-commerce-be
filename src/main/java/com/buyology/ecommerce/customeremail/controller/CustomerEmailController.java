package com.buyology.ecommerce.customeremail.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.customeremail.domain.CustomerEmailCampaign;
import com.buyology.ecommerce.customeremail.dto.CreateCampaignRequest;
import com.buyology.ecommerce.customeremail.dto.PreviewAudienceRequest;
import com.buyology.ecommerce.customeremail.repository.CustomerEmailCampaignRepository;
import com.buyology.ecommerce.customeremail.service.CustomerEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Composing and sending an email to customers.
 *
 * <p>Two steps rather than one, and the split is the guardrail: preview returns the number of
 * people an audience resolves to, and the send will only proceed if the request echoes that same
 * number back. It is the count, not the wording of a confirmation dialog, that a person actually
 * reads before doing something they cannot undo.
 */
@RestController
@RequestMapping("/api/admin/customer-emails")
@Tag(name = "Admin — Customer email")
public class CustomerEmailController {

    private final CustomerEmailService service;
    private final CustomerEmailCampaignRepository campaignRepo;

    public CustomerEmailController(CustomerEmailService service,
                                   CustomerEmailCampaignRepository campaignRepo) {
        this.service = service;
        this.campaignRepo = campaignRepo;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('marketing:email:send') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "How many customers an audience reaches, after suppression")
    public ResponseEntity<ApiResponse<Map<String, Object>>> preview(@Valid @RequestBody PreviewAudienceRequest req) {
        return ApiResponse.success(service.preview(req.getAudience(), req.getUserIds()), "Audience resolved");
    }

    /**
     * Creates the campaign and freezes its recipient list. Does not send.
     *
     * <p>The admin id comes from the authenticated principal on THIS thread. It cannot be read
     * later from the worker: AuditService already demonstrates the trap — it is {@code @Async} and
     * reads {@code SecurityContextHolder} on the pool thread, where the context is gone, so its
     * actor column is null on essentially every row.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('marketing:email:send') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "Create a customer email campaign as a draft")
    public ResponseEntity<ApiResponse<CustomerEmailCampaign>> create(
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody CreateCampaignRequest req) {
        CustomerEmailCampaign c = service.create(
                req.getSubject(), req.getBodyHtml(), req.getAudience(), req.getUserIds(),
                req.getConfirmRecipientCount(), adminId, req.getAdminName());
        return ApiResponse.created(c, "Campaign created. Review it, then send.");
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('marketing:email:send') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "Start sending a draft campaign")
    public ResponseEntity<ApiResponse<String>> send(@PathVariable UUID id) {
        boolean started = service.startSending(id);
        return started
                ? ApiResponse.success("started", "Sending has started. Progress appears on this page.")
                : ApiResponse.success("already", "This campaign has already been sent or is sending.");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('marketing:email:send') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "One campaign, with its progress")
    public ResponseEntity<ApiResponse<CustomerEmailCampaign>> get(@PathVariable UUID id) {
        return campaignRepo.findById(id)
                .map(c -> ApiResponse.success(c, "Campaign"))
                .orElseGet(() -> ApiResponse.failure(org.springframework.http.HttpStatus.NOT_FOUND, "Not found"));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('marketing:email:send') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "Campaign history — who sent what, to how many, and how it went")
    public ResponseEntity<ApiResponse<List<CustomerEmailCampaign>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                campaignRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100))).getContent(),
                "Campaigns");
    }
}
