package com.buyology.ecommerce.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "otp")
public class OtpProperties {

    private int expiryMinutes = 10;
    private int resendCooldownSeconds = 60;
    /** How long a verified contact stays valid as "proof" before a submit must re-verify. */
    private int verificationValidityMinutes = 30;

    public int getExpiryMinutes() { return expiryMinutes; }
    public void setExpiryMinutes(int expiryMinutes) { this.expiryMinutes = expiryMinutes; }

    public int getResendCooldownSeconds() { return resendCooldownSeconds; }
    public void setResendCooldownSeconds(int resendCooldownSeconds) { this.resendCooldownSeconds = resendCooldownSeconds; }

    public int getVerificationValidityMinutes() { return verificationValidityMinutes; }
    public void setVerificationValidityMinutes(int verificationValidityMinutes) { this.verificationValidityMinutes = verificationValidityMinutes; }
}
