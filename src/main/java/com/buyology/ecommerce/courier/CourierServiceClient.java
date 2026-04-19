package com.buyology.ecommerce.courier;

import com.buyology.ecommerce.common.outbox.OutboxEvent;
import com.buyology.ecommerce.common.outbox.OutboxEventRepository;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

@Component
public class CourierServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CourierServiceClient.class);

    private final WebClient webClient;
    private final CourierServiceTokenProvider tokenProvider;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final StoreLocationRepository storeLocationRepository;

    @Value("${courier.service.timeout-ms:10000}")
    private long timeoutMs;

    public CourierServiceClient(
            @Value("${courier.service.url:http://localhost:8081}") String baseUrl,
            CourierServiceTokenProvider tokenProvider,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            StoreLocationRepository storeLocationRepository
    ) {
        this.webClient               = WebClient.builder().baseUrl(baseUrl).build();
        this.tokenProvider           = tokenProvider;
        this.outboxEventRepository   = outboxEventRepository;
        this.objectMapper            = objectMapper;
        this.storeLocationRepository = storeLocationRepository;
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
     * Publishes an EXPRESS order to the courier service via RabbitMQ.
     * The courier service consumes from {@code delivery.order.received.queue}
     * bound to {@code buyology.ecommerce.exchange} with routing key
     * {@code order.delivery.requested}.
     */
    public void pushOrder(CourierOrderRequest req) {
        log.info("[COURIER-CLIENT] pushOrder start — orderId={} storeId={} deliveryLat={} deliveryLng={}",
                req.getOrderId(), req.getStoreId(), req.getDeliveryLatitude(), req.getDeliveryLongitude());
        try {
            // Look up the store's primary location for pickup coordinates
            com.buyology.ecommerce.store.domain.StoreLocation pickup = null;
            if (req.getStoreId() != null) {
                pickup = storeLocationRepository
                        .findByStoreIdAndIsPrimary(req.getStoreId(), true)
                        .orElseGet(() -> storeLocationRepository
                                .findAllByStoreIdAndIsActive(req.getStoreId(), true)
                                .stream().findFirst().orElse(null));
                log.info("[COURIER-CLIENT] store location lookup — storeId={} found={}",
                        req.getStoreId(), pickup != null);
                if (pickup != null) {
                    log.info("[COURIER-CLIENT] pickup location — address='{}' city='{}' lat={} lng={}",
                            pickup.getAddress(), pickup.getCity(), pickup.getLatitude(), pickup.getLongitude());
                }
            } else {
                log.warn("[COURIER-CLIENT] storeId is null — pickup lat/lng will be null");
            }

            String pickupAddress = pickup != null
                    ? pickup.getAddress() + ", " + pickup.getCity()
                    : "Store location unavailable";
            java.math.BigDecimal pickupLat = pickup != null ? java.math.BigDecimal.valueOf(pickup.getLatitude()) : null;
            java.math.BigDecimal pickupLng = pickup != null ? java.math.BigDecimal.valueOf(pickup.getLongitude()) : null;

            java.math.BigDecimal dropoffLat = req.getDeliveryLatitude() != null
                    ? java.math.BigDecimal.valueOf(req.getDeliveryLatitude()) : null;
            java.math.BigDecimal dropoffLng = req.getDeliveryLongitude() != null
                    ? java.math.BigDecimal.valueOf(req.getDeliveryLongitude()) : null;

            String dropoffAddress = String.join(", ",
                    req.getAddressLine1() != null ? req.getAddressLine1() : "",
                    req.getAddressLine2() != null ? req.getAddressLine2() : "",
                    req.getCity() != null ? req.getCity() : "",
                    req.getCountry() != null ? req.getCountry() : "")
                    .replaceAll(", ,", ",").replaceAll("^, |, $", "");

            log.info("[COURIER-CLIENT] event fields — pickupLat={} pickupLng={} dropoffLat={} dropoffLng={} shippingFee={}",
                    pickupLat, pickupLng, dropoffLat, dropoffLng, req.getShippingFee());

            if (pickupLat == null || pickupLng == null) {
                log.error("[COURIER-CLIENT] ABORT — pickupLat or pickupLng is null for orderId={}. storeId={} pickup={}",
                        req.getOrderId(), req.getStoreId(), pickup);
                return;
            }
            if (dropoffLat == null || dropoffLng == null) {
                log.error("[COURIER-CLIENT] ABORT — dropoffLat or dropoffLng is null for orderId={}. address lat/lng not set on UserAddress.",
                        req.getOrderId());
                return;
            }

            DeliveryOrderEvent event = new DeliveryOrderEvent(
                    req.getOrderId(),
                    req.getStoreId(),
                    req.getRecipientFirstName() + " " + req.getRecipientLastName(),
                    req.getRecipientPhone(),
                    pickupAddress,
                    pickupLat,
                    pickupLng,
                    dropoffAddress,
                    dropoffLat,
                    dropoffLng,
                    "SMALL",
                    null,
                    req.getShippingFee(),
                    "EXPRESS",
                    java.time.Instant.now()
            );

            log.info("[COURIER-CLIENT] writing order {} to outbox — exchange={} routingKey={}",
                    req.getOrderId(),
                    DeliveryRabbitMQConfig.ECOMMERCE_EXCHANGE,
                    DeliveryRabbitMQConfig.ORDER_DELIVERY_REQUESTED_KEY);

            try {
                outboxEventRepository.save(OutboxEvent.builder()
                        .exchange(DeliveryRabbitMQConfig.ECOMMERCE_EXCHANGE)
                        .routingKey(DeliveryRabbitMQConfig.ORDER_DELIVERY_REQUESTED_KEY)
                        .payload(objectMapper.writeValueAsString(event))
                        .eventVersion(1)
                        .build());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize DeliveryOrderEvent for order " + req.getOrderId(), e);
            }
            log.info("[COURIER-CLIENT] order {} queued in outbox for courier backend", req.getOrderId());
        } catch (Exception e) {
            log.error("[COURIER-CLIENT] failed to publish order {} to courier exchange: {}",
                    req.getOrderId(), e.getMessage(), e);
        }
    }

    public ResponseEntity<String> getDeliveryProof(UUID deliveryOrderId, String adminUserId) {
        String uri = "/api/v1/deliveries/" + deliveryOrderId + "/proof";
        String token = tokenProvider.generateToken(adminUserId);
        log.info("[COURIER-CLIENT] GET {} adminId={}", uri, adminUserId);
        return execute(
                webClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        );
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
