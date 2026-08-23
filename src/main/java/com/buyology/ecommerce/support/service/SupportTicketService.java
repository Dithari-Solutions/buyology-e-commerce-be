package com.buyology.ecommerce.support.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.support.domain.SupportCategory;
import com.buyology.ecommerce.support.domain.SupportMessageAuthor;
import com.buyology.ecommerce.support.domain.SupportTicket;
import com.buyology.ecommerce.support.domain.SupportTicketMessage;
import com.buyology.ecommerce.support.domain.SupportTicketStatus;
import com.buyology.ecommerce.support.dto.SupportMessageResponse;
import com.buyology.ecommerce.support.dto.SupportTicketResponse;
import com.buyology.ecommerce.support.repository.SupportTicketMessageRepository;
import com.buyology.ecommerce.support.repository.SupportTicketRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Customer support tickets: software bug reports, "I'm stuck" requests and general help asks.
 *
 * Any logged-in customer opens a ticket; ownership is keyed on the caller's auth_credentials.id
 * (sub) with users.id (uid) denormalized, exactly like {@code RepairService}. The support team
 * (SUPERADMIN / CUSTOMER_SUPPORT via {@code support:*} permissions) triages it through
 * OPEN → IN_PROGRESS → WAITING_FOR_CUSTOMER → RESOLVED/CLOSED, replying in a message thread.
 *
 * Every action notifies the other side: the customer gets an email AND an in-app notification
 * row (type SUPPORT_UPDATE — the storefront bell) on each team action; the team gets an email
 * and the superadmin bell fan-out (type SUPPORT_REQUEST) on ticket creation, plus the
 * {@code adminUnread} dashboard badge on every customer action. All side effects are best-effort.
 */
@Service
public class SupportTicketService {

    private static final Logger log = LoggerFactory.getLogger(SupportTicketService.class);

    private static final int MAX_IMAGES = 4;
    private static final int MAX_SUBJECT = 150;
    private static final int MAX_DESCRIPTION = 4000;
    private static final int MAX_PAGE_URL = 500;

    private final SupportTicketRepository ticketRepo;
    private final SupportTicketMessageRepository messageRepo;
    private final AuthCredentialRepository authCredentialRepository;
    private final UserRepository userRepository;
    private final ContaboObjectService contaboObjectService;
    private final EmailService emailService;
    private final PushNotificationService pushService;
    private final UserRoleRepository userRoleRepository;

    @Value("${app.admin-email:firdovsirz@gmail.com}")
    private String supportTeamEmail;

    @Value("${app.dashboard-base-url:https://admin.buyology.online}")
    private String dashboardBaseUrl;

    public SupportTicketService(SupportTicketRepository ticketRepo,
                                SupportTicketMessageRepository messageRepo,
                                AuthCredentialRepository authCredentialRepository,
                                UserRepository userRepository,
                                ContaboObjectService contaboObjectService,
                                EmailService emailService,
                                PushNotificationService pushService,
                                UserRoleRepository userRoleRepository) {
        this.ticketRepo = ticketRepo;
        this.messageRepo = messageRepo;
        this.authCredentialRepository = authCredentialRepository;
        this.userRepository = userRepository;
        this.contaboObjectService = contaboObjectService;
        this.emailService = emailService;
        this.pushService = pushService;
        this.userRoleRepository = userRoleRepository;
    }

    // =========================================================================
    // Customer
    // =========================================================================

