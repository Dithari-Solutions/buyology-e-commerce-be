package com.buyology.ecommerce.admin.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.repository.UserRepository;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserPhoneTest {
    private final AdminUserService service;
    private final Object[] dependencies;
    private final Users user = new Users();
    private final UUID credentialId = UUID.randomUUID();
    AdminUserPhoneTest() throws Exception {
        Constructor<?> ctor = AdminUserService.class.getDeclaredConstructors()[0];
        Class<?>[] types = ctor.getParameterTypes(); dependencies = new Object[types.length];
        for (int i = 0; i < types.length; i++) dependencies[i] = mock(types[i]);
        service = (AdminUserService) ctor.newInstance(dependencies);
        user.setId(UUID.randomUUID()); user.setFirstName("Ada");
        AuthCredentials credential = new AuthCredentials(); credential.setUserId(user.getId());
        when(dependency(AuthCredentialRepository.class).findById(credentialId)).thenReturn(Optional.of(credential));
        when(dependency(UserRepository.class).findById(user.getId())).thenReturn(Optional.of(user));
    }
    private <T> T dependency(Class<T> type) {
        for (Object value : dependencies) if (type.isInstance(value)) return type.cast(value);
        throw new IllegalArgumentException(type.getName());
    }
    @Test void dashboardCustomerShowsAccountPhoneCapturedAtSignup() {
        UserProfiles profile = new UserProfiles(); profile.setPhoneNumber("+971501234567");
        when(dependency(UserProfilesRepository.class).findByUser(user)).thenReturn(Optional.of(profile));
        assertEquals("+971501234567",service.getUserDetail(credentialId).getBody().getData().getPhoneNumber());
        verifyNoInteractions(dependency(PaymentTransactionRepository.class));
    }
    @Test void legacyCustomerShowsSavedPaymentContactWhenAccountAndAddressesHaveNone() {
        when(dependency(PaymentTransactionRepository.class).findUserContactPhones(eq(user.getId()),any()))
                .thenReturn(List.of("+994501234567"));
        assertEquals("+994501234567",service.getUserDetail(credentialId).getBody().getData().getPhoneNumber());
        verify(dependency(PaymentTransactionRepository.class)).findUserContactPhones(eq(user.getId()),any());
    }
    @Test void missingContactIsNotInvented() {
        assertNull(service.getUserDetail(credentialId).getBody().getData().getPhoneNumber());
    }
    @Test void credentialPhoneIsVisibleForExistingBusinessAccountsWithoutProfilePhone() {
        AuthCredentials credential = new AuthCredentials();
        credential.setPhoneNumber("+971501234567");
        when(dependency(AuthCredentialRepository.class).findByUserId(user.getId())).thenReturn(List.of(credential));
        assertEquals("+971501234567",service.getUserDetail(credentialId).getBody().getData().getPhoneNumber());
        verifyNoInteractions(dependency(PaymentTransactionRepository.class));
    }

}
