package com.buyology.ecommerce.auth.service;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.auth.dto.SignUpRequest;
import com.buyology.ecommerce.auth.dto.SignInRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.common.utils.PasswordUtils;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.common.utils.EmailValidation;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;

@Service
public class AuthService {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;

    public AuthService(TokenService tokenService,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
    }

    /**
     * Regular signup: create user + auth credentials.
     */
    @Transactional
    public ResponseEntity<ApiResponse<SignInResponse>> signup(SignUpRequest request) {

        // Validate email
        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.<SignInResponse>failure(HttpStatus.BAD_REQUEST, "Email is not valid");
        }

        // Check if user already exists
        Optional<AuthCredentials> existingUser = authCredentialRepository
                .findByEmailAndProvider(request.getEmail(), "LOCAL");
        if (existingUser.isPresent()) {
            return ApiResponse.<SignInResponse>failure(HttpStatus.CONFLICT, "User already exists");
        }

        // Check passwords match
        if (!request.getPassword().equals(request.getRepeatedPassword())) {
            return ApiResponse.<SignInResponse>failure(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }

        try {
            // Create User
            Users newUser = new Users();
            newUser.setIsGuest(false);
            newUser.setStatus("ACTIVE");
            userRepository.save(newUser);

            // Create AuthCredentials
            AuthCredentials credentials = new AuthCredentials();
            credentials.setUserId(newUser.getId());
            credentials.setEmail(request.getEmail());
            credentials.setPasswordHash(PasswordUtils.hashPassword(request.getPassword()));
            credentials.setProvider("LOCAL");
            credentials.setIsActive(true);
            credentials.setCreatedAt(java.time.Instant.now());
            authCredentialRepository.save(credentials);

            // Generate tokens
            String accessToken = tokenService.generateAccessToken(credentials);
            var refreshToken = tokenService.generateRefreshToken(credentials);

            // Return success response with tokens
            return ApiResponse.signinSuccess(
                    accessToken,
                    refreshToken.getToken(),
                    tokenService.getAccessTokenExpirySeconds());

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.<SignInResponse>failure(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Something went wrong during signup");
        }
    }

    /**
     * Signin using email + password
     */
    public ResponseEntity<ApiResponse<SignInResponse>> signin(SignInRequest request) {
        try {
            // Validate email
            if (!EmailValidation.isValid(request.getEmail())) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
            }

            // Find user by email + provider
            Optional<AuthCredentials> existingUser = authCredentialRepository
                    .findByEmailAndProvider(request.getEmail(), "LOCAL");
            if (existingUser.isEmpty()) {
                return ApiResponse.<SignInResponse>failure(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
            }

            AuthCredentials authCredentials = existingUser.get();

            // Verify password
            if (!PasswordUtils.verifyPassword(request.getPassword(), authCredentials.getPasswordHash())) {
                return ApiResponse.<SignInResponse>failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            // Generate tokens
            String accessToken = tokenService.generateAccessToken(authCredentials);
            var refreshToken = tokenService.generateRefreshToken(authCredentials);

            // Return success response
            return ApiResponse.signinSuccess(
                    accessToken,
                    refreshToken.getToken(),
                    tokenService.getAccessTokenExpirySeconds());

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.<SignInResponse>failure(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Something went wrong during signin");
        }
    }
}
