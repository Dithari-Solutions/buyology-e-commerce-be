package com.buyology.ecommerce.admin.health;

import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * One request, one picture of whether anything is going wrong.
 *
 * <p>Superadmin only. The payload names registration IP addresses and email domains, which is
 * personal data gathered for abuse detection — it does not belong behind the same permission as
 * editing a product.
 */
@RestController
@Tag(name = "Admin — Service Health")
public class ServiceHealthController {

    private final ServiceHealthService service;

    public ServiceHealthController(ServiceHealthService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/service-health")
    @PreAuthorize("hasRole('SUPERADMIN') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "Operational health across verification, signups, payments and integrations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ApiResponse.success(service.snapshot(), "Service health");
    }
}
