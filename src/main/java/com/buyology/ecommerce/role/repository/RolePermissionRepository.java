package com.buyology.ecommerce.role.repository;

import com.buyology.ecommerce.role.domain.RolePermission;
import com.buyology.ecommerce.role.domain.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
