package com.buyology.ecommerce.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * One customer turn.
 *
 * <p>Note what is <em>not</em> here: no history, no system prompt, no model, no temperature. The
 * transcript is server-side and the prompt is built on the server, so the only thing a caller
 * controls is their own question — which is the whole reason this endpoint can be public.
 */
@Schema(description = "A single message from the customer to the storefront AI assistant")
public class AssistantChatRequest {

    @Schema(description = "Conversation to continue. Omit on the first message — the reply carries "
            + "the new id.", example = "6b1f2c3d-0000-4000-8000-000000000000")
    private UUID conversationId;

    @Schema(description = "The customer's question.", example = "Do you have any refurbished MacBooks?")
    @NotBlank(message = "Message must not be empty")
    @Size(max = 1000, message = "Message must be at most 1000 characters")
    private String message;

    @Schema(description = "Opaque browser id (the same one the visitor beacon sends). Used for the "
            + "per-visitor daily conversation cap.")
    @Size(max = 64)
    private String visitorId;

    @Schema(description = "Reply language: 'en' or 'ar'. Defaults to English.", example = "en")
    @Size(max = 8)
    private String language;

    @Schema(description = "ISO-3166 alpha-2 country. Scopes products and prices to that market.",
            example = "AE")
    @Size(max = 2)
    private String countryCode;

    @Schema(description = "Display currency for quoted prices.", example = "AED")
    @Size(max = 8)
    private String currency;

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getVisitorId() { return visitorId; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
