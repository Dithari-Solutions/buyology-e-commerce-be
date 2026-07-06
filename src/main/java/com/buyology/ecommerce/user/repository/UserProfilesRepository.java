package com.buyology.ecommerce.user.repository;

import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfilesRepository extends JpaRepository<UserProfiles, UUID> {

    Optional<UserProfiles> findByUser(Users user);

    Optional<UserProfiles> findByUserId(UUID userId);

    /** Batch variant of {@link #findByUserId} — for enriching paged order lists without an N+1. */
    List<UserProfiles> findByUserIdIn(Collection<UUID> userIds);

    /**
     * Atomically debit {@code cost} tokens iff the balance is sufficient. Returns the
     * number of rows updated: 1 on success, 0 when the balance was below {@code cost}.
     * Avoids the read-modify-write race of get/set under concurrent redeems.
     */
    @Modifying(clearAutomatically = true)
    @Query("update UserProfiles p set p.tokens = p.tokens - :cost, p.updatedAt = CURRENT_TIMESTAMP "
            + "where p.user.id = :userId and p.tokens >= :cost")
    int debitTokens(@Param("userId") UUID userId, @Param("cost") int cost);
}
