package com.buyology.ecommerce.assistant.controller;

import com.buyology.ecommerce.assistant.dto.AssistantChatRequest;
import com.buyology.ecommerce.assistant.dto.AssistantChatResponse;
import com.buyology.ecommerce.assistant.service.AssistantService;
import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * The storefront chat widget's endpoints.
 *
 * <p>Unauthenticated by design. The visitors who most need to ask "do you have this in stock?" have
 * not signed in, and requiring a login would leave the assistant answering only the customers who
 * least need it. What makes that safe is that the endpoint cannot be steered: the caller supplies a
 * question and a conversation id, and nothing else — no history, no prompt, no model, no
 * parameters. Cost and abuse are bounded by the ASSISTANT rate-limit tier (per IP), the
 * per-conversation message ceiling and the per-visitor daily conversation cap.
 *
 * <p>When a signed-in customer happens to be chatting, their id is recorded on the conversation so
 * support can tie a transcript to an account. It changes nothing about the answer.
 */
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "AI Assistant", description = "Storefront customer-support assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /**
     * Tells the storefront whether to render the chat widget at all.
     *
     * <p>Without this the widget has no way to know the assistant is switched off until a customer
     * has already typed a question and been refused — which is a worse first impression than no
     * widget.
     */
    @Operation(summary = "Whether the AI assistant is currently available")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> status() {
        return ApiResponse.success(Map.of("enabled", assistantService.isEnabled()),
                "Assistant status fetched");
    }

    /**
     * Sends one customer message and returns the assistant's reply.
     *
     * <p>Omit {@code conversationId} to start a chat — the reply carries the new id, which the
     * client sends on every subsequent message.
     */
    @Operation(summary = "Ask the assistant a question about the company or its products")
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AssistantChatResponse>> chat(
            @Valid @RequestBody AssistantChatRequest request,
            @AuthenticationPrincipal UUID customerId) {

        AssistantChatResponse response = assistantService.chat(request, customerId);
        return ApiResponse.success(response, "Assistant replied");
    }
}
