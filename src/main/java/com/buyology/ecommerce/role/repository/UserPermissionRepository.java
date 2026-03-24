package com.buyology.ecommerce.role.repository;

import com.buyology.ecommerce.role.domain.UserPermission;
import com.buyology.ecommerce.role.domain.UserPermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {

    List<UserPermission> findByIdUserId(UUID userId);

    List<UserPermission> findByIdPermissionId(UUID permissionId);

    boolean existsByIdUserIdAndIdPermissionId(UUID userId, UUID permissionId);

    void deleteByIdUserIdAndIdPermissionId(UUID userId, UUID permissionId);

    @Query("SELECT up FROM UserPermission up JOIN FETCH up.permission WHERE up.id.userId = :userId")
    List<UserPermission> findWithPermissionByUserId(@Param("userId") UUID userId);
}
