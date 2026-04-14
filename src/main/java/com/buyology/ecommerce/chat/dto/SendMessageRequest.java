package com.buyology.ecommerce.chat.dto;

import com.buyology.ecommerce.chat.domain.enums.ClientType;
import com.buyology.ecommerce.chat.domain.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload sent by the customer over the STOMP WebSocket to
 * {@code /app/chat/{deliveryOrderId}/send}.
 *
 * <p>Web clients must set {@code clientType = WEB}. Any {@code messageType} other than
 * {@code TEXT} is rejected with HTTP 400 for WEB sessions.
 */
public record SendMessageRequest(

        @NotNull
        MessageType messageType,

        @NotBlank
        @Size(max = 4000)
        String content,

        /** {@code MOBILE} or {@code WEB} — set by the client. Defaults to MOBILE if absent. */
        ClientType clientType
) {
    public SendMessageRequest {
        if (clientType == null) clientType = ClientType.MOBILE;
    }
}
