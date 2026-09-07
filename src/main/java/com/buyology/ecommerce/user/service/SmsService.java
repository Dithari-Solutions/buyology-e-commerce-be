package com.buyology.ecommerce.user.service;

import com.buyology.ecommerce.user.config.TwilioProperties;
import com.buyology.ecommerce.verification.service.PhoneVerificationGuard;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final TwilioProperties twilioProperties;
    private final PhoneVerificationGuard guard;

    public SmsService(TwilioProperties twilioProperties, PhoneVerificationGuard guard) {
        this.twilioProperties = twilioProperties;
        this.guard = guard;
        Twilio.init(twilioProperties.getAccountSid(), twilioProperties.getAuthToken());
    }

    /**
     * Sends a 6-digit OTP via SMS, but only once the send has cleared the spend guard.
     *
     * <p>The guard is enforced HERE rather than at each call site, and {@code quotaSubject} is a
     * required argument, so a new caller cannot send an SMS without deciding who is accountable for
     * it — the compiler asks the question. That matters because one of the existing callers is the
     * supplier apply form, which is permitAll: it sends a message to any number a stranger types,
     * and it sat in the 300-requests-per-minute public tier while an SMS-pumping attack ran the
     * Twilio balance to -$383.
     *
     * @param quotaSubject who is accountable — a user id, or an application id for a public form
     */
    public void sendOtp(String toPhoneNumber, String otpCode, String quotaSubject) {
        String phone = guard.check(quotaSubject, toPhoneNumber);
        try {
            Message message = Message.creator(
                    new PhoneNumber(phone),
                    new PhoneNumber(twilioProperties.getPhoneNumber()),
                    "Your Buyology verification code is: " + otpCode + ". Valid for 10 minutes. Do not share this code."
            ).create();

            log.info("SMS OTP sent to {} — SID: {}", mask(phone), message.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS OTP to {}: {}", mask(phone), e.getMessage());
            throw new RuntimeException("Failed to send verification SMS. Please try again.", e);
        }
    }

    /** Last four digits only — a full number never reaches a log line. */
    private static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "***" : "***" + phone.substring(phone.length() - 4);
    }
}
