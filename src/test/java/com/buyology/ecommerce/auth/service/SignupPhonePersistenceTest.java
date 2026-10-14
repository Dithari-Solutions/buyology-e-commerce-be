package com.buyology.ecommerce.auth.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.domain.EmailOtp;
import com.buyology.ecommerce.auth.dto.SignUpRequest;
import com.buyology.ecommerce.auth.dto.OtpVerifyRequest;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.repository.EmailOtpRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SignupPhonePersistenceTest {
    private final AuthService service;
    private final Object[] dependencies;
    private EmailOtp held;
    SignupPhonePersistenceTest() throws Exception {
        Constructor<?> ctor = AuthService.class.getDeclaredConstructors()[0];
        Class<?>[] types = ctor.getParameterTypes(); dependencies = new Object[types.length];
        for (int i = 0; i < types.length; i++) dependencies[i] = types[i].isPrimitive() ? false : mock(types[i]);
        service = spy((AuthService) ctor.newInstance(dependencies));
        doReturn(ApiResponse.success("signed in", "Account created")).when(service).buildSigninResponse(any(), any());
        when(dependency(OtpProperties.class).getExpiryMinutes()).thenReturn(10);
        when(dependency(OtpProperties.class).getResendCooldownSeconds()).thenReturn(60);
        when(dependency(EmailOtpRepository.class).save(any())).thenAnswer(inv -> {
            held = inv.getArgument(0);
            ReflectionTestUtils.setField(held,"createdAt",Instant.now().minusSeconds(120));
            return held;
        });
        when(dependency(UserRepository.class).save(any())).thenAnswer(inv -> {
            Users user = inv.getArgument(0); user.setId(UUID.randomUUID()); return user;
        });
    }
    private <T> T dependency(Class<T> type) {
        for (Object value : dependencies) if (type.isInstance(value)) return type.cast(value);
        throw new IllegalArgumentException(type.getName());
    }
    private SignUpRequest request(String phone) {
        SignUpRequest request = new SignUpRequest();
        request.setEmail("phone@example.com"); request.setPassword("Buyology2026!"); request.setRepeatedPassword("Buyology2026!");
        request.setPhoneNumber(phone); return request;
    }
    private void verifyEmailCode(String code) {
        when(dependency(EmailOtpRepository.class).findTopByEmailAndUsedFalseAndTypeOrderByCreatedAtDesc("phone@example.com",EmailOtp.OtpType.SIGNUP))
                .thenReturn(Optional.of(held));
        OtpVerifyRequest verify = new OtpVerifyRequest(); verify.setEmail("phone@example.com"); verify.setOtpCode(code);
        service.verifyOtp(verify,new MockHttpServletRequest());
    }
    @Test void phoneSurvivesEmailVerificationAndIsSavedOnProfileAndCredential() {
        assertEquals(200,service.signup(request("+971 (50) 123-4567")).getStatusCode().value());
        assertEquals("+971501234567",held.getPhoneNumber());
        verifyEmailCode(held.getOtpCode());
        ArgumentCaptor<UserProfiles> profile = ArgumentCaptor.forClass(UserProfiles.class);
        verify(dependency(UserProfilesRepository.class)).save(profile.capture());
        assertEquals("+971501234567",profile.getValue().getPhoneNumber());
        assertTrue(profile.getValue().isPhoneVerified());
        ArgumentCaptor<AuthCredentials> credentials = ArgumentCaptor.forClass(AuthCredentials.class);
        verify(dependency(AuthCredentialRepository.class)).save(credentials.capture());
        assertEquals(profile.getValue().getUser().getId(),credentials.getValue().getUserId());
        assertEquals("+971501234567",credentials.getValue().getPhoneNumber());
    }
    @Test void resendPreservesPhoneBeforeAccountExists() {
        service.signup(request("+994501234567"));
        service.signup(request("+994501234567"));
        assertEquals("+994501234567",held.getPhoneNumber());
        verify(dependency(UserProfilesRepository.class),never()).save(any());
    }
    @Test void wrongEmailCodeCannotCreateAccountOrSavePhone() {
        service.signup(request("+971501234567"));
        verifyEmailCode("not-the-code");
        verify(dependency(UserProfilesRepository.class),never()).save(any());
        verify(dependency(UserRepository.class),never()).save(any());
    }
    @Test void oldClientsCanStillRegisterWithoutPhoneAndCompleteItBeforeCheckout() {
        service.signup(request(null));
        verifyEmailCode(held.getOtpCode());
        ArgumentCaptor<UserProfiles> profile = ArgumentCaptor.forClass(UserProfiles.class);
        verify(dependency(UserProfilesRepository.class)).save(profile.capture());
        assertNull(profile.getValue().getPhoneNumber());
    }
    @Test void malformedNumbersAreRejected() {
        for (String phone : new String[]{"", "abc", "+971", "++971501234567", "+1234567890123456"}) {
            assertThrows(IllegalArgumentException.class,()->AuthService.normaliseSignupPhone(phone));
        }
    }
}
