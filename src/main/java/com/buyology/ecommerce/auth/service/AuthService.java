package com.buyology.ecommerce.auth.service;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.auth.dto.SignUpRequest;
import com.buyology.ecommerce.auth.dto.SignInRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.common.utils.PasswordUtils;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.common.utils.EmailValidation;
import com.buyology.ecommerce.user.repository.UserRepository;
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
    public ApiResponse<SignInResponse> signup(SignUpRequest request) {

        // 1️⃣ Validate email
        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.<SignInResponse>failure(HttpStatus.BAD_REQUEST.value(), "Email is not valid");
        }

        // 2️⃣ Check if user already exists
        Optional<AuthCredentials> existingUser = authCredentialRepository
                .findByEmailAndProvider(request.getEmail(), "EMAIL");
        if (existingUser.isPresent()) {
            return ApiResponse.<SignInResponse>failure(HttpStatus.CONFLICT.value(), "User already exists");
        }

        // 3️⃣ Check passwords match
        if (!request.getPassword().equals(request.getRepeatedPassword())) {
            return ApiResponse.<SignInResponse>failure(HttpStatus.BAD_REQUEST.value(), "Passwords do not match");
        }

        try {
            // 4️⃣ Create User
            Users newUser = new Users();
            newUser.setIsGuest(false);
            newUser.setStatus("ACTIVE");
            userRepository.save(newUser);

            // 5️⃣ Create AuthCredentials
            AuthCredentials credentials = new AuthCredentials();
            credentials.setUserId(newUser.getId());
            credentials.setEmail(request.getEmail());
            credentials.setPasswordHash(PasswordUtils.hashPassword(request.getPassword()));
            credentials.setProvider("EMAIL");
            credentials.setIsActive(true);
            credentials.setCreatedAt(java.time.Instant.now());
            authCredentialRepository.save(credentials);

            // 6️⃣ Generate tokens
            String accessToken = tokenService.generateAccessToken(credentials);
            var refreshToken = tokenService.generateRefreshToken(credentials);

            // 7️⃣ Return success response with tokens
            return ApiResponse.signinSuccess(
                    accessToken,
                    refreshToken.getToken(),
                    tokenService.getAccessTokenExpirySeconds()
            );

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.<SignInResponse>failure(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Something went wrong during signup");
        }
    }

    /**
     * Signin using email + password
     */
    public ApiResponse<SignInResponse> signin(SignInRequest request) {
        try {
            // 1️⃣ Validate email
            if (!EmailValidation.isValid(request.getEmail())) {
                return ApiResponse.<SignInResponse>failure(HttpStatus.BAD_REQUEST.value(), "Email is not valid");
            }

            // 2️⃣ Find user by email + provider
            Optional<AuthCredentials> existingUser = authCredentialRepository
                    .findByEmailAndProvider(request.getEmail(), "EMAIL");
            if (existingUser.isEmpty()) {
                return ApiResponse.<SignInResponse>failure(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED");
            }

            AuthCredentials authCredentials = existingUser.get();

            // 3️⃣ Verify password
            if (!PasswordUtils.verifyPassword(request.getPassword(), authCredentials.getPasswordHash())) {
                return ApiResponse.<SignInResponse>failure(HttpStatus.UNAUTHORIZED.value(), "Invalid credentials");
            }

            // 4️⃣ Generate tokens
            String accessToken = tokenService.generateAccessToken(authCredentials);
            var refreshToken = tokenService.generateRefreshToken(authCredentials);

            // 5️⃣ Return success response
            return ApiResponse.signinSuccess(
                    accessToken,
                    refreshToken.getToken(),
                    tokenService.getAccessTokenExpirySeconds()
            );

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.<SignInResponse>failure(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Something went wrong during signin");
        }
    }
}
