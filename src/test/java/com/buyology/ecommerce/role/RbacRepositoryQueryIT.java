package com.buyology.ecommerce.role;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.role.repository.PermissionRepository;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.RoleRepository;
import com.buyology.ecommerce.role.repository.UserPermissionRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes every RBAC/admin repository query added for the roles console.
 *
 * <p>Derived query names and JPQL strings are only validated when Spring Data builds the repository,
 * and malformed ones fail at runtime rather than compile time — so each query is run once here.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=update"
})
class RbacRepositoryQueryIT {

    @Autowired UserRepository userRepository;
    @Autowired AuthCredentialRepository authCredentialRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired RolePermissionRepository rolePermissionRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired UserPermissionRepository userPermissionRepository;

    @Test
    void everyQueryUsedByTheRolesConsoleExecutes() {
        UUID missing = UUID.randomUUID();

        // Admin list: server-side user-type filter + type-scoped search.
        assertThat(userRepository.findByUserType(Users.UserType.ADMIN, PageRequest.of(0, 5))).isNotNull();
        assertThat(userRepository.searchUsersByType(Users.UserType.ADMIN, "zzz", PageRequest.of(0, 5)))
                .isNotNull();

        // Email conflict diagnosis: case-insensitive, provider-agnostic.
        assertThat(authCredentialRepository.findAllByEmailIgnoreCase("Nobody@Example.COM")).isEmpty();

        // Roles list: batched permission codes + holder counts.
        List<Object[]> codes = rolePermissionRepository.findAllRoleIdToPermissionCode();
        assertThat(codes).allSatisfy(row -> {
            assertThat(row[0]).isInstanceOf(UUID.class);
            assertThat(row[1]).isInstanceOf(String.class);
        });
        List<Object[]> holders = userRoleRepository.countHoldersPerRole();
        assertThat(holders).allSatisfy(row -> {
            assertThat(row[0]).isInstanceOf(UUID.class);
            assertThat(row[1]).isInstanceOf(Number.class);
        });

        // Delete/edit guards.
        assertThat(userRoleRepository.countByIdRoleId(missing)).isZero();
        assertThat(rolePermissionRepository.countByIdPermissionId(missing)).isZero();
        assertThat(userPermissionRepository.countByIdPermissionId(missing)).isZero();

        // Cascade clear before a role delete (bulk @Modifying statement).
        rolePermissionRepository.deleteByRoleId(missing);

        // Untouched but exercised alongside the above.
        assertThat(roleRepository.findAll()).isNotNull();
        assertThat(permissionRepository.findAll()).isNotNull();
        assertThat(userRoleRepository.findByIdRoleId(missing)).isEmpty();
    }
}
