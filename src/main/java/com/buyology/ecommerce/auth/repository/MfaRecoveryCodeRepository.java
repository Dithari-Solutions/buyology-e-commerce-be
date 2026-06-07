package com.buyology.ecommerce.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.buyology.ecommerce.auth.domain.MfaRecoveryCode;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    Optional<MfaRecoveryCode> findByUserIdAndCodeHashAndUsedFalse(UUID userId, String codeHash);

    List<MfaRecoveryCode> findByUserId(UUID userId);

    @Modifying
    @Query("delete from MfaRecoveryCode r where r.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
