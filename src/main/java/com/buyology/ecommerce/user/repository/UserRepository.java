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

    /** Paged lookup by user type — used to broadcast to customers without loading the
     *  entire users table into memory at once. */
    org.springframework.data.domain.Page<Users> findByUserType(
            Users.UserType userType, org.springframework.data.domain.Pageable pageable);

    /** Accounts in the given status whose soft-delete timestamp is past {@code cutoff}.
     *  Used by the account-deletion purge job to finalize deletions after the grace window. */
    List<Users> findByStatusAndDeletedAtBefore(String status, Instant cutoff);

    /** Admin user search by first/last name or any of the user's credential emails. */
    @Query("""
            SELECT DISTINCT u FROM Users u
            WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR EXISTS (SELECT 1 FROM com.buyology.ecommerce.auth.domain.AuthCredentials c
                          WHERE c.userId = u.id AND LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    org.springframework.data.domain.Page<Users> searchUsers(
            @Param("q") String q, org.springframework.data.domain.Pageable pageable);
}
