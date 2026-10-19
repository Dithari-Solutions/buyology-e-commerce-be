package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/partnership/invitations")
@PreAuthorize("hasRole('SUPERADMIN') or hasRole('CUSTOMER_SUPPORT') or @rbacPolicy.legacyAdmin()")
public class PartnershipInvitationController {
    private final PartnershipInvitationService service;
    private final PartnershipInvitationTemplate template;
    public PartnershipInvitationController(PartnershipInvitationService service, PartnershipInvitationTemplate template) {
        this.service = service; this.template = template;
    }
    public record Submission(@NotNull UUID requestId, @NotBlank @Size(max = 20000) String emails,
            @NotBlank @Size(max = 32) String templateVersion) {}
    @GetMapping("/template")
    public ResponseEntity<ApiResponse<PartnershipInvitationTemplate.Preview>> preview() {
        return ApiResponse.success(template.preview(), "Partnership invitation preview");
    }
    @PostMapping
    public ResponseEntity<ApiResponse<PartnershipInvitationService.Detail>> submit(@Valid @RequestBody Submission input) {
        return ApiResponse.success(service.submit(input.requestId(), SecurityUtils.currentUserId(), input.emails(), input.templateVersion()), "Partnership invitations queued");
    }
    @GetMapping
    public ResponseEntity<ApiResponse<List<PartnershipInvitationService.Detail>>> recent() {
        return ApiResponse.success(service.recent(), "Recent partnership invitations");
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PartnershipInvitationService.Detail>> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id), "Partnership invitation status");
    }
}
