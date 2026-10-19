package com.buyology.ecommerce.partnership;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PartnershipInvitationTemplate {
    public static final String VERSION = "2026-10-19";
    public static final String SUBJECT = "Your invitation to partner with Buyology";
    public static final int MAX_RECIPIENTS = 50;
    private final String html;

    public PartnershipInvitationTemplate() throws IOException {
        try (var in = new ClassPathResource("static/partnership-invitation.html").getInputStream()) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    public record Preview(String version, String subject, String html, int maxRecipients) {}
    public Preview preview() { return new Preview(VERSION, SUBJECT, html, MAX_RECIPIENTS); }
}
