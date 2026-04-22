package com.buyology.ecommerce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * STOMP WebSocket configuration for real-time customer-facing features:
 * <ul>
 *   <li>Courier location tracking — customers subscribe to
 *       {@code /topic/orders/{orderId}/location}</li>
 *   <li>Order status updates — customers subscribe to
 *       {@code /topic/orders/{orderId}/status}</li>
 *   <li>Customer ↔ courier chat — customers subscribe to
 *       {@code /user/queue/chat/{deliveryOrderId}}</li>
 * </ul>
 *
 * <p>Clients authenticate by sending the JWT access token in the STOMP
 * {@code CONNECT} frame's {@code Authorization} header.
 * The token is validated with the same manual HMAC-SHA256 approach used by
 * {@code TokenService} — no JJWT library, no database lookup.
 * The {@code uid} claim (userId) in the token payload is used as the STOMP
 * session principal so that {@code convertAndSendToUser(userId, ...)} routing works.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS transport for web browsers (ChatPanel.tsx uses sockjs-client)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Raw WebSocket for React Native mobile (no SockJS framing — @stomp/stompjs native)
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        log.info("[WS] Configuring message broker with heartbeats (10s) and task scheduler");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registration) {
        // Increase buffer sizes to handle larger JWT tokens in headers
        registration.setMessageSizeLimit(128 * 1024); // 128 KB
        registration.setSendBufferSizeLimit(512 * 1024); // 512 KB
        registration.setSendTimeLimit(20000); // 20 seconds
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                try {
                    return handlePreSend(message);
                } catch (Exception ex) {
                    log.error("[WS] Unexpected error in preSend interceptor", ex);
                    return message;
                }
            }
        });
    }

    private Message<?> handlePreSend(Message<?> message) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand cmd = accessor.getCommand();
        if (cmd == null) return message; // Probably a heartbeat or other low-level frame

        log.debug("[WS] Inbound message: command={}", cmd);

        if (StompCommand.CONNECT.equals(cmd)) {
            String clientType = accessor.getFirstNativeHeader("X-Client-Type");
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            log.info("[WS] CONNECT received — clientType={} hasToken={}", clientType, authHeader != null);

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                UUID userId = extractUserIdFromToken(token);
                if (userId != null) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userId, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    accessor.setUser(auth);
                    log.info("[WS] CONNECT authenticated — userId={} clientType={}", userId, clientType);
                } else {
                    log.warn("[WS] CONNECT — token present but could not extract userId (invalid/expired token)");
                }
            } else {
                log.warn("[WS] CONNECT — missing or malformed Authorization header (clientType={})", clientType);
            }

        } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
            log.debug("[WS] SUBSCRIBE — destination={} user={}",
                    accessor.getDestination(),
                    accessor.getUser() != null ? accessor.getUser().getName() : "anonymous");

        } else if (StompCommand.DISCONNECT.equals(cmd)) {
            log.debug("[WS] DISCONNECT — user={}",
                    accessor.getUser() != null ? accessor.getUser().getName() : "anonymous");
        }

        return message;
    }

    /**
     * Validates the token's HMAC-SHA256 signature and expiry using the same manual
     * approach as {@code TokenService}, then extracts the {@code uid} (userId) claim.
     * Returns {@code null} if the token is invalid, expired, or missing the uid claim.
     */
    private UUID extractUserIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            // 1. Verify signature
            String expected = hmacSha256(parts[0] + "." + parts[1], jwtSecret);
            if (!expected.equals(parts[2])) {
                log.warn("[WS] JWT signature mismatch");
                return null;
            }

            // 2. Decode payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            // 3. Check expiry
            String expMatch = extractClaim(payloadJson, "exp");
            if (expMatch == null) return null;
            long exp = Long.parseLong(expMatch);
            if (Instant.now().getEpochSecond() >= exp) {
                log.warn("[WS] JWT expired");
                return null;
            }

            // 4. Extract userId from the 'uid' claim (added by TokenService.generateAccessToken)
            String uid = extractClaim(payloadJson, "uid");
            if (uid != null) {
                return UUID.fromString(uid);
            }

            // Fallback: if token predates the 'uid' claim, use 'sub' (authCredentialsId).
            // In this case we can't resolve userId without a DB lookup — skip setting principal.
            log.warn("[WS] JWT has no 'uid' claim — user will connect unauthenticated (old token format)");
            return null;

        } catch (Exception ex) {
            log.warn("[WS] JWT extraction failed: {}", ex.getMessage());
            return null;
        }
    }

    private String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    /** Extracts a simple string claim value from a JSON payload string. */
    private String extractClaim(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        if (start >= json.length()) return null;
        char first = json.charAt(start);
        if (first == '"') {
            // String value
            int end = json.indexOf('"', start + 1);
            return end > start ? json.substring(start + 1, end) : null;
        } else {
            // Numeric value
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                end++;
            }
            return end > start ? json.substring(start, end) : null;
        }
    }
}
