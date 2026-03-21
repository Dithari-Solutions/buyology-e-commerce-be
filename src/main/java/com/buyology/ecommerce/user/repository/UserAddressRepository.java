package com.buyology.ecommerce.user.repository;

import com.buyology.ecommerce.user.domain.UserAddress;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {

    List<UserAddress> findAllByUser(Users user);

    Optional<UserAddress> findByIdAndUser(UUID id, Users user);

    Optional<UserAddress> findByUserAndIsDefaultTrue(Users user);

    // Clears the default flag from all addresses for a user before setting a new default
    @Modifying
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.user = :user")
    void clearDefaultForUser(@Param("user") Users user);
}
