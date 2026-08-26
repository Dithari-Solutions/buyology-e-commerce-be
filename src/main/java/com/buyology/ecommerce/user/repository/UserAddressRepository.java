package com.buyology.ecommerce.user.repository;

import com.buyology.ecommerce.user.domain.UserAddress;
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
public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {

    List<UserAddress> findAllByUser(Users user);

    Optional<UserAddress> findByIdAndUser(UUID id, Users user);

    Optional<UserAddress> findByUserAndIsDefaultTrue(Users user);

    /**
     * A user's addresses, their default first, by users.id rather than the entity.
     *
     * <p>For callers that hold only the id and have no reason to load the whole user — the courier
     * fee charge, which needs a billing address for the Paymob page and previously sent "NA" for
     * every line of it.
     */
    @Query("SELECT a FROM UserAddress a WHERE a.user.id = :userId "
            + "ORDER BY a.isDefault DESC, a.createdAt ASC")
    List<UserAddress> findByUserIdPreferringDefault(@Param("userId") UUID userId);

    /** Batch-load all addresses for a set of users — for enriching admin user/order lists without an N+1. */
    @Query("SELECT a FROM UserAddress a WHERE a.user.id IN :userIds")
    List<UserAddress> findByUserIds(@Param("userIds") Collection<UUID> userIds);

    // Clears the default flag from all addresses for a user before setting a new default
    @Modifying
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.user = :user")
    void clearDefaultForUser(@Param("user") Users user);
}
