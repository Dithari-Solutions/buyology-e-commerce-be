package com.buyology.ecommerce.user.service;

import com.buyology.ecommerce.user.config.TwilioProperties;
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

    public SmsService(TwilioProperties twilioProperties) {
        this.twilioProperties = twilioProperties;
        Twilio.init(twilioProperties.getAccountSid(), twilioProperties.getAuthToken());
    }

    /**
     * Sends a 6-digit OTP via SMS to the given E.164 phone number.
     * Throws RuntimeException if Twilio rejects the request.
     */
    public void sendOtp(String toPhoneNumber, String otpCode) {
        try {
            Message message = Message.creator(
                    new PhoneNumber(toPhoneNumber),
                    new PhoneNumber(twilioProperties.getPhoneNumber()),
                    "Your Buyology verification code is: " + otpCode + ". Valid for 10 minutes. Do not share this code."
            ).create();

            log.info("SMS OTP sent to {} — SID: {}", toPhoneNumber, message.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS OTP to {}: {}", toPhoneNumber, e.getMessage());
            throw new RuntimeException("Failed to send verification SMS. Please try again.", e);
        }
    }
}
