package com.buyology.ecommerce.auth.repository;

import com.buyology.ecommerce.auth.domain.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {

    // Fetch the most recent unused OTP for an email (used for verification + rate-limit check)
    Optional<EmailOtp> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    // Bulk-delete all expired OTPs (called by the scheduled cleanup job)
    // Returns the number of deleted records so callers can log the result
    @Modifying
    @Transactional
    @Query("DELETE FROM EmailOtp e WHERE e.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    // Invalidate all unused OTPs for an email when a new one is issued
    @Modifying
    @Transactional
    @Query("UPDATE EmailOtp e SET e.used = true WHERE e.email = :email AND e.used = false")
    void invalidateAllForEmail(@Param("email") String email);
}
