package com.buyology.ecommerce.auth.repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.buyology.ecommerce.auth.domain.RefreshToken;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Find refresh token by its value
    Optional<RefreshToken> findByToken(String token);

    // Optional: find all tokens for a specific auth credential (user/device
    // sessions)
    List<RefreshToken> findAllByAuthCredential(AuthCredentials authCredentials);

    // Optional: delete all expired tokens older than a certain timestamp
    void deleteByExpiresAtBefore(java.time.Instant cutoff);

    // Optional: find all active tokens for a user
    List<RefreshToken> findByAuthCredentialAndRevokedFalse(AuthCredentials authCredentials);
}
