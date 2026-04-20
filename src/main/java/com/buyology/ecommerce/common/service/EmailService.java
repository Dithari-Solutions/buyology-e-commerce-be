package com.buyology.ecommerce.common.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.buyology.ecommerce.auth.repository.EmailOtpRepository;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.infrastructure.config.TwilioSendGridProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailOtpRepository emailOtpRepository;
    private final TwilioSendGridProperties sendGridProps;
    private final OtpProperties otpProps;

    public EmailService(
            EmailOtpRepository emailOtpRepository,
            TwilioSendGridProperties sendGridProps,
            OtpProperties otpProps) {
        this.emailOtpRepository = emailOtpRepository;
        this.sendGridProps = sendGridProps;
        this.otpProps = otpProps;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Send a 6-digit OTP verification email via Twilio SendGrid.
     */
    public void sendOtpEmail(String toEmail, String otpCode) {
        try {
            String htmlBody = buildOtpEmailHtml(otpCode);
            send(toEmail, "Verify your Buyology account", htmlBody);
            log.info("OTP email sent to {}", toEmail);
        } catch (IOException e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            // Preserve the original SendGrid error so callers can surface it
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Send a 6-digit OTP for the forgot-password flow.
     * Uses the same OTP template as signup but with a different subject line.
     */
    public void sendPasswordResetOtpEmail(String toEmail, String otpCode) {
        try {
            String htmlBody = buildOtpEmailHtml(otpCode);
            send(toEmail, "Reset your Buyology password", htmlBody);
            log.info("Password reset OTP email sent to {}", toEmail);
        } catch (IOException e) {
            log.error("Failed to send password reset OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void sendPromoCodeEmail(String toEmail, String promoCode, String offerText) {
        try {
            String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                    + "<h2 style='color:#402F75'>Exclusive Offer from Buyology! 🎉</h2>"
                    + "<p>" + offerText + "</p>"
                    + "<div style='background:#f5f5f5;padding:16px;border-radius:8px;text-align:center'>"
                    + "<span style='font-size:24px;font-weight:bold;letter-spacing:4px;color:#402F75'>"
                    + promoCode + "</span></div>"
                    + "<p style='color:#888;font-size:12px;margin-top:24px'>Buyology — Shop smarter.</p>"
                    + "</div>";
            send(toEmail, "Your exclusive promo code: " + promoCode, html);
        } catch (Exception e) {
            log.warn("Could not send promo email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendStreakReminderEmail(String toEmail, int streakCount) {
        try {
            String template = loadTemplate("static/streak-reminder-email.html");
            String html = template.replace("{{STREAK_COUNT}}", String.valueOf(streakCount));
            send(toEmail, "Don't break your " + streakCount + "-day streak! 🔥", html);
        } catch (Exception e) {
            log.warn("Could not send streak reminder email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendNewsletterEmail(String toEmail, String title, String htmlContent, String unsubscribeUrl) {
        try {
            String template = loadTemplate("static/newsletter-email.html");
            String html = template
                    .replace("{{TITLE}}", title)
                    .replace("{{CONTENT}}", htmlContent)
                    .replace("{{UNSUBSCRIBE_URL}}", unsubscribeUrl);
            send(toEmail, title, html);
        } catch (Exception e) {
            log.warn("Could not send newsletter email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendNewsletterSubscriptionEmail(String toEmail, String unsubscribeUrl) {
        try {
            String template = loadTemplate("static/newsletter-subscription-confirmation.html");
            String html = template.replace("{{UNSUBSCRIBE_URL}}", unsubscribeUrl);
            send(toEmail, "Welcome to the Buyology Newsletter!", html);
        } catch (Exception e) {
            log.warn("Could not send newsletter subscription email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendB2bInquiryNotification(String adminEmail, String company, String contact,
                                            String email, String phone, int quantity, String message) {
        try {
            String html = "<div style='font-family:sans-serif;max-width:600px;margin:auto'>"
                    + "<h2 style='color:#402F75'>New B2B Inquiry</h2>"
                    + "<table style='width:100%;border-collapse:collapse'>"
                    + "<tr><td><b>Company:</b></td><td>" + company + "</td></tr>"
                    + "<tr><td><b>Contact:</b></td><td>" + contact + "</td></tr>"
                    + "<tr><td><b>Email:</b></td><td>" + email + "</td></tr>"
                    + "<tr><td><b>Phone:</b></td><td>" + (phone != null ? phone : "—") + "</td></tr>"
                    + "<tr><td><b>Quantity:</b></td><td>" + quantity + "</td></tr>"
                    + "<tr><td><b>Message:</b></td><td>" + (message != null ? message : "—") + "</td></tr>"
                    + "</table></div>";
            send(adminEmail, "New B2B Inquiry from " + company, html);
        } catch (Exception e) {
            log.warn("Could not send B2B inquiry notification: {}", e.getMessage());
        }
    }

    /**
     * Send the registration success email after OTP verification.
     */
    public void sendRegistrationSuccessEmail(String toEmail) {
        try {
            String htmlBody = loadTemplate("static/email.html");
            send(toEmail, "Welcome to Buyology!", htmlBody);
            log.info("Registration success email sent to {}", toEmail);
        } catch (IOException e) {
            // Non-critical — log and continue; the user is already registered
            log.warn("Could not send registration success email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Scheduled cleanup ────────────────────────────────────────────────────

    /**
     * Delete expired OTP records every hour to keep the table lean.
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredOtps() {
        int deleted = emailOtpRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired OTP record(s)", deleted);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void send(String toEmail, String subject, String htmlBody) throws IOException {
        Email from    = new Email(sendGridProps.getFromEmail(), sendGridProps.getFromName());
        Email to      = new Email(toEmail);
        Content content = new Content("text/html", htmlBody);
        Mail mail     = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(sendGridProps.getApiKey());
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sg.api(request);

        if (response.getStatusCode() >= 400) {
            String body = response.getBody();
            log.error("SendGrid error {} sending to {}: {}", response.getStatusCode(), toEmail, body);
            throw new IOException(
                "SendGrid returned HTTP " + response.getStatusCode() + ": " + body
            );
        }
    }

    private String buildOtpEmailHtml(String otpCode) throws IOException {
        String template = loadTemplate("static/otp-email.html");
        return template
                .replace("{{OTP_CODE}}", otpCode)
                .replace("{{EXPIRY_MINUTES}}", String.valueOf(otpProps.getExpiryMinutes()));
    }

    private String loadTemplate(String classpathPath) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathPath);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
