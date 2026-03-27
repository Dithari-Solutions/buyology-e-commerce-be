package com.buyology.ecommerce.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<Users, UUID> {

    /**
     * Returns ACTIVE, non-admin users whose last activity (last login, or account
     * creation if they never logged in) is older than {@code cutoff}.
     */
    @Query("SELECT u FROM Users u " +
           "WHERE u.status = 'ACTIVE' " +
           "AND u.userType <> com.buyology.ecommerce.user.domain.Users.UserType.ADMIN " +
           "AND COALESCE(u.lastLoginAt, u.createdAt) < :cutoff")
    List<Users> findInactiveCustomersToBlock(@Param("cutoff") Instant cutoff);
}
