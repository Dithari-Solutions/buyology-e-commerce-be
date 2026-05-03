package com.buyology.ecommerce.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import com.buyology.ecommerce.auth.repository.EmailOtpRepository;
import com.buyology.ecommerce.infrastructure.config.AwsSesProperties;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailOtpRepository emailOtpRepository;
    private final AwsSesProperties sesProps;
    private final OtpProperties otpProps;
    private final SesV2Client sesClient;

    public EmailService(
            EmailOtpRepository emailOtpRepository,
            AwsSesProperties sesProps,
            OtpProperties otpProps,
            SesV2Client sesClient) {
        this.emailOtpRepository = emailOtpRepository;
        this.sesProps = sesProps;
        this.otpProps = otpProps;
        this.sesClient = sesClient;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void sendOtpEmail(String toEmail, String otpCode) {
        try {
            String htmlBody = buildOtpEmailHtml(otpCode);
            send(toEmail, "Verify your Buyology account", htmlBody);
            log.info("OTP email sent to {}", toEmail);
        } catch (IOException e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

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

    public void sendRegistrationSuccessEmail(String toEmail) {
        try {
            String htmlBody = loadTemplate("static/email.html");
            send(toEmail, "Welcome to Buyology!", htmlBody);
            log.info("Registration success email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send registration success email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Supplier emails ──────────────────────────────────────────────────────

    public void sendSupplierOtpEmail(String toEmail, String otpCode) {
        try {
            String htmlBody = buildOtpEmailHtml(otpCode);
            send(toEmail, "Verify your Buyology supplier application", htmlBody);
        } catch (IOException e) {
            log.error("Failed to send supplier OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void sendSupplierApplicationReceivedEmail(String toEmail, String supplierName, String businessName) {
        try {
            String template = loadTemplate("static/supplier-application-received.html");
            String html = template
                    .replace("{{SUPPLIER_NAME}}", supplierName)
                    .replace("{{BUSINESS_NAME}}", businessName)
                    .replace("{{APPLICATION_DATE}}", java.time.LocalDate.now().toString());
            send(toEmail, "Your supplier application is under review — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send supplier application received email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendSupplierApprovedEmail(String toEmail, String supplierName, String businessName, String setupLink) {
        try {
            String template = loadTemplate("static/supplier-approved.html");
            String html = template
                    .replace("{{SUPPLIER_NAME}}", supplierName)
                    .replace("{{BUSINESS_NAME}}", businessName)
                    .replace("{{SETUP_LINK}}", setupLink);
            send(toEmail, "Your Buyology supplier account is approved!", html);
        } catch (Exception e) {
            log.warn("Could not send supplier approved email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendSupplierRejectedEmail(String toEmail, String supplierName, String businessName, String reason) {
        try {
            String template = loadTemplate("static/supplier-rejected.html");
            String html = template
                    .replace("{{SUPPLIER_NAME}}", supplierName)
                    .replace("{{BUSINESS_NAME}}", businessName)
                    .replace("{{REJECTION_REASON}}", reason);
            send(toEmail, "Update on your Buyology supplier application", html);
        } catch (Exception e) {
            log.warn("Could not send supplier rejected email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendSupplierProductUnderReviewEmail(String toEmail, String supplierName, String productName, String sku) {
        try {
            String template = loadTemplate("static/supplier-product-under-review.html");
            String html = template
                    .replace("{{SUPPLIER_NAME}}", supplierName)
                    .replace("{{PRODUCT_NAME}}", productName)
                    .replace("{{PRODUCT_SKU}}", sku);
            send(toEmail, "Your product is under review — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send supplier product under review email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendSupplierProductApprovedEmail(String toEmail, String supplierName, String productName, String sku) {
        try {
            String template = loadTemplate("static/supplier-product-approved.html");
            String html = template
                    .replace("{{SUPPLIER_NAME}}", supplierName)
                    .replace("{{PRODUCT_NAME}}", productName)
                    .replace("{{PRODUCT_SKU}}", sku);
            send(toEmail, "Your product is now live on Buyology!", html);
        } catch (Exception e) {
            log.warn("Could not send supplier product approved email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendSupplierProductRejectedEmail(String toEmail, String supplierName, String productName, String reason) {
        try {
            String template = loadTemplate("static/supplier-product-rejected.html");
            String html = template
                    .replace("{{SUPPLIER_NAME}}", supplierName)
                    .replace("{{PRODUCT_NAME}}", productName)
                    .replace("{{REJECTION_REASON}}", reason);
            send(toEmail, "Action required: product review update — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send supplier product rejected email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Scheduled cleanup ────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredOtps() {
        int deleted = emailOtpRepository.deleteExpiredBefore(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired OTP record(s)", deleted);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void send(String toEmail, String subject, String htmlBody) throws IOException {
        String fromAddress = (sesProps.getFromName() != null && !sesProps.getFromName().isBlank())
                ? sesProps.getFromName() + " <" + sesProps.getFromEmail() + ">"
                : sesProps.getFromEmail();

        EmailContent content = EmailContent.builder()
                .simple(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                .build())
                        .build())
                .build();

        SendEmailRequest.Builder req = SendEmailRequest.builder()
                .fromEmailAddress(fromAddress)
                .destination(Destination.builder().toAddresses(toEmail).build())
                .content(content);

        if (sesProps.getConfigurationSet() != null && !sesProps.getConfigurationSet().isBlank()) {
            req.configurationSetName(sesProps.getConfigurationSet());
        }

        try {
            sesClient.sendEmail(req.build());
        } catch (SesV2Exception ex) {
            log.error("SES error sending to {}: {}", toEmail, ex.awsErrorDetails().errorMessage());
            throw new IOException("SES returned error: " + ex.awsErrorDetails().errorMessage(), ex);
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
