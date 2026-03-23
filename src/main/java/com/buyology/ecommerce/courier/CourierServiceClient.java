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

    @Value("${courier.service.timeout-ms:5000}")
    private long timeoutMs;

    public CourierServiceClient(@Value("${courier.service.url:http://localhost:8081}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Forward a courier creation request to the courier service.
     */
    public ResponseEntity<String> createCourier(Object request, String bearerToken, String clientIp) {
        try {
            return webClient.post()
                    .uri("/api/auth/admin/couriers")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
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
        } catch (CourierServiceException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("Courier service call failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }

    /**
     * List couriers — supports optional query params: page, size, status, vehicleType, search.
     */
    public ResponseEntity<String> listCouriers(
            String bearerToken,
            String clientIp,
            Integer page,
            Integer size,
            String status,
            String vehicleType,
            String search
    ) {
        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/admin/couriers");
            if (page != null)        uriBuilder.queryParam("page", page);
            if (size != null)        uriBuilder.queryParam("size", size);
            if (status != null)      uriBuilder.queryParam("status", status);
            if (vehicleType != null) uriBuilder.queryParam("vehicleType", vehicleType);
            if (search != null)      uriBuilder.queryParam("search", search);

            String uri = uriBuilder.toUriString();

            return webClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
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
        } catch (CourierServiceException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("Courier service list call failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }

    /**
     * Get a single courier by ID.
     */
    public ResponseEntity<String> getCourierById(String courierId, String bearerToken, String clientIp) {
        try {
            return webClient.get()
                    .uri("/api/admin/couriers/{id}", courierId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
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
        } catch (CourierServiceException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("Courier service getById call failed: {}", ex.getMessage(), ex);
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
            String bearerToken,
            String clientIp
    ) {
        try {
            return webClient.patch()
                    .uri("/api/admin/couriers/{id}/status", courierId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
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
        } catch (CourierServiceException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("Courier service updateStatus call failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }

    /**
     * Delete (deactivate) a courier account.
     */
    public ResponseEntity<String> deleteCourier(String courierId, String bearerToken, String clientIp) {
        try {
            return webClient.delete()
                    .uri("/api/admin/couriers/{id}", courierId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
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
        } catch (CourierServiceException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getBody());
        } catch (Exception ex) {
            log.error("Courier service delete call failed: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"status\":502,\"error\":\"Bad Gateway\"," +
                          "\"message\":\"Courier service is temporarily unavailable.\"}");
        }
    }
}
