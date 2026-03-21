package com.buyology.ecommerce.user.repository;

import com.buyology.ecommerce.user.domain.PhoneOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhoneOtpRepository extends JpaRepository<PhoneOtp, UUID> {

    // Most recent unused OTP for this user + phone combination
    Optional<PhoneOtp> findTopByUserIdAndPhoneNumberAndUsedFalseOrderByCreatedAtDesc(
            UUID userId, String phoneNumber);

    // Used for resend cooldown — find the most recent OTP regardless of used status
    Optional<PhoneOtp> findTopByUserIdAndPhoneNumberOrderByCreatedAtDesc(
            UUID userId, String phoneNumber);

    @Modifying
    @Query("DELETE FROM PhoneOtp p WHERE p.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
