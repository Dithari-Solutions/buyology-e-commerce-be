package com.buyology.ecommerce.infrastructure.filter;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.service.TokenService;
import com.buyology.ecommerce.role.domain.UserPermission;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.UserPermissionRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Validates the JWT Bearer token on each request and populates the SecurityContext with:
 * - ROLE_<USER_TYPE>   (e.g. ROLE_ADMIN, ROLE_CUSTOMER) derived from Users.userType
 * - ROLE_<ROLE_NAME>   for every role assigned in user_roles (e.g. ROLE_SUPERADMIN, ROLE_COURIER_ADMIN)
 * - <permission:code>  for every permission that role grants (e.g. courier:read)
 * Direct user-level DENY overrides remove a permission; ALLOW overrides add one.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final AuthCredentialRepository authCredentialRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;

    public JwtAuthenticationFilter(
            TokenService tokenService,
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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        if (!tokenService.validateAccessToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID authCredentialsId = extractSubject(token);
        if (authCredentialsId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthCredentials credentials = authCredentialRepository.findById(authCredentialsId).orElse(null);
        if (credentials == null || !Boolean.TRUE.equals(credentials.getIsActive())) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId = credentials.getUserId();

        // Reject suspended users — their tokens are structurally valid but the account is blocked
        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty() || "SUSPENDED".equals(userOpt.get().getStatus())) {
            filterChain.doFilter(request, response);
            return;
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        // 1. Add ROLE_ authority based on UserType (ADMIN / CUSTOMER)
        if (userOpt.get().getUserType() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + userOpt.get().getUserType().name()));
        }

        // 2. Add ROLE_ authorities from assigned roles + collect role IDs for permission lookup
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        List<String> roleNames = userRoleRepository.findRoleNamesByUserId(userId);

        // Enforce: SUPPLIER role tokens are only valid with audience "dashboard"
        boolean isSupplier = roleNames.stream().anyMatch("SUPPLIER"::equalsIgnoreCase);
        if (isSupplier) {
            String audience = extractAudience(token);
            if (!"dashboard".equals(audience)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        roleNames.forEach(name -> authorities.add(new SimpleGrantedAuthority("ROLE_" + name)));

        // 3. Add permission codes derived from roles
        if (!roleIds.isEmpty()) {
            rolePermissionRepository.findPermissionCodesByRoleIds(roleIds)
                    .forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        }

        // 4. Apply direct user-level permission overrides (ALLOW adds, DENY removes)
        userPermissionRepository.findWithPermissionByUserId(userId).forEach(up -> {
            String code = up.getPermission().getCode();
            if (up.getEffect() == UserPermission.Effect.DENY) {
                authorities.removeIf(a -> a.getAuthority().equals(code));
            } else {
                SimpleGrantedAuthority grant = new SimpleGrantedAuthority(code);
                if (!authorities.contains(grant)) {
                    authorities.add(grant);
                }
            }
        });

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private UUID extractSubject(String token) {
        try {
            String[] parts = token.split("\\.");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            String sub = payloadJson.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
            return UUID.fromString(sub);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractAudience(String token) {
        try {
            String[] parts = token.split("\\.");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            if (!payloadJson.contains("\"aud\"")) return "web";
            return payloadJson.replaceAll(".*\"aud\":\"([^\"]+)\".*", "$1");
        } catch (Exception e) {
            return "web";
        }
    }
}
