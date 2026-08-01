package com.buyology.ecommerce.role.repository;

import com.buyology.ecommerce.role.domain.RolePermission;
import com.buyology.ecommerce.role.domain.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByIdRoleId(UUID roleId);

    List<RolePermission> findByIdPermissionId(UUID permissionId);

    boolean existsByIdRoleIdAndIdPermissionId(UUID roleId, UUID permissionId);

    void deleteByIdRoleIdAndIdPermissionId(UUID roleId, UUID permissionId);

    @Query("SELECT p.code FROM RolePermission rp JOIN rp.permission p WHERE rp.id.roleId IN :roleIds")
    List<String> findPermissionCodesByRoleIds(@Param("roleIds") List<UUID> roleIds);

    long countByIdPermissionId(UUID permissionId);

    /**
     * Clears a role's grants ahead of deleting the role itself.
     *
     * <p>A bulk {@code @Modifying} delete rather than a derived one so the statement runs immediately:
     * a derived delete only queues entity removals, and Hibernate does not guarantee it will order
     * them before the parent role's delete, which would trip the {@code role_permissions} foreign key.
     */
    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.id.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") UUID roleId);

    /** {@code [roleId, permissionCode]} pairs for every role — lets the roles list report its own
     *  permissions in one query instead of an N+1 across roles. */
    @Query("SELECT rp.id.roleId, p.code FROM RolePermission rp JOIN rp.permission p")
    List<Object[]> findAllRoleIdToPermissionCode();
}
