package com.buyology.ecommerce.role.repository;

import com.buyology.ecommerce.role.domain.UserRole;
import com.buyology.ecommerce.role.domain.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByIdUserId(UUID userId);

    List<UserRole> findByIdRoleId(UUID roleId);

    boolean existsByIdUserIdAndIdRoleId(UUID userId, UUID roleId);

    void deleteByIdUserIdAndIdRoleId(UUID userId, UUID roleId);
}
