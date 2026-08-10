package com.buyology.ecommerce.analytics.dto;

/**
 * Beacon sent by the storefront on every page view.
 *
 * <p>Nothing here is trusted: the service truncates and sanitises every field, and only
 * {@code visitorId} / {@code sessionId} are required (a beacon without them cannot be counted as
 * either a unique visitor or a visit, so it is rejected rather than silently distorting the numbers).
 *
 * @param visitorId stable per-browser id (localStorage)
 * @param sessionId per-session id (sessionStorage)
 * @param path      pathname of the page viewed — no query string
 * @param referrer  full referrer URL; only its host is stored
 * @param language  active storefront language code
 */
public record TrackVisitRequest(
        String visitorId,
        String sessionId,
        String path,
        String referrer,
        String language) {
}
