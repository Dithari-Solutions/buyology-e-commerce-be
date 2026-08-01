package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.CreateGlobalSpecGroupRequest;
import com.buyology.ecommerce.product.dto.GlobalSpecGroupResponse;
import com.buyology.ecommerce.product.dto.ReorderSpecOptionsRequest;
import com.buyology.ecommerce.product.dto.UpdateSpecGroupRequest;
import com.buyology.ecommerce.product.service.GlobalSpecService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/specs")
@Tag(name = "Admin Global Specs", description = "Manage the global reusable spec library (RAM options, OS list, etc.)")
public class AdminGlobalSpecController {

    private final GlobalSpecService globalSpecService;

    public AdminGlobalSpecController(GlobalSpecService globalSpecService) {
        this.globalSpecService = globalSpecService;
    }

    @Operation(summary = "List all global spec groups with their options")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GlobalSpecGroupResponse>>> getAllGroups() {
        return globalSpecService.getAllGroups();
    }

    @Operation(summary = "Create a new global spec group (e.g. RAM) with optional initial options")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse>> createGroup(
            @Valid @RequestBody CreateGlobalSpecGroupRequest request) {
        return globalSpecService.createGroup(request);
    }

    @Operation(summary = "Edit a global spec group's display names")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:update') or @rbacPolicy.legacyAdmin()")
    @PutMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse>> updateGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateSpecGroupRequest request) {
        return globalSpecService.updateGroup(groupId, request);
    }

    @Operation(summary = "Delete a global spec group (and its options). Products keep their copied specs.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable UUID groupId) {
        return globalSpecService.deleteGroup(groupId);
    }

    @Operation(summary = "Add an option to an existing global spec group")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{groupId}/options")
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse.OptionDto>> addOption(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateGlobalSpecGroupRequest.OptionRequest request) {
        return globalSpecService.addOption(groupId, request);
    }

    @Operation(summary = "Edit an existing global spec option (value + unit)")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:update') or @rbacPolicy.legacyAdmin()")
    @PutMapping("/options/{optionId}")
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse.OptionDto>> updateOption(
            @PathVariable UUID optionId,
            @Valid @RequestBody CreateGlobalSpecGroupRequest.OptionRequest request) {
        return globalSpecService.updateOption(optionId, request);
    }

    @Operation(summary = "Delete a global spec option")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/options/{optionId}")
    public ResponseEntity<ApiResponse<Void>> deleteOption(@PathVariable UUID optionId) {
        return globalSpecService.deleteOption(optionId);
    }

    @Operation(summary = "Reorder the options of a spec group (sets their display order)")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:update') or @rbacPolicy.legacyAdmin()")
    @PutMapping("/{groupId}/options/reorder")
    public ResponseEntity<ApiResponse<List<GlobalSpecGroupResponse.OptionDto>>> reorderOptions(
            @PathVariable UUID groupId,
            @Valid @RequestBody ReorderSpecOptionsRequest request) {
        return globalSpecService.reorderOptions(groupId, request.getOptionIds());
    }
}
