package com.buyology.ecommerce.admin.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.repository.RefreshTokenRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AdminInactivityService {

    private static final Logger log = LoggerFactory.getLogger(AdminInactivityService.class);

    @Value("${inactivity.block-after-days:90}")
    private int blockAfterDays;

    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AdminInactivityService(UserRepository userRepository,
                                  AuthCredentialRepository authCredentialRepository,
                                  RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ── Scheduled daily auto-block ────────────────────────────────────────────

    @Scheduled(cron = "0 0 1 * * *") // runs every day at 01:00
    @Transactional
    public void autoBlockInactiveUsers() {
        int blocked = blockInactiveUsers();
        if (blocked > 0) {
            log.info("Auto-block: suspended {} user(s) inactive for more than {} days", blocked, blockAfterDays);
        }
    }

    // ── Manual trigger ────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> triggerInactivityBlock() {
        int blocked = blockInactiveUsers();
        String msg = blocked == 0
                ? "No inactive users found to block"
                : "Blocked " + blocked + " user(s) inactive for more than " + blockAfterDays + " days";
        return ApiResponse.success(null, msg);
    }

    // ── Manual block / unblock individual user ────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> blockUser(UUID userId) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }
        if ("SUSPENDED".equals(user.getStatus())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "User is already blocked");
        }
        suspendUser(user);
        return ApiResponse.success(null, "User blocked successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<String>> unblockUser(UUID userId) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }
        if (!"SUSPENDED".equals(user.getStatus())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "User is not currently blocked");
        }
        user.setStatus("ACTIVE");
        userRepository.save(user);
        return ApiResponse.success(null, "User unblocked successfully");
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private int blockInactiveUsers() {
        Instant cutoff = Instant.now().minus(blockAfterDays, ChronoUnit.DAYS);
        List<Users> inactiveUsers = userRepository.findInactiveCustomersToBlock(cutoff);
        for (Users user : inactiveUsers) {
            suspendUser(user);
        }
        return inactiveUsers.size();
    }

    private void suspendUser(Users user) {
        user.setStatus("SUSPENDED");
        userRepository.save(user);

        Instant now = Instant.now();
        List<AuthCredentials> credentials = authCredentialRepository.findByUserId(user.getId());
        for (AuthCredentials cred : credentials) {
            int revoked = refreshTokenRepository.revokeAllActiveByAuthCredential(cred, now);
            if (revoked > 0) {
                log.debug("Revoked {} token(s) for suspended user {}", revoked, user.getId());
            }
        }
    }
}
