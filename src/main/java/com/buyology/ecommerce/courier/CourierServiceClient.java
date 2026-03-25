package com.buyology.ecommerce.courier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

@Component
public class CourierServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CourierServiceClient.class);

    private final WebClient webClient;
    private final CourierServiceTokenProvider tokenProvider;

    @Value("${courier.service.timeout-ms:5000}")
    private long timeoutMs;

    public CourierServiceClient(
            @Value("${courier.service.url:https://api-courier.dithari.com}") String baseUrl,
            CourierServiceTokenProvider tokenProvider
    ) {
        this.webClient     = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.tokenProvider = tokenProvider;
    }

    /**
     * Forward a courier creation request to the courier service.
     */
    public ResponseEntity<String> createCourier(Object request, String clientIp) {
        String token = tokenProvider.generateServiceToken();
        log.info("[COURIER-CLIENT] POST /api/v1/couriers ip={}", clientIp);
        try {
            ResponseEntity<String> response = webClient.post()
                    .uri("/api/v1/couriers")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Forwarded-For", clientIp)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new CourierServiceException(
                                            clientResponse.statusCode().value(), body)))
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
            log.info("[COURIER-CLIENT] POST /api/v1/couriers → {}", response.getStatusCode().value());
            return response;
        } catch (CourierServiceException ex) {
            log.warn("[COURIER-CLIENT] POST /api/v1/couriers → {} body={}", ex.getStatusCode(), ex.getBody());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("[COURIER-CLIENT] POST /api/v1/couriers failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }

    /**
     * List couriers — supports optional query params: page, size, status, vehicleType, search.
     */
    public ResponseEntity<String> listCouriers(
            String clientIp,
            Integer page,
            Integer size,
            String status,
            String vehicleType,
            String search
    ) {
        String token = tokenProvider.generateServiceToken();
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/v1/couriers");
            if (page != null)        uriBuilder.queryParam("page", page);
            if (size != null)        uriBuilder.queryParam("size", size);
            if (status != null)      uriBuilder.queryParam("status", status);
            if (vehicleType != null) uriBuilder.queryParam("vehicleType", vehicleType);
            if (search != null)      uriBuilder.queryParam("search", search);

            String uri = uriBuilder.toUriString();
            log.info("[COURIER-CLIENT] GET {} ip={}", uri, clientIp);

            ResponseEntity<String> response = webClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Forwarded-For", clientIp)
                    .retrieve()
                    .onStatus(
                            s -> s.is4xxClientError() || s.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new CourierServiceException(
                                            clientResponse.statusCode().value(), body)))
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
            log.info("[COURIER-CLIENT] GET {} → {}", uri, response.getStatusCode().value());
            return response;
        } catch (CourierServiceException ex) {
            log.warn("[COURIER-CLIENT] GET /api/v1/couriers → {} body={}", ex.getStatusCode(), ex.getBody());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("[COURIER-CLIENT] GET /api/v1/couriers failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }

    /**
     * Get a single courier by ID.
     */
    public ResponseEntity<String> getCourierById(String courierId, String clientIp) {
        String token = tokenProvider.generateServiceToken();
        log.info("[COURIER-CLIENT] GET /api/v1/couriers/{} ip={}", courierId, clientIp);
        try {
            ResponseEntity<String> response = webClient.get()
                    .uri("/api/v1/couriers/{id}", courierId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Forwarded-For", clientIp)
                    .retrieve()
                    .onStatus(
                            s -> s.is4xxClientError() || s.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new CourierServiceException(
                                            clientResponse.statusCode().value(), body)))
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
            log.info("[COURIER-CLIENT] GET /api/v1/couriers/{} → {}", courierId, response.getStatusCode().value());
            return response;
        } catch (CourierServiceException ex) {
            log.warn("[COURIER-CLIENT] GET /api/v1/couriers/{} → {} body={}", courierId, ex.getStatusCode(), ex.getBody());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("[COURIER-CLIENT] GET /api/v1/couriers/{} failed: {}", courierId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }

    /**
     * Update a courier's account status (ACTIVE, SUSPENDED, etc.).
     */
    public ResponseEntity<String> updateCourierStatus(
            String courierId,
            Object request,
            String clientIp
    ) {
        String token = tokenProvider.generateServiceToken();
        log.info("[COURIER-CLIENT] PATCH /api/v1/couriers/{}/status ip={}", courierId, clientIp);
        try {
            ResponseEntity<String> response = webClient.patch()
                    .uri("/api/v1/couriers/{id}/status", courierId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Forwarded-For", clientIp)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            s -> s.is4xxClientError() || s.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new CourierServiceException(
                                            clientResponse.statusCode().value(), body)))
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
            log.info("[COURIER-CLIENT] PATCH /api/v1/couriers/{}/status → {}", courierId, response.getStatusCode().value());
            return response;
        } catch (CourierServiceException ex) {
            log.warn("[COURIER-CLIENT] PATCH /api/v1/couriers/{}/status → {} body={}", courierId, ex.getStatusCode(), ex.getBody());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("[COURIER-CLIENT] PATCH /api/v1/couriers/{}/status failed: {}", courierId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }

    /**
     * Delete (deactivate) a courier account.
     */
    public ResponseEntity<String> deleteCourier(String courierId, String clientIp) {
        String token = tokenProvider.generateServiceToken();
        log.info("[COURIER-CLIENT] DELETE /api/v1/couriers/{} ip={}", courierId, clientIp);
        try {
            ResponseEntity<String> response = webClient.delete()
                    .uri("/api/v1/couriers/{id}", courierId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Forwarded-For", clientIp)
                    .retrieve()
                    .onStatus(
                            s -> s.is4xxClientError() || s.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new CourierServiceException(
                                            clientResponse.statusCode().value(), body)))
                    .toEntity(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
            log.info("[COURIER-CLIENT] DELETE /api/v1/couriers/{} → {}", courierId, response.getStatusCode().value());
            return response;
        } catch (CourierServiceException ex) {
            log.warn("[COURIER-CLIENT] DELETE /api/v1/couriers/{} → {} body={}", courierId, ex.getStatusCode(), ex.getBody());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("[COURIER-CLIENT] DELETE /api/v1/couriers/{} failed: {}", courierId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }
}
