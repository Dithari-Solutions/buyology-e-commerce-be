package com.buyology.ecommerce.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.buyology.ecommerce.auth.domain.AuthCredentials;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthCredentialRepository extends JpaRepository<AuthCredentials, UUID> {
    Optional<AuthCredentials> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<AuthCredentials> findByUserId(UUID userId);

    Optional<AuthCredentials> findByEmailAndProvider(String email, String provider);
}