    /**
     * Open a ticket. Category, subject and description are required; the page URL and up to
     * {@link #MAX_IMAGES} screenshots are optional — a stuck customer shouldn't be forced to
     * produce one. Contact email is snapshotted from the profile; the customer gets a
     * confirmation email + bell row, the team an email + superadmin bell fan-out.
     */
    @Transactional
    public SupportTicketResponse create(UUID userId, String category, String subject,
                                        String description, String pageUrl, List<MultipartFile> images) {
        UUID credentialId = resolveCredentialId(userId);
        UUID resolvedUserId = resolveUsersId(userId);

        requireText(subject, "Subject");
        requireText(description, "Description");
        if (subject.trim().length() > MAX_SUBJECT) {
            throw new IllegalArgumentException("Subject is limited to " + MAX_SUBJECT + " characters.");
        }
        if (description.trim().length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("Description is limited to " + MAX_DESCRIPTION + " characters.");
        }

        String email = resolveContactEmail(resolvedUserId, credentialId);
        String name = resolveCustomerName(resolvedUserId);

        SupportTicket ticket = new SupportTicket();
        ticket.setCredentialId(credentialId);
        ticket.setUserId(resolvedUserId);
        ticket.setCategory(parseCategory(category));
        ticket.setSubject(subject.trim());
        ticket.setDescription(description.trim());
        ticket.setPageUrl(trimTo(pageUrl, MAX_PAGE_URL));
        ticket.setImageKeys(uploadImages(images));
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setContactEmail(email);
        ticket.setAdminUnread(true);
        ticket.setCustomerUnread(false);
        ticket = ticketRepo.save(ticket);
        ticket.setReference(buildReference());
        ticket = ticketRepo.save(ticket);

        final SupportTicket saved = ticket;
        best("received email", () -> {
            if (email != null && !email.isBlank()) {
                emailService.sendSupportReceivedEmail(email, name, saved.getReference(), saved.getSubject());
            }
        });
        best("team notification", () -> emailService.sendSupportTeamEmail(
                supportTeamEmail, "New support ticket", name, saved.getReference(), saved.getSubject(),
                saved.getCategory() == null ? null : saved.getCategory().name(),
                saved.getDescription(), dashboardBaseUrl + "/support/" + saved.getId()));
        notifyCustomer(saved, "We received your ticket",
                "Ticket " + saved.getReference() + " (" + saved.getSubject() + ") is open — our team will get back to you.");
        // The dashboard bell must not miss this — every superadmin gets a feed row.
        try {
            Map<String, String> data = Map.of("id", saved.getId().toString(), "type", "SUPPORT_REQUEST");
            userRoleRepository.findUserIdsByRoleName("SUPERADMIN").forEach(uid ->
                    pushService.sendToUser(uid, "New support ticket",
                            "Ticket " + saved.getReference() + ": " + saved.getSubject(), "SUPPORT_REQUEST", data));
        } catch (Exception e) {
            log.warn("[NOTIFY] superadmin fan-out failed: {}", e.getMessage());
        }

        return toResponse(ticket, false);
    }

    /** A customer's own tickets, newest first (no message threads — lists stay light). */
    @Transactional(readOnly = true)
    public List<SupportTicketResponse> listOwn(UUID userId) {
        UUID credentialId = resolveCredentialId(userId);
        return ticketRepo.findByCredentialIdOrderByCreatedAtDesc(credentialId).stream()
                .map(t -> toResponse(t, false))
                .collect(Collectors.toList());
    }

    /** One of the customer's own tickets, with the full thread. Opening it clears the unread pill. */
    @Transactional
    public SupportTicketResponse getOwn(UUID userId, UUID id) {
        SupportTicket ticket = loadOrThrow(id);
        requireOwner(ticket, userId);
        if (ticket.isCustomerUnread()) {
            ticket.setCustomerUnread(false);
            ticket = ticketRepo.save(ticket);
        }
        return toResponse(ticket, true);
    }

    /**
     * Customer reply on their own ticket. Not allowed on a CLOSED ticket; replying to a RESOLVED
     * or WAITING_FOR_CUSTOMER ticket pulls it back to IN_PROGRESS so the team sees it again.
     */
    @Transactional
    public SupportTicketResponse addCustomerMessage(UUID userId, UUID id, String body) {
        SupportTicket ticket = loadOrThrow(id);
        requireOwner(ticket, userId);
        requireText(body, "Message");
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw new IllegalStateException("This ticket is closed. Please open a new one if you still need help.");
        }
        if (ticket.getStatus() == SupportTicketStatus.RESOLVED
                || ticket.getStatus() == SupportTicketStatus.WAITING_FOR_CUSTOMER) {
            ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
            ticket.setResolvedAt(null);
        }
        ticket.setAdminUnread(true);
        ticket = ticketRepo.save(ticket);

        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicketId(ticket.getId());
        message.setAuthor(SupportMessageAuthor.CUSTOMER);
        message.setAuthorUserId(ticket.getUserId());
        message.setBody(body.trim());
        messageRepo.save(message);

