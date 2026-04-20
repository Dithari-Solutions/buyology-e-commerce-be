package com.buyology.ecommerce.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.domain.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findAllByAuthCredential(AuthCredentials authCredentials);

    List<RefreshToken> findByAuthCredentialAndRevokedFalse(AuthCredentials authCredentials);

    void deleteByExpiresAtBefore(Instant cutoff);

    /**
     * Bulk-revoke all active refresh tokens for a credential.
     * Called after a password reset to force re-login on every device.
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.updatedAt = :now " +
           "WHERE r.authCredential = :cred AND r.revoked = false")
    int revokeAllActiveByAuthCredential(
            @Param("cred") AuthCredentials cred,
            @Param("now") Instant now);
}
