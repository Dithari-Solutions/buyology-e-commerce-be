package com.buyology.ecommerce.user.repository;

import java.util.UUID;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface UserRepository extends JpaRepository<Users, UUID> {
}
