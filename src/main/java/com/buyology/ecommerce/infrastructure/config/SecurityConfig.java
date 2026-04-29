package com.buyology.ecommerce.infrastructure.config;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.service.TokenService;
import com.buyology.ecommerce.infrastructure.filter.JwtAuthenticationFilter;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.UserPermissionRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
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
                        // Payment webhook — must be reachable by Paymob without a JWT
                        .requestMatchers("/api/payments/webhook").permitAll()
                        // Auth endpoints are public
                        .requestMatchers("/auth/**").permitAll()
                        // Supplier registration and password-setup endpoints are public
                        .requestMatchers("/api/supplier/apply/**").permitAll()
                        .requestMatchers("/api/supplier/auth/**").permitAll()
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

        // Allow all origins via patterns to support mobile apps and various dev environments
        // while still allowing credentials (which setAllowedOrigins("*") would forbid).
        configuration.setAllowedOriginPatterns(List.of("*"));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
