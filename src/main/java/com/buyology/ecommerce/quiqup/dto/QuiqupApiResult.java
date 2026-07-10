package com.buyology.ecommerce.quiqup.dto;

/**
 * Result of a call to the Quiqup API, relayed verbatim to the admin testing UI.
 *
 * @param status HTTP status returned by Quiqup
 * @param ok     whether the status was 2xx
 * @param body   parsed JSON ({@code JsonNode}) when the response was JSON, otherwise the raw string
 */
public record QuiqupApiResult(int status, boolean ok, Object body) {}
