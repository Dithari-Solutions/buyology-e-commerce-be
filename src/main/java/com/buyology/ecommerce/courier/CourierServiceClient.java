package com.buyology.ecommerce.courier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class CourierServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CourierServiceClient.class);

    private final WebClient webClient;
    private final CourierServiceTokenProvider tokenProvider;

    @Value("${courier.service.timeout-ms:10000}")
    private long timeoutMs;

    public CourierServiceClient(
            @Value("${courier.service.url:http://localhost:8081}") String baseUrl,
            CourierServiceTokenProvider tokenProvider
    ) {
        this.webClient     = WebClient.builder().baseUrl(baseUrl).build();
        this.tokenProvider = tokenProvider;
    }

    /** Forward a multipart POST request (e.g. courier creation). */
    public ResponseEntity<String> forwardMultipart(
            String uri,
            MultiValueMap<String, HttpEntity<?>> body,
            String adminId,
            String clientIp
    ) {
        String token = tokenProvider.generateToken(adminId);
        log.info("[COURIER-CLIENT] POST {} ip={} adminId={}", uri, clientIp, adminId);
        return execute(
                webClient.post()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .bodyValue(body)
        );
    }

    /** Forward a multipart PATCH request (e.g. courier profile update). */
    public ResponseEntity<String> forwardMultipartPatch(
            String uri,
            MultiValueMap<String, HttpEntity<?>> body,
            String adminId,
            String clientIp
    ) {
        String token = tokenProvider.generateToken(adminId);
        log.info("[COURIER-CLIENT] PATCH {} ip={} adminId={}", uri, clientIp, adminId);
        return execute(
                webClient.patch()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Forwarded-For", clientIp)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .bodyValue(body)
        );
    }

    /** Forward a JSON body request (POST or PATCH). */
    public ResponseEntity<String> forwardJson(
            String method,
            String uri,
            Object body,
            String adminId,
            String clientIp
    ) {
        String token = tokenProvider.generateToken(adminId);
        log.info("[COURIER-CLIENT] {} {} ip={} adminId={}", method, uri, clientIp, adminId);
        WebClient.RequestBodySpec spec = switch (method.toUpperCase()) {
            case "POST"  -> webClient.post().uri(uri);
            case "PATCH" -> webClient.patch().uri(uri);
            default      -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
        return execute(
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Forwarded-For", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
        );
    }

    /** Forward a no-body request (GET or DELETE). Query string is appended as-is. */
    public ResponseEntity<String> forwardNoBody(
            String method,
            String uri,
            String queryString,
            String adminId,
            String clientIp
    ) {
        String token  = tokenProvider.generateToken(adminId);
        String fullUri = (queryString != null && !queryString.isBlank()) ? uri + "?" + queryString : uri;
        log.info("[COURIER-CLIENT] {} {} ip={} adminId={}", method, fullUri, clientIp, adminId);
        WebClient.RequestHeadersSpec<?> spec = switch (method.toUpperCase()) {
            case "GET"    -> webClient.get().uri(fullUri);
            case "DELETE" -> webClient.delete().uri(fullUri);
            default       -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
        return execute(
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Forwarded-For", clientIp)
        );
    }

    /**
     * Sends a new LOCAL_EXPRESS order to the courier backend.
     * Called automatically after a successful payment.
     */
    public void pushOrder(CourierOrderRequest req) {
        try {
            String token = tokenProvider.generateToken("SYSTEM");
            log.info("[COURIER-CLIENT] pushing order {} to courier backend", req.getOrderId());
            execute(
                webClient.post()
                    .uri("/api/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
            );
        } catch (Exception e) {
            log.error("[COURIER-CLIENT] failed to push order {} to courier backend: {}",
                    req.getOrderId(), e.getMessage());
        }
    }

    // ── shared execute ────────────────────────────────────────────────────────

    private ResponseEntity<String> execute(WebClient.RequestHeadersSpec<?> spec) {
        try {
            ResponseEntity<String> response = spec.retrieve()
                    .onStatus(
                            s -> s.is4xxClientError() || s.is5xxServerError(),
                            r -> r.bodyToMono(String.class)
                                    .map(b -> new CourierServiceException(r.statusCode().value(), b)))
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
            log.info("[COURIER-CLIENT] → {}", response.getStatusCode().value());
            return response;
        } catch (CourierServiceException ex) {
            log.warn("[COURIER-CLIENT] → {} body={}", ex.getStatusCode(), ex.getBody());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("[COURIER-CLIENT] call failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }
}
