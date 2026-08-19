package com.buyology.ecommerce.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A full transcript, for support and for auditing what the assistant is telling customers.
 *
 * @param id       conversation id
 * @param language the language used
 * @param turns    every turn, oldest first
 */
@Schema(description = "Full transcript of one AI assistant conversation")
public record AssistantTranscriptResponse(
        UUID id,
        String language,
        String countryCode,
        Instant createdAt,
        Instant lastMessageAt,
        List<Turn> turns
) {

    /**
     * @param role      CUSTOMER or ASSISTANT
     * @param content   what was said
     * @param inScope   assistant turns only — false where the assistant declined an off-topic
     *                  question. The refusal rate is the number worth watching here: an assistant
     *                  that starts declining real product questions is a regression you can only
     *                  see in this field.
     * @param createdAt when the turn was recorded
     */
    @Schema(description = "One turn of an assistant conversation")
    public record Turn(
            String role,
            String content,
            Boolean inScope,
            Instant createdAt
    ) {
    }
}
