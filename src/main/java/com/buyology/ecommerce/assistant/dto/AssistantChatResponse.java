package com.buyology.ecommerce.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * The assistant's answer to one customer turn.
 *
 * @param conversationId the id to send back on the next message (new on the first turn)
 * @param reply          plain text to show in the bubble — no markdown, no HTML
 * @param inScope        false when the question was outside what this assistant answers; the reply
 *                       is then the polite redirect rather than an answer
 * @param escalate       true when the customer should be handed to a human (order-specific
 *                       problems, complaints, anything needing account access)
 * @param products       products the reply refers to, in the order the assistant mentioned them
 */
@Schema(description = "The assistant's reply, plus any products it referred to")
public record AssistantChatResponse(
        UUID conversationId,
        String reply,
        boolean inScope,
        boolean escalate,
        List<AssistantProductCard> products
) {
}
