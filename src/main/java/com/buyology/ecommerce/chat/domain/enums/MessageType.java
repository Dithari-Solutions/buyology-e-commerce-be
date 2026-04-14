package com.buyology.ecommerce.chat.domain.enums;

/**
 * Type of chat message.
 *
 * <ul>
 *   <li>{@code TEXT} — plain chat text; available on all client types (mobile + web).</li>
 *   <li>{@code CALL_OFFER / CALL_ANSWER / CALL_ICE_CANDIDATE / CALL_END / CALL_REJECT} —
 *       WebRTC signalling; mobile-only. The server rejects these types from web sessions.</li>
 * </ul>
 */
public enum MessageType {
    TEXT,
    CALL_OFFER,
    CALL_ANSWER,
    CALL_ICE_CANDIDATE,
    CALL_END,
    CALL_REJECT
}
