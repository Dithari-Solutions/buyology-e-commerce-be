package com.buyology.ecommerce.user.service;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserProfileService {

    private static final String AVATAR_UPLOAD_DIR = "/opt/uploads/user/avatars/";
    private static final String AVATAR_URL_PREFIX = "/user/avatars/";

    private final UserRepository userRepo;
    private final UserProfilesRepository profilesRepo;
    private final UserAddressRepository addressRepo;

    public UserProfileService(UserRepository userRepo,
                               UserProfilesRepository profilesRepo,
                               UserAddressRepository addressRepo) {
        this.userRepo = userRepo;
        this.profilesRepo = profilesRepo;
        this.addressRepo = addressRepo;
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

        String extension = resolveExtension(contentType);
        String filename = userId + extension;

        try {
            Path uploadDir = Paths.get(AVATAR_UPLOAD_DIR);
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save avatar file", e);
        }

        profile.setAvatarUrl(AVATAR_URL_PREFIX + filename);
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

    private Users findUser(UUID userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
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

        ProfileResponse res = new ProfileResponse();
        res.setUserId(user.getId());
        res.setFirstName(user.getFirstName());
        res.setLastName(user.getLastName());
        res.setPhoneNumber(profile.getPhoneNumber());
        res.setDateOfBirth(profile.getDateOfBirth());
        res.setAvatarUrl(profile.getAvatarUrl());
        res.setPaymentReady(missing.isEmpty());
        res.setMissingFields(missing);
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
