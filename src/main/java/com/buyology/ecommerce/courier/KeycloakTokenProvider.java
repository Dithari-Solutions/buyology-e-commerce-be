package com.buyology.ecommerce.courier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class KeycloakTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTokenProvider.class);

    private final WebClient webClient;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakTokenProvider(
            @Value("${keycloak.server-url}") String serverUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.client-id}") String clientId,
            @Value("${keycloak.client-secret}") String clientSecret
    ) {
        this.webClient    = WebClient.builder().baseUrl(serverUrl).build();
        this.realm        = realm;
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Fetches a short-lived Keycloak service-account token via client_credentials grant.
     * Returns a ready-to-use "Bearer <token>" string.
     */
    public String getBearerToken() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                            .with("client_id", clientId)
                            .with("client_secret", clientSecret))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("access_token")) {
                throw new IllegalStateException("Keycloak token response missing access_token");
            }

            return "Bearer " + response.get("access_token");
        } catch (Exception ex) {
            log.error("Failed to obtain Keycloak service-account token: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Could not obtain courier service token", ex);
        }
    }
}
