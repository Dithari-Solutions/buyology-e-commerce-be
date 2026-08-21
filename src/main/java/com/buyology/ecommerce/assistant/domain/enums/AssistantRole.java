package com.buyology.ecommerce.assistant.domain.enums;

/**
 * Who produced one turn of an assistant conversation.
 *
 * <p>Deliberately narrower than the Claude API's own role set: the system prompt is built on the
 * server from the knowledge base and the live catalog on every call and is never persisted, so a
 * stored transcript can only ever be replayed as customer/assistant turns. That is what keeps a
 * caller from smuggling instructions into the model by writing them into their own history.
 */
public enum AssistantRole {

    /** Text the customer typed. Always treated as untrusted data, never as instructions. */
    CUSTOMER,

    /** Text Claude produced. */
    ASSISTANT
}
