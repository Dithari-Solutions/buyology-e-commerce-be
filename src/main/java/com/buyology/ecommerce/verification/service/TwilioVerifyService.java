package com.buyology.ecommerce.verification.service;

import com.buyology.ecommerce.user.config.TwilioProperties;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around Twilio Verify (v2) for phone-number verification.
 *
 * <p>Twilio generates, stores, rate-limits and validates the OTP — we never store
 * the code locally. Configure the Verify Service SID via {@code TWILIO_VERIFY_SERVICE_SID}
 * (Twilio Console → Verify → Services → "VA…").</p>
 */
@Service
public class TwilioVerifyService {

    private static final Logger log = LoggerFactory.getLogger(TwilioVerifyService.class);

    private final TwilioProperties props;

    public TwilioVerifyService(TwilioProperties props) {
        this.props = props;
        if (props.getAccountSid() != null && !props.getAccountSid().isBlank()) {
            Twilio.init(props.getAccountSid(), props.getAuthToken());
        }
    }

    /** Sends an SMS verification code to the given E.164 number. */
    public void startVerification(String phoneNumber) {
        requireConfigured();
        try {
            Verification.creator(props.getVerifyServiceSid(), phoneNumber, "sms").create();
            log.info("Twilio Verify code sent to {}", mask(phoneNumber));
        } catch (ApiException e) {
            log.error("Twilio Verify start failed for {} — code={}, status={}, msg={}, moreInfo={}",
                    mask(phoneNumber), e.getCode(), e.getStatusCode(), e.getMessage(), e.getMoreInfo());
            throw new IllegalStateException(
                    "Could not send the verification code (Twilio error " + e.getCode()
                    + "). Please check the number and try again.");
        }
    }

    /** Returns true only when Twilio reports the code as "approved". */
    public boolean checkVerification(String phoneNumber, String code) {
        requireConfigured();
        try {
            VerificationCheck check = VerificationCheck.creator(props.getVerifyServiceSid())
                    .setTo(phoneNumber)
                    .setCode(code)
                    .create();
            return "approved".equalsIgnoreCase(check.getStatus());
        } catch (ApiException e) {
            // Twilio returns 404 when there's no pending verification (e.g. attempts exhausted).
            log.warn("Twilio Verify check failed for {} — code={}, status={}, msg={}",
                    mask(phoneNumber), e.getCode(), e.getStatusCode(), e.getMessage());
            return false;
        }
    }

    public boolean isConfigured() {
        return props.getVerifyServiceSid() != null && !props.getVerifyServiceSid().isBlank();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Phone verification is not configured.");
        }
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }
}
