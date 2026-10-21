package com.buyology.ecommerce.role.service;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.role.domain.*;
import com.buyology.ecommerce.role.dto.*;
import com.buyology.ecommerce.role.repository.*;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomRoleServiceTest {
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final RolePermissionRepository grants = mock(RolePermissionRepository.class);
    private final UserRoleRepository assignments = mock(UserRoleRepository.class);
    private final RoleService service = new RoleService(roles, permissions, grants, assignments,
            mock(UserRepository.class), mock(AuthCredentialRepository.class));

    private Permission permission(String code) {
        Permission p = new Permission();
        p.setId(UUID.randomUUID());
        p.setCode(code);
        when(permissions.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void createsNamedCustomRoleAndSelectedPermissionsTogether() {
        Permission read = permission("store:read");
        Permission update = permission("store:product:update");
        when(roles.save(any())).thenAnswer(inv -> {
            Role role = inv.getArgument(0);
            role.setId(UUID.randomUUID());
            return role;
        });
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("Inventory Manager");
        request.setDescription("Manage local stock");
        request.setIsSystem(true); // Client cannot elevate a custom role to a built-in role.
        request.setPermissionIds(List.of(read.getId(), update.getId(), read.getId()));
        var result = service.createRole(request);
        assertEquals(201, result.getStatusCode().value());
        var role = result.getBody().getData();
        assertEquals("INVENTORY_MANAGER", role.getName());
        assertFalse(role.getIsSystem());
        assertEquals(List.of("store:product:update", "store:read"), role.getPermissionCodes());
        verify(grants, times(2)).save(any(RolePermission.class));
    }

    @Test
    void unknownPermissionRejectsCreationBeforeAnyWrites() {
        Permission read = permission("store:read");
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("Inventory Manager");
        request.setPermissionIds(List.of(read.getId(), UUID.randomUUID()));
        assertEquals(400, service.createRole(request).getStatusCode().value());
        verify(roles, never()).save(any());
        verifyNoInteractions(grants);
    }

    @Test
    void rejectsDuplicateNormalizedName() {
        when(roles.existsByName("INVENTORY_MANAGER")).thenReturn(true);
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("inventory-manager");
        assertEquals(409, service.createRole(request).getStatusCode().value());
        verify(roles, never()).save(any());
    }

    @Test
    void roleWithMembersCannotBeDeleted() {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("INVENTORY_MANAGER");
        role.setIsSystem(false);
        when(roles.findById(role.getId())).thenReturn(Optional.of(role));
        when(assignments.countByIdRoleId(role.getId())).thenReturn(2L);
        assertEquals(409, service.deleteRole(role.getId()).getStatusCode().value());
        verify(roles, never()).delete(any());
    }

    @Test
    void superAdminPermissionMatrixIsProtected() {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("SUPERADMIN");
        when(roles.findById(role.getId())).thenReturn(Optional.of(role));
        SetRolePermissionsRequest request = new SetRolePermissionsRequest();
        request.setPermissionIds(List.of());
        assertEquals(409, service.setRolePermissions(role.getId(), request).getStatusCode().value());
        verifyNoInteractions(grants);
    }
}
