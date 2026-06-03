package com.buyology.ecommerce.common.service;

import com.sendgrid.Client;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.buyology.ecommerce.auth.repository.EmailOtpRepository;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.infrastructure.config.TwilioSendGridProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    // Connect/socket timeouts so a hung SendGrid endpoint can't pin a thread indefinitely
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS = 10_000;

    private final EmailOtpRepository emailOtpRepository;
    private final TwilioSendGridProperties sendGridProps;
    private final OtpProperties otpProps;

    // Created once and reused for every send (the SendGrid client is thread-safe)
    private final SendGrid sendGrid;

    public EmailService(
            EmailOtpRepository emailOtpRepository,
            TwilioSendGridProperties sendGridProps,
            OtpProperties otpProps) {
        this.emailOtpRepository = emailOtpRepository;
        this.sendGridProps = sendGridProps;
        this.otpProps = otpProps;

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        this.sendGrid = new SendGrid(sendGridProps.getApiKey(), new Client(httpClient));
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

    @Async
    public void sendPromoCodeEmail(String toEmail, String promoCode, String offerText) {
        try {
            String html = loadTemplate("static/promo-code.html")
                    .replace("{{OFFER_TEXT}}", nullToEmpty(offerText))
                    .replace("{{PROMO_CODE}}", nullToEmpty(promoCode));
            send(toEmail, "Your exclusive promo code: " + promoCode, html);
        } catch (Exception e) {
            log.warn("Could not send promo email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendStreakReminderEmail(String toEmail, int streakCount) {
        try {
            String template = loadTemplate("static/streak-reminder-email.html");
            String html = template.replace("{{STREAK_COUNT}}", String.valueOf(streakCount));
            send(toEmail, "Don't break your " + streakCount + "-day streak! 🔥", html);
        } catch (Exception e) {
            log.warn("Could not send streak reminder email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
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

    @Async
    public void sendNewsletterSubscriptionEmail(String toEmail, String unsubscribeUrl) {
        try {
            String template = loadTemplate("static/newsletter-subscription-confirmation.html");
            String html = template.replace("{{UNSUBSCRIBE_URL}}", unsubscribeUrl);
            send(toEmail, "Welcome to the Buyology Newsletter!", html);
        } catch (Exception e) {
            log.warn("Could not send newsletter subscription email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendB2bInquiryNotification(String adminEmail, String company, String contact,
                                            String email, String phone, int quantity, String message) {
        try {
            String html = loadTemplate("static/b2b-inquiry-notification.html")
                    .replace("{{COMPANY}}", nullToEmpty(company))
                    .replace("{{CONTACT}}", nullToEmpty(contact))
                    .replace("{{EMAIL}}", nullToEmpty(email))
                    .replace("{{PHONE}}", phone != null ? phone : "—")
                    .replace("{{QUANTITY}}", String.valueOf(quantity))
                    .replace("{{MESSAGE}}", message != null ? message : "—");
            send(adminEmail, "New B2B Inquiry from " + company, html);
        } catch (Exception e) {
            log.warn("Could not send B2B inquiry notification: {}", e.getMessage());
        }
    }

    @Async
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

    @Async
    public void sendB2bApplicationReceivedEmail(String toEmail, String memberName, String companyName) {
        try {
            String template = loadTemplate("static/b2b-application-received.html");
            String html = template
                    .replace("{{MEMBER_NAME}}", memberName == null ? "" : memberName)
                    .replace("{{COMPANY_NAME}}", companyName == null ? "" : companyName)
                    .replace("{{APPLICATION_DATE}}", java.time.LocalDate.now().toString());
            send(toEmail, "Your B2B membership request is under review — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send B2B application received email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendB2bMembershipApprovedEmail(String toEmail, String memberName, String companyName,
                                               String membershipId, String creditAmount, String currency,
                                               String setupLink) {
        try {
            String template = loadTemplate("static/b2b-membership-approved.html");
            String html = template
                    .replace("{{MEMBER_NAME}}", memberName == null ? "" : memberName)
                    .replace("{{COMPANY_NAME}}", companyName == null ? "" : companyName)
                    .replace("{{MEMBERSHIP_ID}}", membershipId == null ? "" : membershipId)
                    .replace("{{CREDIT_AMOUNT}}", creditAmount == null ? "" : creditAmount)
                    .replace("{{CURRENCY}}", currency == null ? "" : currency)
                    .replace("{{SETUP_LINK}}", setupLink == null ? "" : setupLink);
            send(toEmail, "Your Buyology B2B Premium membership is approved!", html);
        } catch (Exception e) {
            log.warn("Could not send B2B approved email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendB2bMembershipRejectedEmail(String toEmail, String memberName, String companyName, String reason) {
        try {
            String template = loadTemplate("static/b2b-membership-rejected.html");
            String html = template
                    .replace("{{MEMBER_NAME}}", memberName == null ? "" : memberName)
                    .replace("{{COMPANY_NAME}}", companyName == null ? "" : companyName)
                    .replace("{{REASON}}", reason == null ? "" : reason);
            send(toEmail, "Update on your Buyology B2B membership application", html);
        } catch (Exception e) {
            log.warn("Could not send B2B rejected email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
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

    @Async
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

    @Async
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

    @Async
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

    @Async
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

    @Async
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

    // ── Refund emails ────────────────────────────────────────────────────────

    @Async
    public void sendRefundRequestReceivedEmail(String toEmail, String customerName,
                                               String orderNumber, String requestId) {
        try {
            String html = loadTemplate("static/refund-request-received.html")
                    .replace("{{CUSTOMER_NAME}}", nullToEmpty(customerName))
                    .replace("{{ORDER_NUMBER}}", nullToEmpty(orderNumber))
                    .replace("{{REQUEST_ID}}", nullToEmpty(requestId));
            send(toEmail, "We've received your refund request — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send refund request received email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendRefundApprovedEmail(String toEmail, String customerName, String orderNumber,
                                        String requestId, String courierFeeLocal) {
        try {
            String html = loadTemplate("static/refund-approved.html")
                    .replace("{{CUSTOMER_NAME}}", nullToEmpty(customerName))
                    .replace("{{ORDER_NUMBER}}", nullToEmpty(orderNumber))
                    .replace("{{REQUEST_ID}}", nullToEmpty(requestId))
                    .replace("{{COURIER_FEE_LOCAL}}", nullToEmpty(courierFeeLocal));
            send(toEmail, "Your refund request has been approved — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send refund approved email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendRefundRejectedEmail(String toEmail, String customerName, String orderNumber,
                                        String requestId, String reason) {
        try {
            String html = loadTemplate("static/refund-rejected.html")
                    .replace("{{CUSTOMER_NAME}}", nullToEmpty(customerName))
                    .replace("{{ORDER_NUMBER}}", nullToEmpty(orderNumber))
                    .replace("{{REQUEST_ID}}", nullToEmpty(requestId))
                    .replace("{{REASON}}", nullToEmpty(reason));
            send(toEmail, "Update on your refund request — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send refund rejected email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendRefundMethodConfirmedEmail(String toEmail, String customerName, String orderNumber,
                                               String requestId, String method, String instructions) {
        try {
            String html = loadTemplate("static/refund-method-confirmed.html")
                    .replace("{{CUSTOMER_NAME}}", nullToEmpty(customerName))
                    .replace("{{ORDER_NUMBER}}", nullToEmpty(orderNumber))
                    .replace("{{REQUEST_ID}}", nullToEmpty(requestId))
                    .replace("{{METHOD}}", nullToEmpty(method))
                    .replace("{{INSTRUCTIONS}}", nullToEmpty(instructions));
            send(toEmail, "Return method confirmed — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send refund method confirmed email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendRefundProductReceivedEmail(String toEmail, String customerName,
                                               String orderNumber, String requestId) {
        try {
            String html = loadTemplate("static/refund-received.html")
                    .replace("{{CUSTOMER_NAME}}", nullToEmpty(customerName))
                    .replace("{{ORDER_NUMBER}}", nullToEmpty(orderNumber))
                    .replace("{{REQUEST_ID}}", nullToEmpty(requestId));
            send(toEmail, "We've received your product — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send refund product received email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendRefundCompletedEmail(String toEmail, String customerName, String orderNumber,
                                         String requestId, String amount, String currency) {
        try {
            String html = loadTemplate("static/refund-completed.html")
                    .replace("{{CUSTOMER_NAME}}", nullToEmpty(customerName))
                    .replace("{{ORDER_NUMBER}}", nullToEmpty(orderNumber))
                    .replace("{{REQUEST_ID}}", nullToEmpty(requestId))
                    .replace("{{AMOUNT}}", nullToEmpty(amount))
                    .replace("{{CURRENCY}}", nullToEmpty(currency));
            send(toEmail, "Your refund has been processed — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send refund completed email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendRefundFailedEmail(String toEmail, String customerName,
                                      String orderNumber, String requestId) {
        try {
            String html = loadTemplate("static/refund-failed.html")
                    .replace("{{CUSTOMER_NAME}}", nullToEmpty(customerName))
                    .replace("{{ORDER_NUMBER}}", nullToEmpty(orderNumber))
                    .replace("{{REQUEST_ID}}", nullToEmpty(requestId));
            send(toEmail, "Issue processing your refund — Buyology", html);
        } catch (Exception e) {
            log.warn("Could not send refund failed email to {}: {}", toEmail, e.getMessage());
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

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
        Email from = new Email(sendGridProps.getFromEmail(), sendGridProps.getFromName());
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlBody);
        Mail mail = new Mail(from, subject, to, content);

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sendGrid.api(request);

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
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
