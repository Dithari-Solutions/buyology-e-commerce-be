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

import java.time.Duration;

@Component
public class CourierServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CourierServiceClient.class);

    private final WebClient webClient;

    @Value("${courier.service.timeout-ms:5000}")
    private long timeoutMs;

    public CourierServiceClient(@Value("${courier.service.url}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Forward a courier creation request to the courier service.
     *
     * @param request     the courier signup payload
     * @param bearerToken the admin's Keycloak JWT (full "Bearer <token>" header value)
     * @param clientIp    original client IP — forwarded for audit log accuracy
     * @return ResponseEntity with the courier service response body and status code
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
}
