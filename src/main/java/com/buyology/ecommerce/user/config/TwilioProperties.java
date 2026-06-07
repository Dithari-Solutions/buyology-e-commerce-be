package com.buyology.ecommerce.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "twilio")
public class TwilioProperties {

    private String accountSid;
    private String authToken;
    private String phoneNumber;
    /** Twilio Verify Service SID ("VA…") used for phone-number verification. */
    private String verifyServiceSid;

    public String getAccountSid() { return accountSid; }
    public void setAccountSid(String accountSid) { this.accountSid = accountSid; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getVerifyServiceSid() { return verifyServiceSid; }
    public void setVerifyServiceSid(String verifyServiceSid) { this.verifyServiceSid = verifyServiceSid; }
}
