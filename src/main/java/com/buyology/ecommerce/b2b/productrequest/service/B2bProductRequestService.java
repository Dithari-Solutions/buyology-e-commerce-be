package com.buyology.ecommerce.b2b.productrequest.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.b2b.productrequest.domain.B2bProductRequest;
import com.buyology.ecommerce.b2b.productrequest.domain.B2bProductRequestStatus;
import com.buyology.ecommerce.b2b.productrequest.dto.B2bProductRequestResponse;
import com.buyology.ecommerce.b2b.productrequest.repository.B2bProductRequestRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.membership.domain.B2bMembership;
import com.buyology.ecommerce.membership.repository.B2bMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * B2B product-sourcing request service.
 *
 * A logged-in ACTIVE {@link B2bMembership} member asks Buyology to source a free-form product
 * (name + quantity >= 5, optional image, message to procurement). Ownership is keyed on the
 * caller's auth_credentials.id (sub), mirroring {@link com.buyology.ecommerce.b2b.quote.service.B2bQuoteService}.
 * Procurement (SUPERADMIN / PROCUREMENT) triages the request through a NEW → UNDER_REVIEW →
 * IN_PROGRESS → COMPLETED lifecycle (plus REJECTED) and can send the member a custom update;
 * the member is emailed on submit, each transition, and each custom update.
 */
@Service
public class B2bProductRequestService {

    private static final Logger log = LoggerFactory.getLogger(B2bProductRequestService.class);

    /** Rule: every product request must be for at least this quantity. */
    public static final int MIN_QUANTITY = 5;

    private final B2bProductRequestRepository requestRepo;
    private final B2bMembershipRepository membershipRepo;
    private final AuthCredentialRepository authCredentialRepository;
    private final ContaboObjectService contaboObjectService;
    private final EmailService emailService;

    @Value("${app.admin-email:firdovsirz@gmail.com}")
    private String procurementEmail;

    @Value("${app.web-base-url:https://buyology.online}")
    private String webBaseUrl;

    public B2bProductRequestService(B2bProductRequestRepository requestRepo,
                                    B2bMembershipRepository membershipRepo,
                                    AuthCredentialRepository authCredentialRepository,
                                    ContaboObjectService contaboObjectService,
                                    EmailService emailService) {
        this.requestRepo = requestRepo;
        this.membershipRepo = membershipRepo;
        this.authCredentialRepository = authCredentialRepository;
        this.contaboObjectService = contaboObjectService;
        this.emailService = emailService;
    }

    // =========================================================================
    // Member
    // =========================================================================

    /**
     * Create a new product request. The caller must hold an ACTIVE B2B membership; quantity
     * must be >= {@link #MIN_QUANTITY}. An optional image is uploaded to Contabo under
     * {@code b2b-requests/{uuid}/{filename}} (only the key is persisted). The member is emailed
     * a confirmation and procurement is notified — both best-effort.
     */
    @Transactional
    public B2bProductRequestResponse create(UUID userId, String productName, Integer quantity,
                                            String message, MultipartFile image) {
        B2bMembership membership = requireActiveMembership(userId);
        UUID credentialId = resolveCredentialId(userId);
        UUID resolvedUserId = resolveUsersId(userId);

        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        int qty = quantity == null ? 0 : quantity;
        if (qty < MIN_QUANTITY) {
            throw new IllegalArgumentException("Minimum quantity for a product request is " + MIN_QUANTITY + ".");
        }

        String imageKey = null;
        if (image != null && !image.isEmpty()) {
            String filename = image.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                filename = "image";
            }
            String key = "b2b-requests/" + UUID.randomUUID() + "/" + filename;
            imageKey = contaboObjectService.uploadFile(key, image);
        }

        String memberEmail = resolveMemberEmail(resolvedUserId, credentialId);

        B2bProductRequest request = new B2bProductRequest();
        request.setCredentialId(credentialId);
        request.setUserId(resolvedUserId);
        request.setMembershipId(membership.getId());
        request.setProductName(productName.trim());
        request.setQuantity(qty);
        request.setMessage(message == null || message.isBlank() ? null : message.trim());
        request.setImageKey(imageKey);
        request.setStatus(B2bProductRequestStatus.NEW);
        request.setContactEmail(memberEmail);
        request.setSubmittedAt(Instant.now());
        request = requestRepo.save(request);

        String memberName = resolveMemberName(resolvedUserId);

        // Member confirmation (best-effort).
        try {
            if (memberEmail != null && !memberEmail.isBlank()) {
                emailService.sendB2bProductRequestReceivedEmail(
                        memberEmail, memberName, request.getId().toString(),
                        request.getProductName(), request.getQuantity());
            }
        } catch (Exception e) {
            log.warn("[B2B-REQUEST] received email failed for request {}: {}", request.getId(), e.getMessage());
        }

        // Procurement notification (best-effort).
        try {
            emailService.sendB2bProductRequestNotificationEmail(
                    procurementEmail,
                    memberName != null ? memberName : membership.getCompanyName(),
                    request.getId().toString(),
                    request.getProductName(),
                    request.getQuantity(),
                    request.getMessage(),
                    webBaseUrl + "/procurement/requests/" + request.getId());
        } catch (Exception e) {
            log.warn("[B2B-REQUEST] procurement notification failed for request {}: {}", request.getId(), e.getMessage());
        }

