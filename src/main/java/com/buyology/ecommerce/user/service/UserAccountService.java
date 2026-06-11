package com.buyology.ecommerce.user.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Customer self-service account deletion with a 30-day grace period (mirrors the B2B
 * membership / supplier soft-delete pattern). Requesting deletion flags the account
 * PENDING_DELETION; the user can still sign in to recover it until the purge job
 * finalizes the deletion after the grace window. Finalization anonymizes PII and
 * disables auth, keeping the row so order/transaction records survive for the legally
 * required retention period.
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);
    public static final int GRACE_DAYS = 30;
    private static final String PENDING = "PENDING_DELETION";

    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final EmailService emailService;

    public UserAccountService(UserRepository userRepository,
                              AuthCredentialRepository authCredentialRepository,
                              EmailService emailService) {
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.emailService = emailService;
    }

    @Transactional
    public void requestDeletion(UUID userId) {
        SecurityUtils.requireSelfOrAdmin(userId);
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        if (PENDING.equals(user.getStatus())) return; // already pending — idempotent
        user.setStatus(PENDING);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        String email = emailOf(userId);
        if (email != null) {
            try { emailService.sendAccountDeletionRequestedEmail(email, user.getFirstName()); }
            catch (Exception e) { log.warn("deletion-requested email failed for {}: {}", userId, e.getMessage()); }
        }
        log.info("[ACCOUNT] Deletion requested for user {} — purge after {} days", userId, GRACE_DAYS);
    }

    @Transactional
    public void recover(UUID userId) {
        SecurityUtils.requireSelfOrAdmin(userId);
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        if (!PENDING.equals(user.getStatus())) return;
        user.setStatus("ACTIVE");
        user.setDeletedAt(null);
        userRepository.save(user);
        String email = emailOf(userId);
        if (email != null) {
            try { emailService.sendAccountRecoveryEmail(email, user.getFirstName()); }
            catch (Exception e) { log.warn("recovery email failed for {}: {}", userId, e.getMessage()); }
        }
        log.info("[ACCOUNT] Deletion cancelled (recovered) for user {}", userId);
    }

    /** Called by the scheduled purge job. Finalizes any deletion past the grace window. */
    @Transactional
    public int purgeExpired() {
        Instant cutoff = Instant.now().minus(GRACE_DAYS, ChronoUnit.DAYS);
        List<Users> expired = userRepository.findByStatusAndDeletedAtBefore(PENDING, cutoff);
        for (Users user : expired) {
            String email = emailOf(user.getId());   // capture BEFORE scrambling
            String name = user.getFirstName();

            List<AuthCredentials> creds = authCredentialRepository.findByUserId(user.getId());
            for (AuthCredentials c : creds) {
                c.setIsActive(false);
                c.setEmail("deleted+" + user.getId() + "@deleted.buyology.local");
            }
            authCredentialRepository.saveAll(creds);

            user.setStatus("DELETED");
            userRepository.save(user);

            if (email != null) {
                try { emailService.sendAccountDeletedEmail(email, name); }
                catch (Exception e) { log.warn("account-deleted email failed for {}: {}", user.getId(), e.getMessage()); }
            }
            log.info("[ACCOUNT] Permanently deleted (anonymized) user {}", user.getId());
        }
        return expired.size();
    }

    private String emailOf(UUID userId) {
        return authCredentialRepository.findByUserId(userId).stream()
                .map(AuthCredentials::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst()
                .orElse(null);
    }
}
