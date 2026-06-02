package com.buyology.ecommerce.user.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.user.domain.PhoneOtp;
import com.buyology.ecommerce.user.domain.UserAddress;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.dto.AddressResponse;
import com.buyology.ecommerce.user.dto.SaveAddressRequest;
import com.buyology.ecommerce.user.repository.PhoneOtpRepository;
import com.buyology.ecommerce.user.repository.UserAddressRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserAddressService {

    private final UserAddressRepository addressRepo;
    private final PhoneOtpRepository phoneOtpRepo;
    private final UserRepository userRepo;
    private final AuthCredentialRepository authCredentialRepo;
    private final SmsService smsService;
    private final OtpProperties otpProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserAddressService(UserAddressRepository addressRepo,
                               PhoneOtpRepository phoneOtpRepo,
                               UserRepository userRepo,
                               AuthCredentialRepository authCredentialRepo,
                               SmsService smsService,
                               OtpProperties otpProperties) {
        this.addressRepo = addressRepo;
        this.phoneOtpRepo = phoneOtpRepo;
        this.userRepo = userRepo;
        this.authCredentialRepo = authCredentialRepo;
        this.smsService = smsService;
        this.otpProperties = otpProperties;
    }

    // =========================================================================
    // Send phone OTP
    // =========================================================================

    @Transactional
    public void sendPhoneOtp(UUID authCredId, String phoneNumber) {
        Users user = resolveUser(authCredId);
        UUID internalUserId = user.getId();

        // Enforce resend cooldown
        phoneOtpRepo.findTopByUserIdAndPhoneNumberOrderByCreatedAtDesc(internalUserId, phoneNumber)
                .ifPresent(existing -> {
                    long secondsSince = ChronoUnit.SECONDS.between(existing.getCreatedAt(), Instant.now());
                    if (secondsSince < otpProperties.getResendCooldownSeconds()) {
                        long waitSeconds = otpProperties.getResendCooldownSeconds() - secondsSince;
                        throw new IllegalStateException(
                                "Please wait " + waitSeconds + " seconds before requesting a new OTP.");
                    }
                });

        String otpCode = String.format("%06d", secureRandom.nextInt(1_000_000));

        PhoneOtp otp = new PhoneOtp();
        otp.setUserId(internalUserId);
        otp.setPhoneNumber(phoneNumber);
        otp.setOtpCode(otpCode);
        otp.setExpiresAt(Instant.now().plus(otpProperties.getExpiryMinutes(), ChronoUnit.MINUTES));
        phoneOtpRepo.save(otp);

        smsService.sendOtp(phoneNumber, otpCode);
    }

    // =========================================================================
    // Save address (verifies OTP + geocodes)
    // =========================================================================

    @Transactional
    public AddressResponse saveAddress(UUID userId, SaveAddressRequest req) {
        Users user = resolveUser(userId);

        // ── Clear existing default if this address will be the new default ─────
        if (req.isDefault()) {
            addressRepo.clearDefaultForUser(user);
        }

        // ── 4. Persist address ────────────────────────────────────────────────
        UserAddress address = new UserAddress();
        address.setUser(user);
        address.setFirstName(req.getFirstName());
        address.setLastName(req.getLastName());
        address.setPhoneNumber(req.getPhoneNumber());
        address.setPhoneVerified(false); // set to true once SMS OTP is re-enabled
        address.setLabel(req.getLabel());
        address.setAddressLine1(req.getAddressLine1());
        address.setAddressLine2(req.getAddressLine2());
        address.setCity(req.getCity());
        address.setState(req.getState());
        address.setCountry(normalizeCountry(req.getCountry()));
        address.setPostalCode(req.getPostalCode());
        // Use coordinates from the frontend map picker if provided;
        // full geocoding (formatted address, verification) will be added when the map service is integrated.
        address.setLatitude(req.getLatitude());
        address.setLongitude(req.getLongitude());
        address.setFormattedAddress(null);
        address.setAddressVerified(false);
        address.setDefault(req.isDefault());

        return toResponse(addressRepo.save(address));
    }

    // =========================================================================
    // Queries
    // =========================================================================

    public List<AddressResponse> getAddresses(UUID userId) {
        Users user = resolveUser(userId);
        return addressRepo.findAllByUser(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AddressResponse getAddress(UUID userId, UUID addressId) {
        Users user = resolveUser(userId);
        UserAddress address = addressRepo.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new NoSuchElementException("Address not found: " + addressId));
        return toResponse(address);
    }

    // =========================================================================
    // Set default
    // =========================================================================

    @Transactional
    public AddressResponse setDefault(UUID userId, UUID addressId) {
        Users user = resolveUser(userId);
        UserAddress address = addressRepo.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new NoSuchElementException("Address not found: " + addressId));

        addressRepo.clearDefaultForUser(user);
        address.setDefault(true);
        return toResponse(addressRepo.save(address));
    }

    // =========================================================================
    // Delete
    // =========================================================================

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Users user = resolveUser(userId);
        UserAddress address = addressRepo.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new NoSuchElementException("Address not found: " + addressId));
        addressRepo.delete(address);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves a Users record from the auth_credentials.id that the JWT puts in the {userId} path variable.
     * The JWT sub claim is auth_credentials.id, not users.id.
     */
    private Users resolveUser(UUID authCredId) {
        AuthCredentials creds = authCredentialRepo.findById(authCredId)
                .orElseThrow(() -> new NoSuchElementException("Auth credentials not found: " + authCredId));
        Users user = userRepo.findById(creds.getUserId())
                .orElseThrow(() -> new NoSuchElementException("User not found for credentials: " + authCredId));
        // IDOR guard: the JWT principal is users.id; the {userId} path var is auth_credentials.id.
        // Ensure the resolved user is the authenticated caller (admins bypass).
        SecurityUtils.requireSelfOrAdmin(user.getId());
        return user;
    }

    // =========================================================================
    // Scheduled cleanup
    // =========================================================================

    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void cleanupExpiredOtps() {
        phoneOtpRepo.deleteExpired(Instant.now());
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    private AddressResponse toResponse(UserAddress a) {
        AddressResponse res = new AddressResponse();
        res.setId(a.getId());
        res.setFirstName(a.getFirstName());
        res.setLastName(a.getLastName());
        res.setPhoneNumber(a.getPhoneNumber());
        res.setPhoneVerified(a.isPhoneVerified());
        res.setLabel(a.getLabel());
        res.setAddressLine1(a.getAddressLine1());
        res.setAddressLine2(a.getAddressLine2());
        res.setCity(a.getCity());
        res.setState(a.getState());
        res.setCountry(a.getCountry());
        res.setPostalCode(a.getPostalCode());
        res.setFormattedAddress(a.getFormattedAddress());
        res.setLatitude(a.getLatitude());
        res.setLongitude(a.getLongitude());
        res.setAddressVerified(a.isAddressVerified());
        res.setDefault(a.isDefault());
        res.setCreatedAt(a.getCreatedAt());
        res.setUpdatedAt(a.getUpdatedAt());
        return res;
    }

    private String normalizeCountry(String code) {
        if (code == null) return null;
        String upper = code.toUpperCase();
        if (upper.equals("AZ")) return "AZE";
        if (upper.equals("AE")) return "UAE";
        return upper;
    }
}