        return toResponse(request);
    }

    /** A member's own requests, newest first. */
    public List<B2bProductRequestResponse> listOwn(UUID userId) {
        UUID credentialId = resolveCredentialId(userId);
        return requestRepo.findByCredentialIdOrderByCreatedAtDesc(credentialId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Procurement
    // =========================================================================

    /** Procurement queue — all requests (optionally filtered by status), newest first. */
    public List<B2bProductRequestResponse> listAll(B2bProductRequestStatus status) {
        List<B2bProductRequest> rows = (status != null)
                ? requestRepo.findByStatusOrderByCreatedAtDesc(status)
                : requestRepo.findAllByOrderByCreatedAtDesc();
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public B2bProductRequestResponse getById(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    /**
     * Advance a request's status (and persist the admin note) then email the member with a
     * stage label + note.
     */
    @Transactional
    public B2bProductRequestResponse updateStatus(UUID id, B2bProductRequestStatus status,
                                                  String note, UUID adminUserId) {
        if (status == null) {
            throw new IllegalArgumentException("A status is required.");
        }
        B2bProductRequest request = loadOrThrow(id);
        request.setStatus(status);
        if (note != null && !note.isBlank()) {
            request.setProcurementNote(note);
        }
        request.setUpdatedBy(adminUserId);
        request = requestRepo.save(request);

        try {
            String memberEmail = request.getContactEmail() != null
                    ? request.getContactEmail()
                    : resolveMemberEmail(request.getUserId(), request.getCredentialId());
            if (memberEmail != null && !memberEmail.isBlank()) {
                emailService.sendB2bProductRequestStatusEmail(
                        memberEmail, resolveMemberName(request.getUserId()),
                        request.getId().toString(), request.getProductName(),
                        stageLabel(status), note);
            }
        } catch (Exception e) {
            log.warn("[B2B-REQUEST] status email failed for request {}: {}", request.getId(), e.getMessage());
        }

        return toResponse(request);
    }

    /** Send a free-form update message to the member (persisted as the note, no status change). */
    @Transactional
    public B2bProductRequestResponse sendUpdate(UUID id, String message, UUID adminUserId) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A message is required.");
        }
        B2bProductRequest request = loadOrThrow(id);
        request.setProcurementNote(message);
        request.setUpdatedBy(adminUserId);
        request = requestRepo.save(request);

        try {
            String memberEmail = request.getContactEmail() != null
                    ? request.getContactEmail()
                    : resolveMemberEmail(request.getUserId(), request.getCredentialId());
            if (memberEmail != null && !memberEmail.isBlank()) {
                emailService.sendB2bProductRequestStatusEmail(
                        memberEmail, resolveMemberName(request.getUserId()),
                        request.getId().toString(), request.getProductName(),
                        "Update", message);
            }
        } catch (Exception e) {
            log.warn("[B2B-REQUEST] update email failed for request {}: {}", request.getId(), e.getMessage());
        }

        return toResponse(request);
    }

    /** Count of NEW requests — drives the procurement badge. */
    public long countNew() {
        return requestRepo.countByStatus(B2bProductRequestStatus.NEW);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private B2bProductRequest loadOrThrow(UUID id) {
        return requestRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Product request not found: " + id));
    }

    private B2bProductRequestResponse toResponse(B2bProductRequest request) {
        String imageUrl = request.getImageKey() != null
                ? contaboObjectService.getPresignedUrl(request.getImageKey())
                : null;
        return B2bProductRequestResponse.from(request, imageUrl, resolveMemberName(request.getUserId()));
    }

    /** Human-readable stage label for the member email. */
    private static String stageLabel(B2bProductRequestStatus status) {
        return switch (status) {
            case NEW -> "Received";
            case UNDER_REVIEW -> "Under review";
            case IN_PROGRESS -> "In progress";
            case COMPLETED -> "Completed";
            case REJECTED -> "Rejected";
        };
    }

    private B2bMembership requireActiveMembership(UUID userId) {
        UUID resolvedUserId = resolveUsersId(userId);
        B2bMembership membership = membershipRepo.findByUserId(resolvedUserId)
                .orElseThrow(() -> new AccessDeniedException("An active B2B membership is required for this action"));
        if (membership.getStatus() != B2bMembership.MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("Your B2B membership is not active (status: " + membership.getStatus() + ")");
        }
        return membership;
    }

    /**
     * Resolve the caller's auth_credentials.id (sub) from the users.id principal — the request
     * owner is keyed on the credential (sub). Mirrors B2bQuoteService: maps users.id → its
     * LOCAL/first credential, falling back to the principal itself if it already is a credential.
     */
    private UUID resolveCredentialId(UUID userId) {
        List<AuthCredentials> creds = authCredentialRepository.findByUserId(userId);
        if (!creds.isEmpty()) {
            return creds.stream()
                    .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                    .map(AuthCredentials::getId)
                    .findFirst()
                    .orElse(creds.get(0).getId());
        }
        return authCredentialRepository.findById(userId).map(AuthCredentials::getId).orElse(userId);
    }

    /** users.id from a principal that may already be users.id, or an auth_credentials.id. */
    private UUID resolveUsersId(UUID candidate) {
        if (candidate == null) return null;
        if (membershipRepo.existsByUserId(candidate)) return candidate;
        return authCredentialRepository.findById(candidate)
                .map(AuthCredentials::getUserId)
                .orElse(candidate);
    }

    private String resolveMemberEmail(UUID userId, UUID credentialId) {
        if (credentialId != null) {
            String email = authCredentialRepository.findById(credentialId)
                    .map(AuthCredentials::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .orElse(null);
            if (email != null) return email;
        }
        if (userId != null) {
            return authCredentialRepository.findByUserId(userId).stream()
                    .map(AuthCredentials::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String resolveMemberName(UUID userId) {
        if (userId == null) return null;
        return membershipRepo.findByUserId(userId)
                .map(B2bMembership::getMemberName)
                .orElse(null);
    }
}
