package com.buyology.ecommerce.giveaway.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.giveaway.domain.GiveawayEntry;
import com.buyology.ecommerce.giveaway.dto.GiveawayEntryAdminResponse;
import com.buyology.ecommerce.giveaway.dto.GiveawayStatusResponse;
import com.buyology.ecommerce.giveaway.repository.GiveawayEntryRepository;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import com.buyology.ecommerce.user.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Giveaway entries.
 *
 * The whole feature is an anti-abuse problem: a prize draw is only fair if one person cannot
 * enter twice. Three layers, deliberately stacked because each one alone is easy to walk
 * around:
 *
 * <ol>
 *   <li><b>One entry per account</b> — unique (campaign, user_id). Re-submitting is refused,
 *       not silently duplicated.</li>
 *   <li><b>One entry per Instagram handle</b> — unique (campaign, instagram_handle) on the
 *       NORMALISED handle, so {@code @Buyology}, {@code buyology} and
 *       {@code instagram.com/buyology/} are all the same row. This is what stops one person
 *       registering several site accounts and pointing them at the same Instagram.</li>
 *   <li><b>A reachable, verified account</b> — a delivery address plus a phone number verified
 *       by SMS OTP. Partly practical (a prize has to be delivered to someone), partly the
 *       anti-abuse teeth: the only cheap-to-fake identity here is an email address, while phone
 *       numbers cost real money and effort, so requiring a verified one is what actually caps
 *       how many accounts one person can make. Enforced on the write and advertised by the
 *       status endpoint so the UI can explain the gap up front.</li>
 * </ol>
 *
 * Both uniqueness rules exist as DB constraints as well as service checks: two concurrent
 * submissions pass the read-then-write check and only the constraint stops the duplicate.
 */
@Service
public class GiveawayService {

    private static final Logger log = LoggerFactory.getLogger(GiveawayService.class);

    /** Instagram's own rule: 1-30 chars of letters, digits, period, underscore. */
    private static final Pattern HANDLE = Pattern.compile("^[A-Za-z0-9._]{1,30}$");

    private final GiveawayEntryRepository repository;
    private final AuthCredentialRepository authCredentialRepository;
    private final UserRepository userRepository;
    private final UserProfilesRepository userProfilesRepository;
    private final UserProfileService userProfileService;

    public GiveawayService(GiveawayEntryRepository repository,
                           AuthCredentialRepository authCredentialRepository,
                           UserRepository userRepository,
                           UserProfilesRepository userProfilesRepository,
                           UserProfileService userProfileService) {
        this.repository = repository;
        this.authCredentialRepository = authCredentialRepository;
        this.userRepository = userRepository;
        this.userProfilesRepository = userProfilesRepository;
        this.userProfileService = userProfileService;
    }

    // =========================================================================
    // Customer
    // =========================================================================

    /** The caller's entry state, plus whether they could enter if they wanted to. */
    @Transactional(readOnly = true)
    public GiveawayStatusResponse status(UUID principal) {
        UUID userId = resolveUsersId(principal);
        long total = repository.countByCampaign(GiveawayEntry.DEFAULT_CAMPAIGN);
        Optional<GiveawayEntry> existing =
                repository.findByCampaignAndUserId(GiveawayEntry.DEFAULT_CAMPAIGN, userId);
        if (existing.isPresent()) {
            return GiveawayStatusResponse.from(existing.get(), total);
        }
        return GiveawayStatusResponse.notEntered(
                userProfileService.missingForContactableAction(userId), total);
    }

    /**
     * Enter the giveaway. Idempotent only in the sense that a second attempt is REFUSED with
     * a readable message rather than creating a second entry — the customer should be told
     * they are already in, and told plainly when a handle is already used by someone else.
     */
    @Transactional
    public GiveawayStatusResponse enter(UUID principal, String rawHandle) {
        UUID userId = resolveUsersId(principal);
        UUID credentialId = resolveCredentialId(principal);

        String handle = normalizeHandle(rawHandle);
        if (handle == null) {
            throw new IllegalArgumentException(
                    "That does not look like an Instagram username. Use the name from your profile URL, e.g. buyology.online.");
        }
        // A prize has to reach a real person at a real address, and the verified phone is also
        // what caps how many accounts one person can make — so this is checked on the write, not
        // just advertised by the status endpoint.
        List<String> missing = userProfileService.missingForContactableAction(userId);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Please complete your account first (" + String.join(", ", missing)
                            + ") so we can reach you if you win.");
        }
        if (repository.findByCampaignAndUserId(GiveawayEntry.DEFAULT_CAMPAIGN, userId).isPresent()) {
            throw new IllegalStateException("You are already entered in this giveaway.");
        }
        if (repository.findByCampaignAndInstagramHandle(GiveawayEntry.DEFAULT_CAMPAIGN, handle).isPresent()) {
            throw new IllegalStateException(
                    "That Instagram username is already entered. Each account can enter once.");
        }

        GiveawayEntry entry = new GiveawayEntry();
        entry.setCampaign(GiveawayEntry.DEFAULT_CAMPAIGN);
        entry.setUserId(userId);
        entry.setCredentialId(credentialId);
        entry.setInstagramHandle(handle);
        entry.setInstagramHandleRaw(rawHandle == null ? null : rawHandle.trim());
        entry.setContactEmail(resolveContactEmail(userId, credentialId));
        entry.setContactPhone(phoneOf(userId));

        try {
            entry = repository.save(entry);
        } catch (DataIntegrityViolationException e) {
            // Two submissions raced past the checks above; the constraint is the real guard.
            log.info("[GIVEAWAY] duplicate entry refused for user {} / handle {}", userId, handle);
            throw new IllegalStateException("You are already entered in this giveaway.");
        }
        return GiveawayStatusResponse.from(entry, repository.countByCampaign(GiveawayEntry.DEFAULT_CAMPAIGN));
    }

    // =========================================================================
    // Team (drawing a winner)
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<GiveawayEntryAdminResponse> listAll(int page, int size) {
        return repository
                .findByCampaignOrderByCreatedAtDesc(
                        GiveawayEntry.DEFAULT_CAMPAIGN,
                        PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)))
                .map(GiveawayEntryAdminResponse::from);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Reduce anything a customer might paste to the bare handle, or null when it cannot be
     * one. Accepts '@name', 'name', 'instagram.com/name', with or without scheme, trailing
     * slash or query string.
     */
    static String normalizeHandle(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        value = value.replaceFirst("(?i)^https?://", "");
        value = value.replaceFirst("(?i)^(www\\.)?instagram\\.com/", "");
        int cut = value.indexOf('?');
        if (cut >= 0) value = value.substring(0, cut);
        value = value.replaceAll("/+$", "");
        if (value.startsWith("@")) value = value.substring(1);
        value = value.trim().toLowerCase(Locale.ROOT);
        return HANDLE.matcher(value).matches() ? value : null;
    }

    private String phoneOf(UUID userId) {
        return userProfilesRepository.findByUserId(userId)
                .map(UserProfiles::getPhoneNumber)
                .filter(p -> p != null && !p.isBlank())
                .orElse(null);
    }

    /** users.id from a principal that may already be users.id, or an auth_credentials.id. */
    private UUID resolveUsersId(UUID candidate) {
        if (candidate == null) return null;
        if (userRepository.existsById(candidate)) return candidate;
        return authCredentialRepository.findById(candidate)
                .map(AuthCredentials::getUserId)
                .orElse(candidate);
    }

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
}
