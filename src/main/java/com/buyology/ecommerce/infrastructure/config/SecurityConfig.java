package com.buyology.ecommerce.infrastructure.config;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.service.TokenService;
import com.buyology.ecommerce.infrastructure.filter.JwtAuthenticationFilter;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.UserPermissionRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8080}")
    private String allowedOriginsCsv;

    private final TokenService tokenService;
    private final AuthCredentialRepository authCredentialRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;

    public SecurityConfig(TokenService tokenService,
                          AuthCredentialRepository authCredentialRepository,
                          UserRepository userRepository,
                          UserRoleRepository userRoleRepository,
                          RolePermissionRepository rolePermissionRepository,
                          UserPermissionRepository userPermissionRepository) {
        this.tokenService = tokenService;
        this.authCredentialRepository = authCredentialRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionRepository = userPermissionRepository;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(
                tokenService,
                authCredentialRepository,
                userRepository,
                userRoleRepository,
                rolePermissionRepository,
                userPermissionRepository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .contentTypeOptions(c -> {})
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                + "script-src 'self'; "
                                + "style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data: https:; "
                                + "font-src 'self' data:; "
                                + "connect-src 'self' https:; "
                                + "frame-ancestors 'none'; "
                                + "base-uri 'self'; "
                                + "form-action 'self'"))
                )
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Allow access to Swagger endpoints
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**"
                        ).permitAll()
                        // Allow static resources
                        .requestMatchers("/story/**", "/product/**", "/review/**", "/store/**", "/user/**", "/css/**", "/js/**", "/images/**").permitAll()
                        // Health probes (k8s/load-balancer) + Prometheus scrape — no auth.
                        // Metrics are low-sensitivity; restrict /actuator/prometheus at the
                        // network layer in prod.
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/prometheus").permitAll()
                        // WebSocket/SockJS handshake — authentication happens at the STOMP
                        // CONNECT frame (JWT), not at the HTTP handshake.
                        .requestMatchers("/ws/**", "/ws-native/**").permitAll()
                        .requestMatchers("/api/banner/**").permitAll()
                        // Public storefront / marketing reads. Write endpoints under these
                        // paths are individually guarded with method-level @PreAuthorize, which
                        // is still enforced under permitAll, so only reads are actually open.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/product/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/category/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/brand").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/countries/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/currency/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/reviews/**").permitAll()
                        // Public return/refund policy (window days) for the storefront. The
                        // admin read/write lives under /api/admin/refund-settings (authenticated).
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/refund-settings").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/questions/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/news").permitAll()
                        // Stories: reads + view/like (like/unlike self-enforce auth in-controller)
                        .requestMatchers("/api/story/**").permitAll()
                        // Newsletter subscribe/unsubscribe (unsubscribe is an emailed GET link)
                        .requestMatchers("/api/newsletter/**").permitAll()
                        // Storefront visitor beacon — the whole point is to count visitors who are
                        // not logged in, so it cannot require a JWT. Write-only (no data is
                        // readable here) and throttled by the ANALYTICS_BEACON rate-limit tier
                        // (240 req/min per IP, degrading to a per-instance bucket if Redis is down);
                        // the counts are read back under /api/admin/analytics/**, which stays
                        // authenticated and permission-guarded.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/analytics/visit").permitAll()
                        // Storefront AI assistant. Public for the same reason the beacon is: the
                        // shoppers most likely to ask "do you have this in stock?" are not logged
                        // in, and a login wall would leave the assistant answering only the
                        // customers who least need it. The caller controls nothing but their own
                        // question — no history, no prompt, no model — because the transcript and
                        // the prompt are both built server-side. Spend is bounded by the ASSISTANT
                        // rate-limit tier (per IP), the per-conversation message ceiling and the
                        // per-visitor daily conversation cap. Transcripts are read back under
                        // /api/admin/assistant/**, which stays authenticated and permission-guarded.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/assistant/chat").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/assistant/status").permitAll()
                        // B2B inquiry contact form (admin B2B endpoints live under /api/admin/**)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/b2b/inquiries").permitAll()
                        // Contact verification (email/phone OTP) used by public supplier & B2B apply forms
                        .requestMatchers("/api/verify/**").permitAll()
                        // Payment webhook — must be reachable by Paymob without a JWT
                        .requestMatchers("/api/payments/webhook").permitAll()
                        // Redirect-confirm fallback — HMAC-authenticated, must work even
                        // after the shopper's JWT expires during the 3-D Secure step
                        .requestMatchers("/api/payments/confirm-redirect").permitAll()
                        // Quiqup delivery webhook (STAGING test module) — Quiqup POSTs callbacks
                        // here without a JWT. Only logs events; never mutates a real order.
                        .requestMatchers("/api/quiqup/webhook").permitAll()
                        // All other payment endpoints require authentication; ownership /
                        // role checks are enforced at the method/service layer.
                        .requestMatchers("/api/payments/**").authenticated()
                        // Auth endpoints are public
                        .requestMatchers("/auth/**").permitAll()
                        // Supplier registration and password-setup endpoints are public
                        .requestMatchers("/api/supplier/apply/**").permitAll()
                        .requestMatchers("/api/supplier/auth/**").permitAll()
                        // B2B membership password-setup endpoints are public (token-gated)
                        .requestMatchers("/api/membership/auth/**").permitAll()
                        // B2B membership application is submitted from the public sign-up flow
                        // (no login). The applicant sets their password here; the account is
                        // only activated once an admin approves. Contact email + phone are still
                        // OTP-verified via the public /api/verify/** endpoints before submit.
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/membership/apply").permitAll()
                        // Admin API requires authentication; method-level @PreAuthorize handles role checks
                        .requestMatchers("/api/admin/**").authenticated()
                        // Public storefront contact data (active stores + locations + hours)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/stores/public").permitAll()
                        .requestMatchers("/api/stores/**").authenticated()
                        // Order endpoints require authentication
                        .requestMatchers("/api/orders/**").authenticated()
                        .requestMatchers("/api/courier/orders/**").authenticated()
                        // Per-user resources — must be authenticated; ownership is enforced
                        // at the service layer (see SecurityUtils.requireSelf*).
                        .requestMatchers("/api/cart/**").authenticated()
                        .requestMatchers("/api/favorites/**").authenticated()
                        .requestMatchers("/api/users/**").authenticated()
                        .requestMatchers("/api/membership/**").authenticated()
                        .requestMatchers("/api/game/**").authenticated()
                        // Review/question reads are public; writes are gated by
                        // method-level @PreAuthorize + ownership checks in the service.
                        // Deny-by-default: every endpoint not explicitly permitted above
                        // requires authentication. A forgotten matcher fails closed (401),
                        // not open.
                        .anyRequest().authenticated())
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }

    /**
     * Origins allowed on every deployment, whatever {@code CORS_ALLOWED_ORIGINS} says.
     *
     * <p>{@code app.cors.allowed-origins} has no default — it resolves straight from the env var —
     * so before this list existed, the storefront's access to the API depended on a value set
     * separately on each server. Getting it wrong there is invisible from the code and produces the
     * least obvious failure CORS has: every request works in curl and fails in the browser.
     *
     * <p>These are our own first-party frontends, so pinning them here rather than in per-server
     * configuration is the honest place for them. It also means they cannot be revoked without a
     * code change — which is the point, and the tradeoff to remember before adding anything that is
     * not ours. Third-party or short-lived origins still belong in the env var.
     */
    static final List<String> BUILT_IN_ALLOWED_ORIGINS = List.of(
            "https://v2.buyology.online");

    /**
     * The effective allowlist: whatever the environment configured, plus {@link
     * #BUILT_IN_ALLOWED_ORIGINS}, in that order and without duplicates.
     *
     * <p>Package-private and static so {@code SecurityConfigCorsTest} can pin it without standing
     * up the whole filter chain.
     */
    static List<String> resolveAllowedOrigins(String configuredCsv) {
        // Normalise the null before splitting, not inside the split: a ternary whose branches are
        // String and String[] resolves to their least upper bound, which Arrays.stream cannot take.
        String csv = configuredCsv == null ? "" : configuredCsv;
        List<String> configured = java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // Fail fast: a wildcard origin combined with allowCredentials=true is both rejected by the
        // browser and a security hole. Still refuse to start with it, and still refuse to start on
        // an empty value — the built-ins would mask a wiped CORS_ALLOWED_ORIGINS, letting the
        // storefront keep working while the dashboard silently lost access.
        if (configured.isEmpty() || configured.contains("*")) {
            throw new IllegalStateException(
                    "app.cors.allowed-origins must be a non-empty explicit allowlist (no '*') "
                    + "because credentials are allowed. Configured value: '" + configuredCsv + "'");
        }

        List<String> allowed = new java.util.ArrayList<>(configured);
        for (String builtIn : BUILT_IN_ALLOWED_ORIGINS) {
            if (!allowed.contains(builtIn)) {
                allowed.add(builtIn);
            }
        }
        return List.copyOf(allowed);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> allowed = resolveAllowedOrigins(allowedOriginsCsv);
        log.info("CORS allowlist: {}", String.join(", ", allowed));
        configuration.setAllowedOrigins(allowed);

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin",
                "X-Auth-Credential-Id", "X-Client-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization", "Set-Cookie"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
