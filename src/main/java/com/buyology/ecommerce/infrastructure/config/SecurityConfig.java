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
                        .requestMatchers("/api/banner/**").permitAll()
                        // Payment webhook — must be reachable by Paymob without a JWT
                        .requestMatchers("/api/payments/webhook").permitAll()
                        // Auth endpoints are public
                        .requestMatchers("/auth/**").permitAll()
                        // Supplier registration and password-setup endpoints are public
                        .requestMatchers("/api/supplier/apply/**").permitAll()
                        .requestMatchers("/api/supplier/auth/**").permitAll()
                        // B2B membership password-setup endpoints are public (token-gated)
                        .requestMatchers("/api/membership/auth/**").permitAll()
                        // Admin API requires authentication; method-level @PreAuthorize handles role checks
                        .requestMatchers("/api/admin/**").authenticated()
                        .requestMatchers("/api/stores/**").authenticated()
                        // Order endpoints require authentication
                        .requestMatchers("/api/orders/**").authenticated()
                        .requestMatchers("/api/courier/orders/**").authenticated()
                        // All other requests
                        .anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Explicit allowlist — no wildcards. Driven by app.cors.allowed-origins
        // (env var CORS_ALLOWED_ORIGINS in CI/CD). Defaults to localhost for dev.
        List<String> allowed = java.util.Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
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
