package com.buyology.ecommerce.membership.repository;

import com.buyology.ecommerce.membership.domain.B2bMembershipApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2bMembershipApplicationRepository extends JpaRepository<B2bMembershipApplication, UUID> {

    List<B2bMembershipApplication> findAllByOrderByCreatedAtDesc();

    List<B2bMembershipApplication> findByStatusOrderByCreatedAtDesc(B2bMembershipApplication.ApplicationStatus status);

    Optional<B2bMembershipApplication> findByContactEmail(String contactEmail);

    /**
     * This user's current application — the most recent one.
     *
     * <p>Deliberately {@code findFirstBy...}, which emits {@code LIMIT 1}. The plain
     * {@code findByUserId} this replaces was a single-result query over a column with no unique
     * constraint anywhere — not in the entity and not in any migration — so the moment a user held
     * two application rows it threw {@code IncorrectResultSizeDataAccessException} instead of
     * returning one. That failed GET /api/user/profile outright, because {@code toResponse}
     * enriches every profile with the B2B application: one duplicate row in an ancillary table
     * took down the whole profile page for that customer, permanently, until the data was cleaned.
     *
     * <p>A unique constraint would be the wrong fix. Holding more than one application is
     * legitimate — an applicant rejected once may apply again — so the read has to name which row
     * it wants rather than assert there is only ever one. Newest wins: a re-application supersedes
     * what came before, which is what all three callers mean by "the" application.
     */
    Optional<B2bMembershipApplication> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByContactEmailAndStatusNot(String email, B2bMembershipApplication.ApplicationStatus status);
}
