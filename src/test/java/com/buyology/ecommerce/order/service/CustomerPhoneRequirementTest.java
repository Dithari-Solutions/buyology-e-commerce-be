package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.user.domain.UserProfiles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerPhoneRequirementTest {
    @Test
    void missingOrBlankPhoneCannotPlaceOrderEvenWhenDefaultVerified() {
        UserProfiles profile = new UserProfiles();
        for (String phone : new String[] {null, "", "   "}) {
            profile.setPhoneNumber(phone);
            assertThrows(IllegalStateException.class, () -> OrderService.requireCustomerPhone(profile));
        }
    }

    @Test
    void phoneWithoutSmsVerificationCanPlaceOrder() {
        UserProfiles profile = new UserProfiles();
        profile.setPhoneNumber("+971501234567");
        profile.setPhoneVerified(false);
        assertDoesNotThrow(() -> OrderService.requireCustomerPhone(profile));
    }
}
