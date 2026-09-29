package com.buyology.ecommerce.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * The questionnaire a customer answered while cancelling their own order.
 *
 * <p>One record for three jobs — the shape Jackson binds the request into, the shape stored in
 * {@code orders.cancellation_feedback}, and the shape {@link OrderResponse} hands back — because
 * they are genuinely the same document and three near-identical records would drift apart at the
 * first added field.
 *
 * <p>Deliberately self-describing: every answer carries its question and its answer as displayable
 * English text, not only codes. The dashboard renders the list without knowing a single code, so a
 * new cancellation reason shipped in the storefront needs no backend or dashboard deploy and no
 * label table anywhere. {@link Answer#code()} rides alongside purely so the data stays groupable for
 * analytics and may be null.
 *
 * <p>English regardless of the UI locale the customer used, matching {@code cancellationReason} and
 * the admin dashboard. Sending English is the client's job — nothing here translates or interprets.
 *
 * <p>As a REQUEST this comes from an untrusted client, so nothing on it may be believed. {@code
 * version} and {@code submittedAt} are stamped by the server and whatever the client sent for them
 * is discarded; every text value is cleaned and capped. See {@code CancellationFeedbackCodec}, which
 * is the only thing that may turn one of these into a stored document.
 *
 * <p>Unknown fields are ignored rather than rejected, as on {@code CreateProductRequest}. This is not
 * laxness, it is the same invariant as every cap in the codec: a cancellation must never fail because
 * of the questionnaire attached to it. The application's {@code ObjectMapper} has Jackson's default
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}, so without this the storefront or the app adding a sixth
 * component to the payload — the ordinary way this flow will grow, in two repos being written right
 * now — would turn every cancel request into an HTTP 400 and leave the shopper unable to cancel their
 * order at all. An extra field costs one ignored key; refusing it costs the cancellation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancellationFeedback(
        Integer version,
        String source,
        String reasonCode,
        Instant submittedAt,
        List<Answer> answers) {

    /**
     * One question and the customer's answer to it.
     *
     * @param key      stable slot name for this question within the flow ("reason", "price")
     * @param question the question as the customer read it, in English
     * @param answer   the answer as the customer gave it, in English
     * @param code     the machine code for a chosen option, or null for free text and numbers
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(String key, String question, String answer, String code) {}
}
