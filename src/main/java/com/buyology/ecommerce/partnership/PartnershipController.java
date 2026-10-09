package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
public class PartnershipController {
    private final PartnershipRequestService service;
    public PartnershipController(PartnershipRequestService service) { this.service=service; }
    @PostMapping("/api/partnership/requests")
    public ResponseEntity<ApiResponse<PartnershipRequestService.Receipt>> submit(@Valid @RequestBody PartnershipApplication application) {
        return ApiResponse.created(service.submit(application),"Partnership request received");
    }
    @GetMapping("/api/admin/partnership/requests")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('CUSTOMER_SUPPORT') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<PartnershipRequestService.RequestPage>> list(@RequestParam(defaultValue="0") int page) {
        return ApiResponse.success(service.list(page),"Partnership requests fetched");
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestError(ResponseStatusException exception) {
        return ApiResponse.failure(exception.getStatusCode(), exception.getReason());
    }
    public record StatusUpdate(@NotBlank @Pattern(regexp="NEW|REVIEWED|RESPONDED") String status, @Size(max=5000) String adminNotes) {}
    @PatchMapping("/api/admin/partnership/requests/{id}/status")
    @PreAuthorize("hasRole('SUPERADMIN') or hasRole('CUSTOMER_SUPPORT') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<PartnershipRequestService.Detail>> update(@PathVariable UUID id, @Valid @RequestBody StatusUpdate update) {
        return ApiResponse.success(service.update(id,update.status(),update.adminNotes()),"Partnership request updated");
    }
}
