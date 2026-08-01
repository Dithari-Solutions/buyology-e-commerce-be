package com.buyology.ecommerce.role.repository;

import com.buyology.ecommerce.role.domain.UserRole;
import com.buyology.ecommerce.role.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByIdUserId(UUID userId);

    List<UserRole> findByIdRoleId(UUID roleId);

    boolean existsByIdUserIdAndIdRoleId(UUID userId, UUID roleId);

    void deleteByIdUserIdAndIdRoleId(UUID userId, UUID roleId);

    @Query("SELECT r.name FROM UserRole ur JOIN ur.role r WHERE ur.id.userId = :userId")
    List<String> findRoleNamesByUserId(@Param("userId") UUID userId);

    @Query("SELECT r.id FROM UserRole ur JOIN ur.role r WHERE ur.id.userId = :userId")
    List<UUID> findRoleIdsByUserId(@Param("userId") UUID userId);

    /** User IDs that hold the given role name (e.g. "SUPERADMIN"). */
    @Query("SELECT ur.id.userId FROM UserRole ur JOIN ur.role r WHERE r.name = :roleName")
    List<UUID> findUserIdsByRoleName(@Param("roleName") String roleName);

    /** Distinct user IDs holding any of the given role names (e.g. PROCUREMENT + SUPERADMIN). */
    @Query("SELECT DISTINCT ur.id.userId FROM UserRole ur JOIN ur.role r WHERE r.name IN :roleNames")
    List<UUID> findUserIdsByRoleNameIn(@Param("roleNames") Set<String> roleNames);

    long countByIdRoleId(UUID roleId);

    /** {@code [roleId, holderCount]} for every role that has at least one holder — powers the
     *  "assigned to N admins" column without an N+1 across roles. */
    @Query("SELECT ur.id.roleId, COUNT(ur) FROM UserRole ur GROUP BY ur.id.roleId")
    List<Object[]> countHoldersPerRole();
}
