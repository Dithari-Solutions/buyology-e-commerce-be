package com.buyology.ecommerce.supplier.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.role.domain.Role;
import com.buyology.ecommerce.role.domain.UserRole;
import com.buyology.ecommerce.role.domain.UserRoleId;
import com.buyology.ecommerce.role.repository.RoleRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.domain.SupplierApplication;
import com.buyology.ecommerce.supplier.domain.SupplierApplication.ApplicationStatus;
import com.buyology.ecommerce.supplier.domain.SupplierSetupToken;
import com.buyology.ecommerce.supplier.domain.SupplierStoreAssignment;
import com.buyology.ecommerce.supplier.dto.SupplierApplicationResponse;
import com.buyology.ecommerce.supplier.repository.SupplierApplicationRepository;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import com.buyology.ecommerce.supplier.repository.SupplierSetupTokenRepository;
import com.buyology.ecommerce.supplier.repository.SupplierStoreAssignmentRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AdminSupplierService {

    private static final Logger log = LoggerFactory.getLogger(AdminSupplierService.class);

    private final SupplierApplicationRepository applicationRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierSetupTokenRepository setupTokenRepository;
    private final SupplierStoreAssignmentRepository storeAssignmentRepository;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final EmailService emailService;
    private final ContaboObjectService contaboObjectService;

    @Value("${app.dashboard-base-url:http://localhost:5173}")
    private String dashboardBaseUrl;

    public AdminSupplierService(
            SupplierApplicationRepository applicationRepository,
            SupplierRepository supplierRepository,
            SupplierSetupTokenRepository setupTokenRepository,
            SupplierStoreAssignmentRepository storeAssignmentRepository,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            EmailService emailService,
            ContaboObjectService contaboObjectService) {
        this.applicationRepository = applicationRepository;
        this.supplierRepository = supplierRepository;
        this.setupTokenRepository = setupTokenRepository;
        this.storeAssignmentRepository = storeAssignmentRepository;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.emailService = emailService;
        this.contaboObjectService = contaboObjectService;
    }

    // ── List applications ────────────────────────────────────────────────────

    public Page<SupplierApplicationResponse> listApplications(ApplicationStatus status, Pageable pageable) {
        Page<SupplierApplication> page = (status != null)
                ? applicationRepository.findByStatus(status, pageable)
                : applicationRepository.findAll(pageable);
        return page.map(app -> {
            Supplier supplier = supplierRepository.findByApplicationId(app.getId()).orElse(null);
            return SupplierApplicationResponse.from(
                    app,
                    getDocumentUrl(app.getTradeLicenseKey()),
                    supplier != null ? supplier.getId() : null,
                    supplier != null ? hasLocalPassword(supplier.getUserId()) : null);
        });
    }

    public ResponseEntity<ApiResponse<SupplierApplicationResponse>> getApplication(UUID id) {
        SupplierApplication app = applicationRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
        if (app == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Application not found");
        }
        Supplier supplier = supplierRepository.findByApplicationId(app.getId()).orElse(null);
        return ApiResponse.success(
                SupplierApplicationResponse.from(
                        app,
                        getDocumentUrl(app.getTradeLicenseKey()),
                        supplier != null ? supplier.getId() : null,
                        supplier != null ? hasLocalPassword(supplier.getUserId()) : null),
                "Application found");
    }

    // ── Approve ──────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<UUID>> approveApplication(UUID applicationId, List<UUID> storeIds) {

        SupplierApplication app = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElse(null);
        if (app == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Application not found");
        }
        if (app.getStatus() != ApplicationStatus.PENDING) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Application already reviewed");
        }
        if (storeIds == null || storeIds.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "At least one store must be assigned");
        }

        // 1. Create Users record
        String[] nameParts = app.getFullName().trim().split("\\s+", 2);
        Users user = new Users();
        user.setFirstName(nameParts[0]);
        user.setLastName(nameParts.length > 1 ? nameParts[1] : "");
        user.setUserType(Users.UserType.ADMIN);
        user.setIsGuest(false);
        user.setStatus("ACTIVE");
        userRepository.save(user);

        // 2. Create AuthCredentials shell (no password yet — set via setup token)
        AuthCredentials credentials = new AuthCredentials();
        credentials.setUserId(user.getId());
        credentials.setEmail(app.getEmail());
        credentials.setProvider("LOCAL");
        credentials.setIsActive(true);
        credentials.setPhoneVerified(false);
        authCredentialRepository.save(credentials);

        // 3. Create Supplier record
        Supplier supplier = new Supplier();
        supplier.setApplicationId(app.getId());
        supplier.setUserId(user.getId());
        supplier.setBusinessName(app.getBusinessName() != null ? app.getBusinessName() : app.getFullName());
        supplier.setContactEmail(app.getEmail());
        supplier.setContactPhone(app.getPhoneNumber());
        supplier.setTradeLicenseKey(app.getTradeLicenseKey());
        supplierRepository.save(supplier);

        // 4. Assign SUPPLIER role
        Role supplierRole = roleRepository.findByName("SUPPLIER")
                .orElseThrow(() -> new IllegalStateException("SUPPLIER role not found"));
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), supplierRole.getId()));
        userRole.setUser(user);
        userRole.setRole(supplierRole);
        userRoleRepository.save(userRole);

        // 5. Assign stores
        for (UUID storeId : storeIds) {
            storeAssignmentRepository.save(new SupplierStoreAssignment(supplier.getId(), storeId));
        }

        // 6. Create setup token (24h TTL)
        SupplierSetupToken token = new SupplierSetupToken();
        token.setSupplierId(supplier.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        setupTokenRepository.save(token);

        // 7. Update application status
        app.setStatus(ApplicationStatus.APPROVED);
        app.setReviewedAt(Instant.now());
        applicationRepository.save(app);

        // 8. Send approval email
        String setupLink = dashboardBaseUrl + "/supplier/set-password?token=" + token.getToken();
        emailService.sendSupplierApprovedEmail(
                app.getEmail(),
                app.getFullName(),
                supplier.getBusinessName(),
                setupLink);

        return ApiResponse.created(supplier.getId(), "Supplier approved successfully");
    }

    // ── Reject ───────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> rejectApplication(UUID applicationId, String reason) {

        SupplierApplication app = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElse(null);
        if (app == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Application not found");
        }
        if (app.getStatus() != ApplicationStatus.PENDING) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Application already reviewed");
        }

        app.setStatus(ApplicationStatus.REJECTED);
        app.setRejectionReason(reason);
        app.setReviewedAt(Instant.now());
        applicationRepository.save(app);

        emailService.sendSupplierRejectedEmail(
                app.getEmail(),
                app.getFullName(),
                app.getBusinessName() != null ? app.getBusinessName() : app.getFullName(),
                reason);

        return ApiResponse.success("rejected", "Application rejected");
    }

    // ── Resend setup email ──────────────────────────────────────────────────

    /**
     * Re-issue the password-setup email for an approved supplier who hasn't
     * completed setup yet. Invalidates any prior unused tokens, mints a fresh
     * one, and re-sends the approval email. Refuses if the supplier already
     * has a LOCAL password.
     */
    @Transactional
    public ResponseEntity<ApiResponse<String>> resendSetupEmail(UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId).orElse(null);
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier not found");
        }
        if (hasLocalPassword(supplier.getUserId())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Supplier has already set a password");
        }

        String email = supplier.getContactEmail();
        if (email == null || email.isBlank()) {
            email = authCredentialRepository.findByUserId(supplier.getUserId()).stream()
                    .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                    .map(AuthCredentials::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        if (email == null || email.isBlank()) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "No contact email on file for this supplier");
        }

        // Invalidate any prior unused tokens.
        for (SupplierSetupToken old : setupTokenRepository.findBySupplierIdAndUsedFalse(supplier.getId())) {
            old.setUsed(true);
            setupTokenRepository.save(old);
        }

        SupplierSetupToken token = new SupplierSetupToken();
        token.setSupplierId(supplier.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        setupTokenRepository.save(token);

        String setupLink = dashboardBaseUrl + "/supplier/set-password?token=" + token.getToken();
        try {
            String memberName = supplier.getBusinessName();
            emailService.sendSupplierApprovedEmail(
                    email,
                    memberName,
                    supplier.getBusinessName(),
                    setupLink);
        } catch (Exception e) {
            log.warn("Resend supplier setup email failed for {}: {}", supplierId, e.getMessage());
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to send email: " + e.getMessage());
        }

        return ApiResponse.success("sent", "Set-password email re-sent");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean hasLocalPassword(UUID userId) {
        if (userId == null) return false;
        return authCredentialRepository.findByUserId(userId).stream()
                .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                .anyMatch(c -> c.getPasswordHash() != null && !c.getPasswordHash().isBlank());
    }

    private String getDocumentUrl(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            return contaboObjectService.getPresignedUrl(key);
        } catch (Exception e) {
            log.warn("Could not generate presigned URL for key {}: {}", key, e.getMessage());
            return null;
        }
    }
}
