package com.buyology.ecommerce.role.service;

import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.role.domain.*;
import com.buyology.ecommerce.role.dto.AssignRoleToUserRequest;
import com.buyology.ecommerce.role.repository.*;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomRoleAssignmentTest {
    private final UserRoleRepository assignments = mock(UserRoleRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserRoleService service = new UserRoleService(assignments, users, roles);
    private Users person;
    private Role role;
    private AssignRoleToUserRequest request;

    @BeforeEach
    void setup() {
        person = new Users();
        person.setId(UUID.randomUUID());
        person.setUserType(Users.UserType.ADMIN);
        person.setStatus("ACTIVE");
        role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("INVENTORY_MANAGER");
        role.setIsSystem(false);
        request = new AssignRoleToUserRequest();
        request.setUserId(person.getId());
        request.setRoleId(role.getId());
        when(users.findById(person.getId())).thenReturn(Optional.of(person));
        when(roles.findById(role.getId())).thenReturn(Optional.of(role));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void assignsToActiveAdminUsingAuthenticatedActorForAudit() {
        Users actor = new Users();
        actor.setId(UUID.randomUUID());
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getId(), null, List.of()));
        request.setAssignedBy(UUID.randomUUID()); // Ignore caller-supplied audit identity.
        when(assignments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(201, service.assignRoleToUser(request).getStatusCode().value());
        ArgumentCaptor<UserRole> saved = ArgumentCaptor.forClass(UserRole.class);
        verify(assignments).save(saved.capture());
        assertEquals(actor.getId(), saved.getValue().getAssignedBy().getId());
        assertEquals(person.getId(), saved.getValue().getUser().getId());
    }

    @Test
    void customerMustBeExplicitlyPromotedBeforeReceivingStaffRole() {
        person.setUserType(Users.UserType.CUSTOMER);
        assertEquals(409, service.assignRoleToUser(request).getStatusCode().value());
        verify(assignments, never()).save(any());
    }

    @Test
    void suspendedAdminCannotReceiveCustomRole() {
        person.setStatus("SUSPENDED");
        assertEquals(409, service.assignRoleToUser(request).getStatusCode().value());
        verify(assignments, never()).save(any());
    }

    @Test
    void duplicateAssignmentDoesNotWrite() {
        when(assignments.existsByIdUserIdAndIdRoleId(person.getId(), role.getId())).thenReturn(true);
        assertEquals(409, service.assignRoleToUser(request).getStatusCode().value());
        verify(assignments, never()).save(any());
    }

    private void prepareRemoval(List<String> remainingRoles) {
        when(users.existsById(person.getId())).thenReturn(true);
        when(roles.existsById(role.getId())).thenReturn(true);
        when(assignments.existsByIdUserIdAndIdRoleId(person.getId(), role.getId())).thenReturn(true);
        when(assignments.findRoleNamesByUserId(person.getId())).thenReturn(remainingRoles);
    }

    @Test
    void removingLastStaffRoleCannotRestoreBlanketLegacyAdminAccess() {
        prepareRemoval(List.of("INVENTORY_MANAGER"));
        assertEquals(409, service.removeRoleFromUser(person.getId(), role.getId()).getStatusCode().value());
        verify(assignments, never()).deleteByIdUserIdAndIdRoleId(any(), any());
    }

    @Test
    void removesRoleWhenPersonHasAReplacement() {
        prepareRemoval(List.of("INVENTORY_MANAGER", "STOCK_VIEWER"));
        assertEquals(200, service.removeRoleFromUser(person.getId(), role.getId()).getStatusCode().value());
        verify(assignments).deleteByIdUserIdAndIdRoleId(person.getId(), role.getId());
    }

    @Test
    void lastSuperAdminCannotBeRemovedEvenWithAReplacementStaffRole() {
        role.setName("SUPERADMIN");
        prepareRemoval(List.of("SUPERADMIN", "INVENTORY_MANAGER"));
        when(assignments.findUserIdsByRoleName("SUPERADMIN")).thenReturn(List.of(person.getId()));
        assertEquals(409, service.removeRoleFromUser(person.getId(), role.getId()).getStatusCode().value());
        verify(assignments, never()).deleteByIdUserIdAndIdRoleId(any(), any());
    }
}
