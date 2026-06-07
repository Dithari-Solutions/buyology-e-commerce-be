package com.buyology.ecommerce.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.buyology.ecommerce.auth.domain.MfaCredential;

public interface MfaCredentialRepository extends JpaRepository<MfaCredential, UUID> {

    Optional<MfaCredential> findByUserId(UUID userId);

    @Modifying
    @Query("delete from MfaCredential m where m.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
