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

    /**
     * Takes a write lock on one credential row, used to serialise "find or create the cart".
     *
     * <p>Cart creation is a read-then-insert with nothing between the two, so a page load that
     * fires several cart calls at once had every one of them find no ACTIVE cart and insert. On a
     * database carrying V17's {@code ux_cart_active_per_credential} partial unique index the loser
     * fails with a constraint violation, which the client is shown as "A record with the same
     * unique value already exists" — the error people hit on the checkout page. On a database
     * created after V17 that index was never built (V17 is guarded on the table already existing,
     * and Flyway runs before Hibernate creates it), so there the same race silently produces TWO
     * active carts and the items split invisibly across them.
     *
     * <p>Locking the credential first makes the pair atomic per shopper, and fixes both outcomes
     * with one mechanism that does not depend on the index being there. It is a real row lock, so
     * it holds across replicas, and it serialises nothing beyond the one shopper creating a cart.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from AuthCredentials c where c.id = :id")
    Optional<AuthCredentials> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
