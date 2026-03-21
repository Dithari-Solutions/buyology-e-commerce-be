package com.buyology.ecommerce.user.repository;

import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfilesRepository extends JpaRepository<UserProfiles, UUID> {

    Optional<UserProfiles> findByUser(Users user);
}
