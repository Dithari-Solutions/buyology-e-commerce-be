package com.buyology.ecommerce.assistant.controller;

import com.buyology.ecommerce.assistant.dto.AssistantConversationSummary;
import com.buyology.ecommerce.assistant.dto.AssistantTranscriptResponse;
import com.buyology.ecommerce.assistant.service.AssistantTranscriptService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dashboard reads of the assistant's conversations.
 *
 * <p>Transcripts contain whatever customers chose to type, so they are treated as customer data:
 * guarded by an explicit permission rather than left open to any authenticated admin.
 */
@RestController
@RequestMapping("/api/admin/assistant")
@Tag(name = "AI Assistant (Admin)", description = "Read what customers asked the assistant")
public class AdminAssistantController {

    private final AssistantTranscriptService transcriptService;

    public AdminAssistantController(AssistantTranscriptService transcriptService) {
        this.transcriptService = transcriptService;
    }

    @Operation(summary = "List assistant conversations, most recently active first")
    @GetMapping("/conversations")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('assistant:conversation:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<PageResponse<AssistantConversationSummary>>> conversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(transcriptService.list(page, size), "Assistant conversations fetched");
    }

    @Operation(summary = "Read the full transcript of one assistant conversation")
    @GetMapping("/conversations/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('assistant:conversation:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<AssistantTranscriptResponse>> transcript(@PathVariable UUID id) {
        return ApiResponse.success(transcriptService.transcript(id), "Assistant transcript fetched");
    }
}
