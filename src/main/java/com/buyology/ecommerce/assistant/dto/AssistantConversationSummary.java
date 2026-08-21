package com.buyology.ecommerce.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the admin transcript list.
 *
 * @param id           conversation id, to open the full transcript
 * @param language     the language the customer was answered in
 * @param countryCode  the market the conversation was scoped to
 * @param messageCount customer turns in the conversation
 * @param firstQuestion the opening question — what the customer came to ask
 * @param createdAt     when the chat started
 * @param lastMessageAt when it was last active
 */
@Schema(description = "Summary of one AI assistant conversation")
public record AssistantConversationSummary(
        UUID id,
        String language,
        String countryCode,
        Integer messageCount,
        String firstQuestion,
        Instant createdAt,
        Instant lastMessageAt
) {
}
