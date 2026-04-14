package com.buyology.ecommerce.chat.controller;

import com.buyology.ecommerce.chat.dto.ChatMessageResponse;
import com.buyology.ecommerce.chat.service.ChatService;
import com.buyology.ecommerce.common.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoint for fetching chat history.
 *
 * <p>Available to both mobile and web customers. Web clients use this
 * endpoint to render the full conversation on page load before subscribing
 * to the WebSocket for live updates.
 *
 * <p>Call signalling messages ({@code CALL_OFFER}, {@code CALL_ANSWER}, etc.)
 * are included in the history response but web clients should ignore them.
 */
@RestController
@RequestMapping("/api/orders/{orderId}/chat")
public class ChatRestController {

    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Returns paginated chat history for the order.
     * The caller must be the order owner.
     *
     * @param orderId   the ecommerce Order.id
     * @param page      zero-based page index (default 0)
     * @param size      page size (default 50, max 100)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getHistory(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UUID customerId) {

        int safeSize = Math.min(size, 100);
        Page<ChatMessageResponse> history = chatService.getHistory(orderId, customerId, page, safeSize);
        return ApiResponse.success(history, "Chat history retrieved");
    }
}
