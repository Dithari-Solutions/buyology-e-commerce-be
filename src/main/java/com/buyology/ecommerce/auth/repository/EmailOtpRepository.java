package com.buyology.ecommerce.auth.repository;

import com.buyology.ecommerce.auth.domain.EmailOtp;
import com.buyology.ecommerce.auth.domain.EmailOtp.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {

    // ── Type-aware lookups (preferred) ────────────────────────────────────────

    /**
     * Returns the most recent unused OTP for a given email + flow type.
     * Used for verification and rate-limit checks.
     */
    Optional<EmailOtp> findTopByEmailAndUsedFalseAndTypeOrderByCreatedAtDesc(
            String email, OtpType type);

    // ── Legacy lookup (kept for backward compat — no new code should call this) ─

    Optional<EmailOtp> findTopByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    // ── Bulk operations ───────────────────────────────────────────────────────

    /**
     * Invalidate all unused OTPs for a specific email + type before issuing a new one.
     * Scoped to a single type so signup and password-reset OTPs don't cancel each other.
     */
    @Modifying
    @Transactional
    @Query("UPDATE EmailOtp e SET e.used = true WHERE e.email = :email AND e.type = :type AND e.used = false")
    void invalidateAllForEmailAndType(@Param("email") String email, @Param("type") OtpType type);

    /** Original invalidation — invalidates ALL types. Kept for backward compatibility. */
    @Modifying
    @Transactional
    @Query("UPDATE EmailOtp e SET e.used = true WHERE e.email = :email AND e.used = false")
    void invalidateAllForEmail(@Param("email") String email);

    /** Delete all expired OTP records (called by the scheduled cleanup job). */
    @Modifying
    @Transactional
    @Query("DELETE FROM EmailOtp e WHERE e.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
