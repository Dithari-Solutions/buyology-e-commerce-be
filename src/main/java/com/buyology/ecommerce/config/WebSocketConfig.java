package com.buyology.ecommerce.config;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

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
 * Web clients should also send {@code X-Client-Type: WEB}; mobile clients
 * send {@code X-Client-Type: MOBILE} (or omit it — defaults to MOBILE).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final AuthCredentialRepository authCredentialRepository;

    public WebSocketConfig(AuthCredentialRepository authCredentialRepository) {
        this.authCredentialRepository = authCredentialRepository;
    }

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
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        // Enables /user/{principal}/queue/... routing for private chat pushes
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                StompCommand cmd = accessor.getCommand();

                if (StompCommand.CONNECT.equals(cmd)) {
                    String clientType = accessor.getFirstNativeHeader("X-Client-Type");
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    log.info("[WS] CONNECT received — clientType={} hasToken={}",
                            clientType, authHeader != null);

                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            Claims claims = Jwts.parser()
                                    .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                            jwtSecret.getBytes()))
                                    .build()
                                    .parseSignedClaims(token)
                                    .getPayload();

                            // JWT sub is authCredentialsId — must resolve to actual userId,
                            // same as JwtAuthenticationFilter does for HTTP requests.
                            UUID authCredentialsId = UUID.fromString(claims.getSubject());
                            var credentials = authCredentialRepository.findById(authCredentialsId)
                                    .orElse(null);
                            if (credentials == null || !Boolean.TRUE.equals(credentials.getIsActive())) {
                                log.warn("[WS] CONNECT rejected — credentials not found or inactive: {}", authCredentialsId);
                                return message;
                            }

                            UUID userId = credentials.getUserId();
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            userId, null,
                                            List.of(new SimpleGrantedAuthority("ROLE_USER")));
                            accessor.setUser(auth);
                            log.info("[WS] CONNECT authenticated — userId={} clientType={}", userId, clientType);
                        } catch (Exception ex) {
                            log.warn("[WS] CONNECT rejected — invalid JWT: {}", ex.getMessage());
                        }
                    } else {
                        log.warn("[WS] CONNECT missing or malformed Authorization header — clientType={}", clientType);
                    }

                } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
                    log.info("[WS] SUBSCRIBE — destination={} user={}",
                            accessor.getDestination(),
                            accessor.getUser() != null ? accessor.getUser().getName() : "anonymous");

                } else if (StompCommand.DISCONNECT.equals(cmd)) {
                    log.info("[WS] DISCONNECT — user={}",
                            accessor.getUser() != null ? accessor.getUser().getName() : "anonymous");
                }
                return message;
            }
        });
    }
}
