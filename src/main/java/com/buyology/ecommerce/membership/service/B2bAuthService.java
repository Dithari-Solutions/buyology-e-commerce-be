package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.PasswordUtils;
import com.buyology.ecommerce.membership.domain.B2bMembership;
import com.buyology.ecommerce.membership.domain.B2bMembershipApplication;
import com.buyology.ecommerce.membership.domain.B2bSetupToken;
import com.buyology.ecommerce.membership.dto.B2bSetPasswordRequest;
import com.buyology.ecommerce.membership.repository.B2bMembershipApplicationRepository;
import com.buyology.ecommerce.membership.repository.B2bMembershipRepository;
import com.buyology.ecommerce.membership.repository.B2bSetupTokenRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class B2bAuthService {

    private final B2bSetupTokenRepository setupTokenRepository;
    private final B2bMembershipRepository membershipRepository;
    private final B2bMembershipApplicationRepository applicationRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    public B2bAuthService(B2bSetupTokenRepository setupTokenRepository,
                          B2bMembershipRepository membershipRepository,
                          B2bMembershipApplicationRepository applicationRepository,
                          AuthCredentialRepository authCredentialRepository,
                          UserRepository userRepository,
                          AuthService authService) {
        this.setupTokenRepository = setupTokenRepository;
        this.membershipRepository = membershipRepository;
        this.applicationRepository = applicationRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    /**
     * Returns the LOCAL credential for this membership's user, healing any
     * inconsistencies along the way:
     *   1. If membership.userId points to a deleted/missing Users row, recreate
     *      a Users row (so JWT-authenticated requests can resolve a profile).
     *   2. If the credential exists but its user_id points at a missing Users
     *      row, repair that link.
     *   3. If no LOCAL credential exists, create one.
     */
    private AuthCredentials resolveOrCreateLocalCredentials(B2bMembership membership) {
        B2bMembershipApplication app = applicationRepository.findById(membership.getApplicationId())
                .orElse(null);
        String email = app != null ? app.getContactEmail() : null;
        String fullName = app != null ? app.getContactFullName() : null;

        // Step 1: ensure Users row for membership.userId exists.
        UUID userId = membership.getUserId();
        Users user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        if (user == null) {
            user = new Users();
            String[] nameParts = fullName == null
                    ? new String[]{"B2B", "Member"}
                    : fullName.trim().split("\\s+", 2);
            user.setFirstName(nameParts[0]);
            user.setLastName(nameParts.length > 1 ? nameParts[1] : "");
            user.setUserType(Users.UserType.CUSTOMER);
            user.setIsGuest(false);
            user.setStatus("ACTIVE");
            userRepository.save(user);
            // Update the membership to point at the new user
            membership.setUserId(user.getId());
            membershipRepository.save(membership);
            userId = user.getId();
        }

        // Step 2: find LOCAL credential — by user first, then by email as a fallback.
        final UUID finalUserId = userId;
        AuthCredentials local = authCredentialRepository.findByUserId(finalUserId).stream()
                .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                .findFirst()
                .orElse(null);
        if (local == null && email != null && !email.isBlank()) {
            local = authCredentialRepository.findByEmailAndProvider(email, "LOCAL").orElse(null);
            if (local != null && !finalUserId.equals(local.getUserId())) {
                // Found by email but linked to a different (probably orphan) userId — repair.
                local.setUserId(finalUserId);
                authCredentialRepository.save(local);
            }
        }

        // Step 3: still nothing — create.
        if (local == null) {
            if (email == null || email.isBlank()) return null;
            local = new AuthCredentials();
            local.setUserId(finalUserId);
            local.setEmail(email);
            local.setProvider("LOCAL");
            local.setIsActive(true);
            local.setPhoneVerified(false);
            local = authCredentialRepository.save(local);
        } else if (!userRepository.existsById(local.getUserId())) {
            // Credential exists but its user_id points nowhere — repair.
            local.setUserId(finalUserId);
            authCredentialRepository.save(local);
        }
        return local;
    }

    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(String token) {
        return setupTokenRepository.findByTokenAndUsedFalse(token)
                .map(t -> {
                    if (t.isExpired()) {
                        return ApiResponse.<Map<String, Object>>failure(HttpStatus.GONE, "Setup link has expired");
                    }
                    B2bMembership m = membershipRepository.findById(t.getMembershipId()).orElse(null);
                    if (m == null) {
                        return ApiResponse.<Map<String, Object>>failure(HttpStatus.NOT_FOUND, "Membership not found");
                    }
                    AuthCredentials creds = resolveOrCreateLocalCredentials(m);
                    String email = creds != null
                            ? creds.getEmail()
                            : applicationRepository.findById(m.getApplicationId())
                                    .map(B2bMembershipApplication::getContactEmail).orElse(null);
                    Map<String, Object> data = Map.of(
                            "valid", true,
                            "memberEmail", email == null ? "" : email,
                            "companyName", m.getCompanyName(),
                            "memberName", m.getMemberName());
                    return ApiResponse.success(data, "Token valid");
                })
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Invalid or already used setup link"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<SignInResponse>> setPassword(
            B2bSetPasswordRequest request, HttpServletRequest httpRequest) {

        if (!request.getPassword().equals(request.getConfirmedPassword())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }
        boolean hasUppercase = request.getPassword().chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = request.getPassword().chars().anyMatch(Character::isDigit);
        if (!hasUppercase || !hasDigit) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "Password must contain at least one uppercase letter and one number");
        }

        B2bSetupToken setupToken = setupTokenRepository.findByTokenAndUsedFalse(request.getToken()).orElse(null);
        if (setupToken == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Invalid or already used setup link");
        }
        if (setupToken.isExpired()) {
            return ApiResponse.failure(HttpStatus.GONE, "Setup link has expired. Please contact support.");
        }

        B2bMembership membership = membershipRepository.findById(setupToken.getMembershipId()).orElse(null);
        if (membership == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Membership not found");
        }

        AuthCredentials credentials = resolveOrCreateLocalCredentials(membership);
        if (credentials == null) {
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Account credentials not found. Please contact support.");
        }

        credentials.setPasswordHash(PasswordUtils.hashPassword(request.getPassword()));
        authCredentialRepository.save(credentials);

        setupToken.setUsed(true);
        setupTokenRepository.save(setupToken);

        return authService.buildSigninResponse(credentials, httpRequest);
    }
}
