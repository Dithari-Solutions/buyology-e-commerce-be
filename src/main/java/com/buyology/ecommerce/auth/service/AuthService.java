package com.buyology.ecommerce.auth.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.auth.dto.SignUpRequest;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;

    public AuthService(UserRepository userRepository, AuthCredentialRepository authCredentialRepository) {
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
    }

    /**
     * Verifies if a signup request can proceed. If not, returns failure response.
     * If user does not exist, creates new user and auth credentials.
     *
     * @param request SignUpRequest DTO containing signup details
     * @return ApiResponse with status and message
     */

    @Transactional
    public ApiResponse verifySignup(SignUpRequest request) {

        // Check if user already exists
        Optional<AuthCredentials> existingUser = authCredentialRepository
                .findByEmailAndProvider(request.getEmail(), null);

        if (existingUser.isPresent()) {
            return ApiResponse.failure(HttpStatus.CONFLICT.value(), "User already exists");
        }

        if (request.getPassword() != request.getRepeatedPassowrd()) {
            return ApiResponse.failure(400, "Entered passwords are not same");
        }

        try {
            Users newUser = new Users();
            newUser.setIsGuest(false);
            newUser.setStatus("ACTIVE");

            userRepository.save(newUser);

            AuthCredentials credentials = new AuthCredentials();
            credentials.setUserId(newUser.getId());
            credentials.setEmail(request.getEmail());
            credentials.setIsActive(true);
            credentials.setPasswordHash(request.getPassword());
            credentials.setCreatedAt(java.time.Instant.now());

            authCredentialRepository.save(credentials);

            return ApiResponse.success(HttpStatus.CREATED.value(), "User registered successfully.");

        } catch (Exception e) {
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Something went wrong during signup");
        }
    }
}