        final SupportTicket saved = ticket;
        final String reply = body.trim();
        best("team reply notification", () -> emailService.sendSupportTeamEmail(
                supportTeamEmail, "New customer reply", resolveCustomerName(saved.getUserId()),
                saved.getReference(), saved.getSubject(),
                saved.getCategory() == null ? null : saved.getCategory().name(),
                reply, dashboardBaseUrl + "/support/" + saved.getId()));

        return toResponse(ticket, true);
    }

    // =========================================================================
    // Team (dashboard)
    // =========================================================================

    /** All tickets, newest first, optionally filtered by status. Paged for the dashboard queue. */
    @Transactional(readOnly = true)
    public Page<SupportTicketResponse> listAll(SupportTicketStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<SupportTicket> rows = (status != null)
                ? ticketRepo.findByStatusOrderByCreatedAtDesc(status, pageable)
                : ticketRepo.findAllByOrderByCreatedAtDesc(pageable);
        return rows.map(t -> toResponse(t, false));
    }

    /** Detail for the dashboard, with the full thread. Opening it clears the queue badge flag. */
    @Transactional
    public SupportTicketResponse getByIdAdmin(UUID id) {
        SupportTicket ticket = loadOrThrow(id);
        if (ticket.isAdminUnread()) {
            ticket.setAdminUnread(false);
            ticket = ticketRepo.save(ticket);
        }
        return toResponse(ticket, true);
    }

    /** Unresolved-attention count for the dashboard badge. */
    @Transactional(readOnly = true)
    public long countUnread() {
        return ticketRepo.countByAdminUnreadTrue();
    }

    /**
     * Generic team status transition (e.g. mark IN_PROGRESS / RESOLVED / CLOSED) with an optional
     * note. The note is also appended to the thread so the conversation stays complete, and the
     * customer is emailed + gets a bell row.
     */
    @Transactional
    public SupportTicketResponse updateStatus(UUID id, SupportTicketStatus status, String note, UUID adminUserId) {
        if (status == null) {
            throw new IllegalArgumentException("A status is required.");
        }
        SupportTicket ticket = loadOrThrow(id);
        ticket.setStatus(status);
        if (note != null && !note.isBlank()) ticket.setAdminNote(note.trim());
        ticket.setResolvedAt(status == SupportTicketStatus.RESOLVED || status == SupportTicketStatus.CLOSED
                ? Instant.now() : null);
        ticket.setUpdatedBy(adminUserId);
        ticket.setAdminUnread(false);
        ticket.setCustomerUnread(true);
        ticket = ticketRepo.save(ticket);

        if (note != null && !note.isBlank()) {
            SupportTicketMessage message = new SupportTicketMessage();
            message.setTicketId(ticket.getId());
            message.setAuthor(SupportMessageAuthor.ADMIN);
            message.setAuthorUserId(adminUserId);
            message.setBody(note.trim());
            messageRepo.save(message);
        }

        final SupportTicket saved = ticket;
        final String finalNote = note;
        best("status email", () -> {
            String email = contactEmailFor(saved);
            if (email != null && !email.isBlank()) {
                emailService.sendSupportStatusEmail(email, resolveCustomerName(saved.getUserId()),
                        saved.getReference(), saved.getSubject(), stageLabel(saved.getStatus()), finalNote);
            }
        });
        notifyCustomer(saved, "Support ticket update",
                "Ticket " + saved.getReference() + " is now: " + stageLabel(saved.getStatus()) + ".");
        return toResponse(ticket, true);
    }

    /**
     * Team reply into the thread. An OPEN ticket moves to IN_PROGRESS (someone is on it); the
     * customer is emailed the reply and gets a bell row.
     */
    @Transactional
    public SupportTicketResponse reply(UUID id, UUID adminUserId, String body) {
        SupportTicket ticket = loadOrThrow(id);
        requireText(body, "Message");
        if (ticket.getStatus() == SupportTicketStatus.OPEN) {
            ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
        }
        ticket.setUpdatedBy(adminUserId);
        ticket.setAdminUnread(false);
        ticket.setCustomerUnread(true);
        ticket = ticketRepo.save(ticket);

        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicketId(ticket.getId());
        message.setAuthor(SupportMessageAuthor.ADMIN);
        message.setAuthorUserId(adminUserId);
        message.setBody(body.trim());
        messageRepo.save(message);

        final SupportTicket saved = ticket;
        final String reply = body.trim();
        best("reply email", () -> {
            String email = contactEmailFor(saved);
            if (email != null && !email.isBlank()) {
                emailService.sendSupportReplyEmail(email, resolveCustomerName(saved.getUserId()),
                        saved.getReference(), saved.getSubject(), reply);
            }
        });
        notifyCustomer(saved, "Support replied",
                "New reply on ticket " + saved.getReference() + " (" + saved.getSubject() + ").");
        return toResponse(ticket, true);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private SupportTicket loadOrThrow(UUID id) {
        return ticketRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Support ticket not found."));
    }

    private void requireOwner(SupportTicket ticket, UUID userId) {
        if (!ticket.getCredentialId().equals(resolveCredentialId(userId))) {
            throw new AccessDeniedException("This support ticket does not belong to you.");
        }
    }

    private static SupportCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("A category is required.");
        }
        try {
            return SupportCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown category: " + category.trim());
        }
    }

    private String uploadImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) return null;
        List<String> keys = new ArrayList<>();
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) continue;
            if (keys.size() >= MAX_IMAGES) break;
            String filename = image.getOriginalFilename();
            if (filename == null || filename.isBlank()) filename = "image";
            String key = "support/" + UUID.randomUUID() + "/" + filename;
            keys.add(contaboObjectService.uploadFile(key, image));
        }
        return keys.isEmpty() ? null : String.join("\n", keys);
    }

    private SupportTicketResponse toResponse(SupportTicket ticket, boolean withMessages) {
        List<String> imageUrls = new ArrayList<>();
        if (ticket.getImageKeys() != null && !ticket.getImageKeys().isBlank()) {
            for (String key : ticket.getImageKeys().split("\n")) {
                if (key != null && !key.isBlank()) {
                    imageUrls.add(contaboObjectService.getPresignedUrl(key.trim()));
                }
            }
        }
        List<SupportMessageResponse> messages = null;
        if (withMessages) {
            messages = messageRepo.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
                    .map(SupportMessageResponse::from)
                    .collect(Collectors.toList());
        }
        return SupportTicketResponse.from(ticket, imageUrls, messages);
    }

    /** Customer-facing labels, also used in the status email. */
    private static String stageLabel(SupportTicketStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In progress";
            case WAITING_FOR_CUSTOMER -> "Waiting for your reply";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
        };
    }

    /** Bell row for the ticket's owner (type SUPPORT_UPDATE); best-effort. */
    private void notifyCustomer(SupportTicket ticket, String title, String body) {
        if (ticket.getUserId() == null) return;
        try {
            pushService.sendToUser(ticket.getUserId(), title, body, "SUPPORT_UPDATE",
                    Map.of("id", ticket.getId().toString(), "type", "SUPPORT_UPDATE"));
        } catch (Exception e) {
            log.warn("[SUPPORT] customer notification failed: {}", e.getMessage());
        }
    }

    /** Display reference ST-{year}-{padded sequence}. Sequence is derived from the total count. */
    private String buildReference() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        long seq = ticketRepo.count();
        return String.format("ST-%d-%03d", year, seq);
    }

    /**
     * Resolve the caller's auth_credentials.id (sub) from the users.id principal — the ticket
     * owner is keyed on the credential. Prefers the LOCAL credential, else the first, else falls
     * back to the principal itself if it already is a credential id.
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
        if (userRepository.existsById(candidate)) return candidate;
        return authCredentialRepository.findById(candidate)
                .map(AuthCredentials::getUserId)
                .orElse(candidate);
    }

    private String contactEmailFor(SupportTicket ticket) {
        return ticket.getContactEmail() != null
                ? ticket.getContactEmail()
                : resolveContactEmail(ticket.getUserId(), ticket.getCredentialId());
    }

    private String resolveContactEmail(UUID userId, UUID credentialId) {
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

    private String resolveCustomerName(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(this::fullName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(null);
    }

    private String fullName(Users u) {
        String first = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String last = u.getLastName() == null ? "" : u.getLastName().trim();
        return (first + " " + last).trim();
    }

    private static String trimTo(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    /** Runs a best-effort side effect (email); logs but never propagates failure. */
    private void best(String what, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[SUPPORT] {} failed: {}", what, e.getMessage());
        }
    }
}
