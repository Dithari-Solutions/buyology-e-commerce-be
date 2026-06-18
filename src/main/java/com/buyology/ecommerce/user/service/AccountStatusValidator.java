package com.buyology.ecommerce.user.service;

import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guards user-initiated commerce actions (add to cart, save favourites, place an order)
 * against accounts that are pending deletion or already deleted. Such users must recover
 * their account first.
 *
 * <p>This is deliberately NOT enforced in the JWT filter: a PENDING_DELETION user must stay
 * authenticated so they can reach {@code POST /api/users/{userId}/account/recover}. SUSPENDED
 * accounts are already rejected by the filter and never reach here.
 */
@Component
public class AccountStatusValidator {

    private static final String STATUS_PENDING_DELETION = "PENDING_DELETION";
    private static final String STATUS_DELETED = "DELETED";

    private final UserRepository userRepository;

    public AccountStatusValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Rejects the action when the user's account is pending deletion or deleted.
     *
     * @param userId the {@code users.id} of the acting user
     * @throws IllegalStateException (→ HTTP 409 via GlobalExceptionHandler) when the account
     *                               is PENDING_DELETION or DELETED
     */
    public void requireActiveAccount(UUID userId) {
        if (userId == null) {
            return; // ownership/auth checks elsewhere handle a missing principal
        }
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return; // unknown user — let the downstream owner checks surface the real error
        }
        String status = user.getStatus();
        if (STATUS_PENDING_DELETION.equals(status) || STATUS_DELETED.equals(status)) {
            throw new IllegalStateException(
                    "Your account is scheduled for deletion. Please recover your account before "
                            + "adding items to your cart or favourites, or placing an order.");
        }
    }
}
