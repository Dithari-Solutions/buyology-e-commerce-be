package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.CreateGlobalSpecGroupRequest;
import com.buyology.ecommerce.product.dto.GlobalSpecGroupResponse;
import com.buyology.ecommerce.product.dto.ReorderSpecOptionsRequest;
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
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Global Specs", description = "Manage the global reusable spec library (RAM options, OS list, etc.)")
public class AdminGlobalSpecController {

    private final GlobalSpecService globalSpecService;

    public AdminGlobalSpecController(GlobalSpecService globalSpecService) {
        this.globalSpecService = globalSpecService;
    }

    @Operation(summary = "List all global spec groups with their options")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GlobalSpecGroupResponse>>> getAllGroups() {
        return globalSpecService.getAllGroups();
    }

    @Operation(summary = "Create a new global spec group (e.g. RAM) with optional initial options")
    @PostMapping
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse>> createGroup(
            @Valid @RequestBody CreateGlobalSpecGroupRequest request) {
        return globalSpecService.createGroup(request);
    }

    @Operation(summary = "Add an option to an existing global spec group")
    @PostMapping("/{groupId}/options")
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse.OptionDto>> addOption(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateGlobalSpecGroupRequest.OptionRequest request) {
        return globalSpecService.addOption(groupId, request);
    }

    @Operation(summary = "Delete a global spec option")
    @DeleteMapping("/options/{optionId}")
    public ResponseEntity<ApiResponse<Void>> deleteOption(@PathVariable UUID optionId) {
        return globalSpecService.deleteOption(optionId);
    }

    @Operation(summary = "Reorder the options of a spec group (sets their display order)")
    @PutMapping("/{groupId}/options/reorder")
    public ResponseEntity<ApiResponse<List<GlobalSpecGroupResponse.OptionDto>>> reorderOptions(
            @PathVariable UUID groupId,
            @Valid @RequestBody ReorderSpecOptionsRequest request) {
        return globalSpecService.reorderOptions(groupId, request.getOptionIds());
    }
}
