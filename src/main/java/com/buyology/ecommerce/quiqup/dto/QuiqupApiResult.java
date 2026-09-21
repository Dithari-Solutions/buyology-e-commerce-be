package com.buyology.ecommerce.quiqup.dto;

/**
 * Result of a call to the Quiqup API, relayed verbatim to the admin testing UI.
 *
 * @param status HTTP status returned by Quiqup
 * @param ok     whether the status was 2xx
 * @param body   parsed JSON ({@code JsonNode}) when the response was JSON, otherwise the raw string
 * @param mayHaveReachedQuiqup false only when the request certainly never arrived — refused by our
 *        own guards, or the connection was never opened. A write that may have arrived without an
 *        answer coming back (a response timeout, a dropped connection) is true: Quiqup may have
 *        acted on it, and since they do not deduplicate on partner_order_id, repeating a create in
 *        that state books a second courier.
 */
public record QuiqupApiResult(int status, boolean ok, Object body, boolean mayHaveReachedQuiqup) {

    /** A real response from Quiqup, which by definition reached them. */
    public QuiqupApiResult(int status, boolean ok, Object body) {
        this(status, ok, body, true);
    }
}
