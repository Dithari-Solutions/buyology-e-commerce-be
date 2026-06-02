package com.buyology.ecommerce.user.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.utils.FileValidationUtils;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.store.domain.Country;
import com.buyology.ecommerce.store.repository.CountryRepository;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.dto.ProfileResponse;
import com.buyology.ecommerce.user.dto.UpdateProfileRequest;
import com.buyology.ecommerce.user.repository.UserAddressRepository;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserProfileService {

    private final UserRepository userRepo;
    private final UserProfilesRepository profilesRepo;
    private final UserAddressRepository addressRepo;
    private final AuthCredentialRepository authCredentialRepo;
    private final CountryRepository countryRepo;
    private final ContaboObjectService contaboObjectService;

    public UserProfileService(UserRepository userRepo,
                               UserProfilesRepository profilesRepo,
                               UserAddressRepository addressRepo,
                               AuthCredentialRepository authCredentialRepo,
                               CountryRepository countryRepo,
                               ContaboObjectService contaboObjectService) {
        this.userRepo = userRepo;
        this.profilesRepo = profilesRepo;
        this.addressRepo = addressRepo;
        this.authCredentialRepo = authCredentialRepo;
        this.countryRepo = countryRepo;
        this.contaboObjectService = contaboObjectService;
    }

    // =========================================================================
    // Get profile
    // =========================================================================

    public ProfileResponse getProfile(UUID userId) {
        Users user = findUser(userId);
        UserProfiles profile = findOrCreateProfile(user);
        return toResponse(user, profile);
    }

    // =========================================================================
    // Update profile details
    // =========================================================================

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        Users user = findUser(userId);
        UserProfiles profile = findOrCreateProfile(user);

        if (req.getFirstName() != null && !req.getFirstName().isBlank()) {
            user.setFirstName(req.getFirstName());
        }
        if (req.getLastName() != null && !req.getLastName().isBlank()) {
            user.setLastName(req.getLastName());
        }
        userRepo.save(user);

        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()) {
            profile.setPhoneNumber(req.getPhoneNumber());
        }
        if (req.getDateOfBirth() != null) {
            profile.setDateOfBirth(req.getDateOfBirth());
        }
        if (req.getPreferredLanguage() != null && !req.getPreferredLanguage().isBlank()) {
            profile.setPreferredLanguage(req.getPreferredLanguage().toUpperCase());
        }
        // If a new country is provided, auto-derive its currency unless explicitly overridden
        if (req.getSelectedCountryCode() != null && !req.getSelectedCountryCode().isBlank()) {
            String countryCode = req.getSelectedCountryCode().toUpperCase();
            profile.setSelectedCountryCode(countryCode);
            if (req.getPreferredCurrency() == null || req.getPreferredCurrency().isBlank()) {
                countryRepo.findByCode(countryCode).ifPresent(c -> profile.setPreferredCurrency(c.getCurrency()));
            }
        }
        if (req.getPreferredCurrency() != null && !req.getPreferredCurrency().isBlank()) {
            profile.setPreferredCurrency(req.getPreferredCurrency().toUpperCase());
        }
        profilesRepo.save(profile);

        return toResponse(user, profile);
    }

    // =========================================================================
    // Update country preference (sets country + auto-derives currency)
    // =========================================================================

    @Transactional
    public ProfileResponse updateCountryPreference(UUID userId, String countryCode, String currency) {
        Users user = findUser(userId);
        UserProfiles profile = findOrCreateProfile(user);

        String code = countryCode.toUpperCase();
        Country country = countryRepo.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Country not found: " + code));

        profile.setSelectedCountryCode(code);
        // Use explicit currency if provided, otherwise derive from country
        if (currency != null && !currency.isBlank()) {
            profile.setPreferredCurrency(currency.toUpperCase());
        } else {
            profile.setPreferredCurrency(country.getCurrency());
        }
        profilesRepo.save(profile);
        return toResponse(user, profile);
    }

    // =========================================================================
    // Upload avatar
    // =========================================================================

    @Transactional
    public ProfileResponse updateAvatar(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file must not be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Avatar must be an image file (JPEG, PNG, WebP, etc.)");
        }

        Users user = findUser(userId);
        UserProfiles profile = findOrCreateProfile(user);

        // Delete old avatar from S3 if present
        if (profile.getAvatarUrl() != null && profile.getAvatarUrl().startsWith("users/")) {
            try { contaboObjectService.deleteFile(profile.getAvatarUrl()); } catch (Exception ignored) {}
        }

        String extension = resolveExtension(contentType);
        String key = "users/" + userId + "/avatar/" + userId + extension;
        contaboObjectService.uploadFile(key, file);

        // Store the S3 key (presigned URL generated on read)
        profile.setAvatarUrl(key);
        profilesRepo.save(profile);

        return toResponse(user, profile);
    }

    // =========================================================================
    // Payment readiness check
    // Called by PaymentService before initiating a payment.
    // Throws IllegalStateException listing all missing fields if not ready.
    // =========================================================================

    public void checkPaymentReadiness(UUID userId) {
        Users user = findUser(userId);
        UserProfiles profile = findOrCreateProfile(user);

        List<String> missing = new ArrayList<>();

        if (isBlank(user.getFirstName()))         missing.add("firstName");
        if (isBlank(user.getLastName()))          missing.add("lastName");
        if (isBlank(profile.getPhoneNumber()))    missing.add("phoneNumber");
        if (addressRepo.findAllByUser(user).isEmpty()) missing.add("deliveryAddress");

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Your profile is incomplete. Please fill in the following before paying: "
                    + String.join(", ", missing));
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves a Users record from the auth_credentials.id that the JWT puts in the {userId} path variable.
     * The JWT sub claim is auth_credentials.id, not users.id.
     */
    private Users findUser(UUID authCredId) {
        AuthCredentials creds = authCredentialRepo.findById(authCredId)
                .orElseThrow(() -> new NoSuchElementException("Auth credentials not found: " + authCredId));
        Users user = userRepo.findById(creds.getUserId())
                .orElseThrow(() -> new NoSuchElementException("User not found for credentials: " + authCredId));
        // IDOR guard: the JWT principal is users.id; the {userId} path var is auth_credentials.id.
        // Ensure the resolved user is the authenticated caller (admins bypass).
        SecurityUtils.requireSelfOrAdmin(user.getId());
        return user;
    }

    private UserProfiles findOrCreateProfile(Users user) {
        return profilesRepo.findByUser(user).orElseGet(() -> {
            UserProfiles p = new UserProfiles();
            p.setUser(user);
            return profilesRepo.save(p);
        });
    }

    private ProfileResponse toResponse(Users user, UserProfiles profile) {
        List<String> missing = computeMissingFields(user, profile);

        // Resolve email from auth_credentials — pick the first non-null email
        // (a user may have multiple credentials: e.g. EMAIL + Google OAuth)
        String email = authCredentialRepo.findByUserId(user.getId()).stream()
                .map(AuthCredentials::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst()
                .orElse(null);

        ProfileResponse res = new ProfileResponse();
        res.setUserId(user.getId());
        res.setEmail(email);
        res.setFirstName(user.getFirstName());
        res.setLastName(user.getLastName());
        res.setPhoneNumber(profile.getPhoneNumber());
        res.setDateOfBirth(profile.getDateOfBirth());
        String avatarUrl = null;
        if (profile.getAvatarUrl() != null) {
            try { avatarUrl = contaboObjectService.getPresignedUrl(profile.getAvatarUrl()); }
            catch (Exception ignored) { avatarUrl = profile.getAvatarUrl(); }
        }
        res.setAvatarUrl(avatarUrl);
        res.setPaymentReady(missing.isEmpty());
        res.setMissingFields(missing);
        res.setSelectedCountryCode(profile.getSelectedCountryCode());
        res.setPreferredCurrency(profile.getPreferredCurrency());
        res.setPreferredLanguage(profile.getPreferredLanguage());
        res.setCreatedAt(profile.getCreatedAt());
        res.setUpdatedAt(profile.getUpdatedAt());
        return res;
    }

    private List<String> computeMissingFields(Users user, UserProfiles profile) {
        List<String> missing = new ArrayList<>();
        if (isBlank(user.getFirstName()))      missing.add("firstName");
        if (isBlank(user.getLastName()))       missing.add("lastName");
        if (isBlank(profile.getPhoneNumber())) missing.add("phoneNumber");
        if (addressRepo.findAllByUser(user).isEmpty()) missing.add("deliveryAddress");
        return missing;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveExtension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif"  -> ".gif";
            default           -> ".jpg";
        };
    }
}
