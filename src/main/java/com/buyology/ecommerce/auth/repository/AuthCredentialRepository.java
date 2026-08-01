package com.buyology.ecommerce.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.buyology.ecommerce.auth.domain.AuthCredentials;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthCredentialRepository extends JpaRepository<AuthCredentials, UUID> {
    Optional<AuthCredentials> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<AuthCredentials> findByUserId(UUID userId);

    /** Batch variant of {@link #findByUserId} — used to enrich paged order lists without an N+1. */
    List<AuthCredentials> findByUserIdIn(Collection<UUID> userIds);

    Optional<AuthCredentials> findByEmailAndProvider(String email, String provider);

    List<AuthCredentials> findAllByEmailAndProvider(String email, String provider);

    /**
     * Every credential holding this email, regardless of provider and letter case.
     *
     * <p>Emails are stored inconsistently: {@code AdminUserService} and the B2B/supplier flows
     * lower-case before saving, while {@code AuthService.signup} persists whatever the user typed.
     * A case-sensitive {@code =} therefore both misses real duplicates and reports phantom ones, so
     * every "is this email taken?" check must go through this method.
     */
    List<AuthCredentials> findAllByEmailIgnoreCase(String email);
}
