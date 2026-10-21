package com.buyology.ecommerce.infrastructure.filter;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.service.TokenService;
import com.buyology.ecommerce.role.RbacPolicy;
import com.buyology.ecommerce.role.domain.Permission;
import com.buyology.ecommerce.role.domain.UserPermission;
import com.buyology.ecommerce.role.repository.*;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomRoleAccessTest {
    private final TokenService tokens = mock(TokenService.class);
    private final AuthCredentialRepository credentials = mock(AuthCredentialRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final RolePermissionRepository grants = mock(RolePermissionRepository.class);
    private final UserPermissionRepository overrides = mock(UserPermissionRepository.class);
    private final UUID userId = UUID.randomUUID();
    private final UUID credentialId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setup() {
        filter = new JwtAuthenticationFilter(tokens, credentials, users, roles, grants, overrides);
        AuthCredentials credential = new AuthCredentials();
        credential.setId(credentialId);
        credential.setUserId(userId);
        credential.setIsActive(true);
        Users user = new Users();
        user.setId(userId);
        user.setUserType(Users.UserType.ADMIN);
        user.setStatus("ACTIVE");
        when(tokens.validateAccessToken("test-token")).thenReturn(true);
        when(tokens.getAuthCredentialId("test-token")).thenReturn(credentialId);
        when(tokens.getAudience("test-token")).thenReturn("dashboard");
        when(credentials.findById(credentialId)).thenReturn(Optional.of(credential));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(roles.findRoleIdsByUserId(userId)).thenReturn(List.of(roleId));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    private List<String> authenticate() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/products");
        request.addHeader("Authorization", "Bearer test-token");
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? List.of() : auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
    }

    private void customRole() {
        when(roles.findCustomRoleNamesByUserId(userId)).thenReturn(List.of("INVENTORY_MANAGER"));
        when(roles.findRoleNamesByUserId(userId)).thenReturn(List.of("INVENTORY_MANAGER"));
    }

    @Test
    void customInventoryRoleCannotUseLegacyAdminBypass() throws Exception {
        customRole();
        when(grants.findPermissionCodesByRoleIds(List.of(roleId))).thenReturn(List.of("store:read", "store:product:update"));
        var authorities = authenticate();
        assertTrue(authorities.containsAll(List.of("ROLE_INVENTORY_MANAGER", "store:read", "store:product:update")));
        assertFalse(authorities.contains("ROLE_ADMIN"));
        assertFalse(authorities.contains("user:read"));
        assertFalse(new RbacPolicy(false).legacyAdmin());
    }

    @Test
    void customRoleAlsoDisablesExplicitLegacyAdminFallback() throws Exception {
        customRole();
        when(roles.findRoleNamesByUserId(userId)).thenReturn(List.of("INVENTORY_MANAGER", "ADMIN"));
        assertFalse(authenticate().contains("ROLE_ADMIN"));
        assertFalse(new RbacPolicy(false).legacyAdmin());
    }

    @Test
    void preExistingSystemOnlyAdminsKeepCompatibility() throws Exception {
        when(roles.findRoleNamesByUserId(userId)).thenReturn(List.of("STORE_ADMIN"));
        assertTrue(authenticate().contains("ROLE_ADMIN"));
        assertTrue(new RbacPolicy(false).legacyAdmin());
    }

    @Test
    void permissionRevocationAppliesToTheSameTokenOnTheNextRequest() throws Exception {
        customRole();
        when(grants.findPermissionCodesByRoleIds(List.of(roleId)))
                .thenReturn(List.of("store:read", "store:product:update"), List.of("store:read"));
        assertTrue(authenticate().contains("store:product:update"));
        assertFalse(authenticate().contains("store:product:update"));
    }

    @Test
    void directDenyOverridesRoleGrant() throws Exception {
        customRole();
        when(grants.findPermissionCodesByRoleIds(List.of(roleId))).thenReturn(List.of("store:product:update"));
        Permission permission = new Permission();
        permission.setCode("store:product:update");
        UserPermission deny = new UserPermission();
        deny.setPermission(permission);
        deny.setEffect(UserPermission.Effect.DENY);
        when(overrides.findWithPermissionByUserId(userId)).thenReturn(List.of(deny));
        assertFalse(authenticate().contains("store:product:update"));
    }

    @Test
    void customStaffTokenIsRejectedOnStorefrontAudience() throws Exception {
        customRole();
        when(tokens.getAudience("test-token")).thenReturn("web");
        assertTrue(authenticate().isEmpty());
    }

    @Test
    void superAdminRetainsRecoveryAccessWithACustomRole() throws Exception {
        customRole();
        when(roles.findRoleNamesByUserId(userId)).thenReturn(List.of("SUPERADMIN", "INVENTORY_MANAGER"));
        assertTrue(authenticate().contains("ROLE_SUPERADMIN"));
    }
}
