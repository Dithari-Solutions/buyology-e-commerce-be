package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.membership.domain.B2bMembership;
import com.buyology.ecommerce.membership.domain.B2bMembership.MembershipStatus;
import com.buyology.ecommerce.membership.domain.CreditUsage;
import com.buyology.ecommerce.membership.repository.B2bMembershipRepository;
import com.buyology.ecommerce.membership.repository.CreditUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class B2bMembershipLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(B2bMembershipLifecycleService.class);
    private static final int TRASH_RETENTION_DAYS = 30;
    private static final List<CreditUsage.Status> OUTSTANDING_STATUSES = List.of(
            CreditUsage.Status.OUTSTANDING, CreditUsage.Status.PARTIAL, CreditUsage.Status.OVERDUE);

    private final B2bMembershipRepository membershipRepository;
    private final CreditUsageRepository creditUsageRepository;

    public B2bMembershipLifecycleService(B2bMembershipRepository membershipRepository,
                                         CreditUsageRepository creditUsageRepository) {
        this.membershipRepository = membershipRepository;
        this.creditUsageRepository = creditUsageRepository;
    }

    // ── Self ────────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> selfFreeze() {
        UUID userId = currentUserId();
        if (userId == null) return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Not authenticated");
        B2bMembership m = membershipRepository.findByUserId(userId).orElse(null);
        if (m == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "No membership for this account");
        return doFreeze(m, "self:" + userId);
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> selfUnfreeze() {
        UUID userId = currentUserId();
        if (userId == null) return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Not authenticated");
        B2bMembership m = membershipRepository.findByUserId(userId).orElse(null);
        if (m == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "No membership");
        return doUnfreeze(m, "self:" + userId);
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> selfDelete() {
        UUID userId = currentUserId();
        if (userId == null) return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Not authenticated");
        B2bMembership m = membershipRepository.findByUserId(userId).orElse(null);
        if (m == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "No membership");
        return doSoftDelete(m, "self:" + userId);
    }

    // ── Admin ──────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> adminFreeze(UUID id, String performedBy) {
        return membershipRepository.findById(id)
                .map(m -> doFreeze(m, performedBy))
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Membership not found"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> adminUnfreeze(UUID id, String performedBy) {
        return membershipRepository.findById(id)
                .map(m -> doUnfreeze(m, performedBy))
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Membership not found"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> adminDelete(UUID id, String performedBy) {
        return membershipRepository.findById(id)
                .map(m -> doSoftDelete(m, performedBy))
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Membership not found"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> adminRestore(UUID id, String performedBy) {
        B2bMembership m = membershipRepository.findById(id).orElse(null);
        if (m == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Membership not found");
        if (m.getStatus() != MembershipStatus.TRASHED) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Membership is not in trash");
        }
        m.setStatus(MembershipStatus.ACTIVE);
        m.setDeletedAt(null);
        m.setDeletedBy(null);
        m.setFrozenAt(null);
        m.setFrozenBy(null);
        membershipRepository.save(m);
        log.info("Membership {} restored by {}", m.getId(), performedBy);
        return ApiResponse.success("restored", "Membership restored");
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private ResponseEntity<ApiResponse<String>> doFreeze(B2bMembership m, String performedBy) {
        if (m.getStatus() == MembershipStatus.TRASHED) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Membership is in trash; restore first");
        }
        if (m.getStatus() == MembershipStatus.SUSPENDED) {
            return ApiResponse.success("already_frozen", "Already frozen");
        }
        m.setStatus(MembershipStatus.SUSPENDED);
        m.setFrozenAt(Instant.now());
        m.setFrozenBy(performedBy);
        membershipRepository.save(m);
        log.info("Membership {} frozen by {}", m.getId(), performedBy);
        return ApiResponse.success("frozen", "Membership frozen");
    }

    private ResponseEntity<ApiResponse<String>> doUnfreeze(B2bMembership m, String performedBy) {
        if (m.getStatus() != MembershipStatus.SUSPENDED) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Membership is not frozen");
        }
        m.setStatus(MembershipStatus.ACTIVE);
        m.setFrozenAt(null);
        m.setFrozenBy(null);
        membershipRepository.save(m);
        log.info("Membership {} unfrozen by {}", m.getId(), performedBy);
        return ApiResponse.success("unfrozen", "Membership unfrozen");
    }

    private ResponseEntity<ApiResponse<String>> doSoftDelete(B2bMembership m, String performedBy) {
        if (m.getStatus() == MembershipStatus.TRASHED) {
            return ApiResponse.success("already_trashed", "Already in trash");
        }
        // Block delete while there are unpaid credit usages — money still owed
        if (creditUsageRepository.existsByUserIdAndStatusIn(m.getUserId(), OUTSTANDING_STATUSES)) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "Cannot delete membership while credit is outstanding. Pay back used credit first.");
        }
        m.setStatus(MembershipStatus.TRASHED);
        m.setDeletedAt(Instant.now());
        m.setDeletedBy(performedBy);
        membershipRepository.save(m);
        log.info("Membership {} trashed by {}", m.getId(), performedBy);
        return ApiResponse.success("trashed", "Membership moved to trash");
    }

    // ── Purge ──────────────────────────────────────────────────────────────

    @Transactional
    public int purgeExpiredTrash() {
        Instant cutoff = Instant.now().minus(TRASH_RETENTION_DAYS, ChronoUnit.DAYS);
        List<B2bMembership> expired = membershipRepository.findByStatusAndDeletedAtBefore(
                MembershipStatus.TRASHED, cutoff);
        int purged = 0;
        for (B2bMembership m : expired) {
            if (creditUsageRepository.existsByUserIdAndStatusIn(m.getUserId(), OUTSTANDING_STATUSES)) {
                log.warn("Skipping purge for membership {} (outstanding credit)", m.getId());
                continue;
            }
            try {
                membershipRepository.delete(m);
                purged++;
                log.info("Hard-deleted membership {} after 30-day retention", m.getId());
            } catch (Exception e) {
                log.error("Failed to purge membership {}: {}", m.getId(), e.getMessage(), e);
            }
        }
        return purged;
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID id)) return null;
        return id;
    }
}
