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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

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

    /**
     * Approval email for the sign-up flow where the applicant already set their
     * password. The account is active and ready — this email points them at the
     * login page rather than a "set your password" setup link.
     */
    @Async
    public void sendB2bMembershipActivatedEmail(String toEmail, String memberName, String companyName,
                                                String membershipId, String creditAmount, String currency,
                                                String loginLink) {
        try {
            String template = loadTemplate("static/b2b-membership-activated.html");
            String html = template
                    .replace("{{MEMBER_NAME}}", memberName == null ? "" : memberName)
                    .replace("{{COMPANY_NAME}}", companyName == null ? "" : companyName)
                    .replace("{{MEMBERSHIP_ID}}", membershipId == null ? "" : membershipId)
                    .replace("{{CREDIT_AMOUNT}}", creditAmount == null ? "" : creditAmount)
                    .replace("{{CURRENCY}}", currency == null ? "" : currency)
                    .replace("{{LOGIN_LINK}}", loginLink == null ? "" : loginLink);
            send(toEmail, "Your Buyology B2B Premium membership is approved!", html);
        } catch (Exception e) {
            log.warn("Could not send B2B activated email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendB2bMembershipRejectedEmail(String toEmail, String memberName, String companyName,
                                               String reason, String resubmitUrl) {
        try {
            String template = loadTemplate("static/b2b-membership-rejected.html");
            String html = template
                    .replace("{{MEMBER_NAME}}", memberName == null ? "" : memberName)
                    .replace("{{COMPANY_NAME}}", companyName == null ? "" : companyName)
                    .replace("{{REASON}}", reason == null ? "" : reason)
                    .replace("{{RESUBMIT_URL}}", resubmitUrl == null ? "" : resubmitUrl);
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

    // ── Account deletion lifecycle ─────────────────────────────────────────────

    @Async
    public void sendAccountDeletionRequestedEmail(String toEmail, String name) {
        try {
            String body = "<p>Hi " + safeName(name) + ",</p>"
                    + "<p>We've received your request to delete your Buyology account. Your account is now "
                    + "scheduled for permanent deletion in <strong>30 days</strong>.</p>"
                    + "<p>Changed your mind? You can recover your account any time before then by signing in "
                    + "and choosing <strong>Recover account</strong> from your profile.</p>"
                    + "<p>After 30 days your personal data will be permanently removed and this cannot be undone.</p>";
            send(toEmail, "Your Buyology account deletion request", accountEmailHtml("Account deletion requested", body));
            log.info("Account deletion requested email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send account-deletion-requested email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendAccountRecoveryEmail(String toEmail, String name) {
        try {
            String body = "<p>Hi " + safeName(name) + ",</p>"
                    + "<p>Good news — your Buyology account has been <strong>recovered</strong> and the scheduled "
                    + "deletion has been cancelled. Everything is back to normal.</p>"
                    + "<p>If you didn't do this, please contact support@buyology.com immediately.</p>";
            send(toEmail, "Your Buyology account has been recovered", accountEmailHtml("Account recovered", body));
            log.info("Account recovery email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send account-recovery email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendAccountDeletedEmail(String toEmail, String name) {
        try {
            String body = "<p>Hi " + safeName(name) + ",</p>"
                    + "<p>Your Buyology account has been <strong>permanently deleted</strong> and your personal "
                    + "data removed, as requested. We're sorry to see you go.</p>"
                    + "<p>You're always welcome back — just create a new account any time.</p>";
            send(toEmail, "Your Buyology account has been deleted", accountEmailHtml("Account deleted", body));
            log.info("Account deleted email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send account-deleted email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Order cancellation ─────────────────────────────────────────────────────

    @Async
    public void sendOrderCancelledEmail(String toEmail, String name, String orderNumber, String reason) {
        try {
            String body = "<p>Hi " + safeName(name) + ",</p>"
                    + "<p>Your order <strong>" + nullToEmpty(orderNumber) + "</strong> has been <strong>cancelled</strong>"
                    + (reason != null && !reason.isBlank() ? " — " + reason : "") + ".</p>"
                    + "<p>If this order was already paid, a refund has been initiated automatically — you'll receive a "
                    + "separate email with the details. If you didn't request this, please contact support@buyology.com.</p>";
            send(toEmail, "Your Buyology order has been cancelled", accountEmailHtml("Order cancelled", body));
            log.info("Order-cancelled email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send order-cancelled email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendRefundInitiatedOnCancelEmail(String toEmail, String name, String orderNumber, String amount, String currency) {
        try {
            String body = "<p>Hi " + safeName(name) + ",</p>"
                    + "<p>We've initiated a refund of <strong>" + nullToEmpty(currency) + " " + nullToEmpty(amount) + "</strong> "
                    + "for your cancelled order <strong>" + nullToEmpty(orderNumber) + "</strong>.</p>"
                    + "<p>The amount goes back to your original payment method. Depending on your bank or provider this "
                    + "can take a few business days to appear.</p>";
            send(toEmail, "Your refund has been initiated", accountEmailHtml("Refund initiated", body));
            log.info("Refund-initiated email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send refund-initiated email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── B2B RFQ (quotes) ───────────────────────────────────────────────────────

    /** Internal notification to procurement: a member submitted a quote request. */
    @Async
    public void sendB2bQuoteSubmittedNotification(String procurementEmail, String companyName,
                                                  String quoteRef, int lineCount, String reviewUrl) {
        try {
            String body = "<p>A B2B member has submitted a new quote request.</p>"
                    + "<p><strong>Company:</strong> " + nullToEmpty(companyName) + "<br/>"
                    + "<strong>Quote:</strong> " + nullToEmpty(quoteRef) + "<br/>"
                    + "<strong>Lines:</strong> " + lineCount + "</p>"
                    + "<p>Please price it in the Procurement → Quotes section"
                    + (reviewUrl != null && !reviewUrl.isBlank()
                        ? ": <a href=\"" + reviewUrl + "\">" + reviewUrl + "</a>" : ".") + "</p>";
            send(procurementEmail, "New B2B quote request from " + nullToEmpty(companyName),
                    accountEmailHtml("New quote request", body));
            log.info("B2B quote-submitted notification sent to {}", procurementEmail);
        } catch (IOException e) {
            log.warn("Could not send B2B quote-submitted notification to {}: {}", procurementEmail, e.getMessage());
        }
    }

    /**
     * Member notification: procurement has priced their quote and it awaits acceptance.
     * Detailed email — per-item image + SKU + description + qty + lead time + prices,
     * payment terms, terms &amp; conditions, and the member's company details.
     */
    @Async
    public void sendB2bQuoteReadyEmail(String toEmail, String memberName, String quoteRef,
                                       String currency, String total, java.util.List<B2bOrderLine> lines,
                                       String paymentTerms, String termsAndConditions,
                                       String buyerCompany, String buyerWebsite, String buyerEmail,
                                       String validUntil, String quoteUrl) {
        try {
            String body = "<p>Hi " + safeName(memberName) + ",</p>"
                    + "<p>Your quote request <strong>" + escapeHtml(nullToEmpty(quoteRef)) + "</strong> has been "
                    + "priced by our procurement team. The details are below.</p>"
                    + (validUntil != null && !validUntil.isBlank()
                        ? "<p style=\"font-size:13px;color:#6b6b6b;\"><strong>Valid until:</strong> " + escapeHtml(validUntil) + "</p>" : "")
                    + "<p style=\"margin:16px 0 4px;font-weight:bold;color:#1f1b3a;\">Quote summary</p>"
                    + b2bItemsTableHtml(currency, total, lines)
                    + b2bTermsHtml(paymentTerms, termsAndConditions)
                    + b2bPartiesHtml(buyerCompany, buyerWebsite, buyerEmail)
                    + "<p style=\"margin-top:20px;\">Review the quoted prices and accept to proceed to checkout"
                    + (quoteUrl != null && !quoteUrl.isBlank()
                        ? ": <a href=\"" + quoteUrl + "\">" + quoteUrl + "</a>" : ".") + "</p>";
            send(toEmail, "Your Buyology B2B quote is ready", accountEmailHtml("Your quote is ready", body));
            log.info("B2B quote-ready email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send B2B quote-ready email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Member notification: procurement declined to quote the request. */
    @Async
    public void sendB2bQuoteRejectedEmail(String toEmail, String memberName, String quoteRef, String reason) {
        try {
            String body = "<p>Hi " + safeName(memberName) + ",</p>"
                    + "<p>Unfortunately we're unable to quote your request <strong>" + nullToEmpty(quoteRef)
                    + "</strong> at this time"
                    + (reason != null && !reason.isBlank() ? " — " + reason : "") + ".</p>"
                    + "<p>You're welcome to start a new request or contact support@buyology.com for assistance.</p>";
            send(toEmail, "Update on your Buyology B2B quote request",
                    accountEmailHtml("Quote request update", body));
            log.info("B2B quote-rejected email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send B2B quote-rejected email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Member confirmation: we've received their quote request and it's queued for pricing. */
    @Async
    public void sendB2bQuoteReceivedEmail(String toEmail, String memberName, String quoteRef,
                                          int lineCount, String quoteUrl) {
        try {
            String body = "<p>Hi " + safeName(memberName) + ",</p>"
                    + "<p>Thanks — we've received your B2B quote request <strong>" + nullToEmpty(quoteRef)
                    + "</strong>" + (lineCount > 0 ? " (" + lineCount + " line" + (lineCount == 1 ? "" : "s") + ")" : "")
                    + ".</p>"
                    + "<p>Our procurement team will price it shortly and email you as soon as your quote is ready to review"
                    + (quoteUrl != null && !quoteUrl.isBlank()
                        ? ". You can track it here: <a href=\"" + quoteUrl + "\">" + quoteUrl + "</a>" : ".") + "</p>";
            send(toEmail, "We received your Buyology B2B quote request",
                    accountEmailHtml("Quote request received", body));
            log.info("B2B quote-received email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send B2B quote-received email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Member confirmation: they accepted the priced quote — it's ready to check out. */
    @Async
    public void sendB2bQuoteAcceptedEmail(String toEmail, String memberName, String quoteRef,
                                          String total, String currency, String quoteUrl) {
        try {
            String body = "<p>Hi " + safeName(memberName) + ",</p>"
                    + "<p>You've accepted your quote <strong>" + nullToEmpty(quoteRef) + "</strong>"
                    + (total != null && !total.isBlank()
                        ? " for a total of <strong>" + nullToEmpty(currency) + " " + nullToEmpty(total) + "</strong>" : "")
                    + ".</p>"
                    + "<p>You can now proceed to checkout to place your order"
                    + (quoteUrl != null && !quoteUrl.isBlank()
                        ? ": <a href=\"" + quoteUrl + "\">" + quoteUrl + "</a>" : ".") + "</p>";
            send(toEmail, "Your Buyology B2B quote is accepted — ready to check out",
                    accountEmailHtml("Quote accepted", body));
            log.info("B2B quote-accepted email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send B2B quote-accepted email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** One priced line on a B2B quote/order, for the detailed emails (incl. SKU + image). */
    public record B2bOrderLine(String name, String description, int quantity,
                               String unitPrice, String lineTotal, String leadTime,
                               String sku, String imageUrl) {}

    // ── Shared B2B quote/order email fragments ──────────────────────────────────

    /** Item table: image, name + description + SKU, qty, lead time, unit price, line total. */
    private String b2bItemsTableHtml(String currency, String total, java.util.List<B2bOrderLine> lines) {
        String ccy = nullToEmpty(currency);
        String cell = "padding:10px 8px;border-bottom:1px solid #eee;vertical-align:top;";
        StringBuilder rows = new StringBuilder();
        if (lines != null) {
            for (B2bOrderLine l : lines) {
                String img = l.imageUrl() != null && !l.imageUrl().isBlank()
                        ? "<img src=\"" + l.imageUrl() + "\" width=\"56\" height=\"56\" alt=\"\" style=\"width:56px;height:56px;object-fit:contain;border-radius:8px;background:#f4f2fb;border:1px solid #eee;\" />"
                        : "<div style=\"width:56px;height:56px;border-radius:8px;background:#f4f2fb;border:1px solid #eee;\"></div>";
                String desc = l.description() != null && !l.description().isBlank()
                        ? "<br/><span style=\"color:#8a8a8a;font-size:12px;\">" + escapeHtml(l.description()) + "</span>" : "";
                String sku = l.sku() != null && !l.sku().isBlank()
                        ? "<br/><span style=\"color:#8a8a8a;font-size:12px;\">SKU: <span style=\"font-family:monospace;color:#4a4a4a;\">" + escapeHtml(l.sku()) + "</span></span>" : "";
                String lead = l.leadTime() != null && !l.leadTime().isBlank() ? escapeHtml(l.leadTime()) : "—";
                rows.append("<tr>")
                    .append("<td style=\"").append(cell).append("width:64px;\">").append(img).append("</td>")
                    .append("<td style=\"").append(cell).append("\"><strong>").append(escapeHtml(nullToEmpty(l.name()))).append("</strong>").append(desc).append(sku).append("</td>")
                    .append("<td style=\"").append(cell).append("text-align:center;\">").append(l.quantity()).append("</td>")
                    .append("<td style=\"").append(cell).append("\">").append(lead).append("</td>")
                    .append("<td style=\"").append(cell).append("text-align:right;\">").append(ccy).append(" ").append(nullToEmpty(l.unitPrice())).append("</td>")
                    .append("<td style=\"").append(cell).append("text-align:right;\">").append(ccy).append(" ").append(nullToEmpty(l.lineTotal())).append("</td>")
                    .append("</tr>");
            }
        }
        String head = "padding:9px 8px;text-align:left;font-size:12px;text-transform:uppercase;color:#6b6b6b;border-bottom:2px solid #e5e1f2;";
        return "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;margin:8px 0 4px;font-family:Arial,sans-serif;font-size:13px;\">"
                + "<thead><tr>"
                + "<th style=\"" + head + "\">Image</th>"
                + "<th style=\"" + head + "\">Item</th>"
                + "<th style=\"" + head + "text-align:center;\">Qty</th>"
                + "<th style=\"" + head + "\">Lead time</th>"
                + "<th style=\"" + head + "text-align:right;\">Unit price</th>"
                + "<th style=\"" + head + "text-align:right;\">Line total</th>"
                + "</tr></thead><tbody>" + rows + "</tbody>"
                + "<tfoot><tr>"
                + "<td colspan=\"5\" style=\"padding:11px 8px;text-align:right;font-weight:bold;\">Total</td>"
                + "<td style=\"padding:11px 8px;text-align:right;font-weight:bold;\">" + ccy + " " + nullToEmpty(total) + "</td>"
                + "</tr></tfoot></table>";
    }

    /** Payment-terms + terms &amp; conditions blocks (each omitted when blank). */
    private String b2bTermsHtml(String paymentTerms, String termsAndConditions) {
        String block = "background:#faf9fd;border:1px solid #efecf8;border-radius:8px;padding:14px 16px;font-size:13px;line-height:1.6;color:#4a4a4a;";
        String out = "";
        if (paymentTerms != null && !paymentTerms.isBlank()) {
            out += "<p style=\"margin:16px 0 4px;font-weight:bold;color:#1f1b3a;\">Payment terms</p>"
                    + "<div style=\"" + block + "\">" + escapeHtml(paymentTerms).replace("\n", "<br/>") + "</div>";
        }
        if (termsAndConditions != null && !termsAndConditions.isBlank()) {
            out += "<p style=\"margin:16px 0 4px;font-weight:bold;color:#1f1b3a;\">Terms &amp; conditions</p>"
                    + "<div style=\"" + block + "\">" + escapeHtml(termsAndConditions).replace("\n", "<br/>") + "</div>";
        }
        return out;
    }

    /** Buyer (B2B member company / website / email) + Seller (Buyology) blocks, side by side. */
    private String b2bPartiesHtml(String buyerCompany, String buyerWebsite, String buyerEmail) {
        String buyerBlock = "<p style=\"margin:0;font-weight:bold;color:#1f1b3a;\">Buyer</p>"
                + "<p style=\"margin:2px 0 0;color:#4a4a4a;font-size:13px;line-height:1.6;\">"
                + escapeHtml(nullToEmpty(buyerCompany))
                + (buyerWebsite != null && !buyerWebsite.isBlank() ? "<br/>" + escapeHtml(buyerWebsite) : "")
                + (buyerEmail != null && !buyerEmail.isBlank() ? "<br/>" + escapeHtml(buyerEmail) : "")
                + "</p>";
        String sellerBlock = "<p style=\"margin:0;font-weight:bold;color:#1f1b3a;\">Seller</p>"
                + "<p style=\"margin:2px 0 0;color:#4a4a4a;font-size:13px;line-height:1.6;\">"
                + "Buyology<br/>https://buyology.online<br/>support@buyology.com</p>";
        return "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-top:18px;\"><tr>"
                + "<td width=\"50%\" style=\"vertical-align:top;padding-right:8px;\">" + buyerBlock + "</td>"
                + "<td width=\"50%\" style=\"vertical-align:top;padding-left:8px;\">" + sellerBlock + "</td>"
                + "</tr></table>";
    }

    /** Internal notification to procurement: a member uploaded a bank-transfer proof to validate. */
    @Async
    public void sendB2bBankTransferSubmittedNotification(String procurementEmail, String companyName,
                                                         String quoteRef, String reviewUrl) {
        try {
            String body = "<p>A B2B member has paid by bank transfer and uploaded a proof of payment for validation.</p>"
                    + "<p><strong>Company:</strong> " + escapeHtml(nullToEmpty(companyName)) + "<br/>"
                    + "<strong>Quote:</strong> " + escapeHtml(nullToEmpty(quoteRef)) + "</p>"
                    + "<p>Review the proof and validate the payment in Procurement → Quotes"
                    + (reviewUrl != null && !reviewUrl.isBlank()
                        ? ": <a href=\"" + reviewUrl + "\">" + reviewUrl + "</a>" : ".") + "</p>";
            send(procurementEmail, "B2B bank-transfer proof to validate — " + nullToEmpty(companyName),
                    accountEmailHtml("Bank-transfer proof uploaded", body));
            log.info("B2B bank-transfer notification sent to {}", procurementEmail);
        } catch (IOException e) {
            log.warn("Could not send B2B bank-transfer notification to {}: {}", procurementEmail, e.getMessage());
        }
    }

    /** Member notification: their bank-transfer proof was not accepted; please re-submit. */
    @Async
    public void sendB2bBankTransferRejectedEmail(String toEmail, String memberName, String quoteRef,
                                                 String reason, String quoteUrl) {
        try {
            String body = "<p>Hi " + safeName(memberName) + ",</p>"
                    + "<p>We were unable to validate the bank-transfer proof for your quote <strong>"
                    + escapeHtml(nullToEmpty(quoteRef)) + "</strong>"
                    + (reason != null && !reason.isBlank() ? " — " + escapeHtml(reason) : "") + ".</p>"
                    + "<p>Please re-submit a valid proof of payment from your quote page"
                    + (quoteUrl != null && !quoteUrl.isBlank()
                        ? ": <a href=\"" + quoteUrl + "\">" + quoteUrl + "</a>" : ".") + "</p>";
            send(toEmail, "Action needed: bank-transfer proof for your Buyology B2B order",
                    accountEmailHtml("Proof of payment not accepted", body));
            log.info("B2B bank-transfer-rejected email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send B2B bank-transfer-rejected email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Member confirmation: their bank-transfer proof was validated and the order placed.
     * Includes every line (name, description, qty, lead time, unit price, line total), the
     * payment terms, the terms &amp; conditions, and both the seller (Buyology) and buyer
     * (B2B member) company details.
     */
    @Async
    public void sendB2bOrderPlacedEmail(String toEmail, String memberName, String quoteRef, String orderRef,
                                        String currency, String total, java.util.List<B2bOrderLine> lines,
                                        String paymentTerms, String termsAndConditions,
                                        String buyerCompany, String buyerWebsite, String buyerEmail) {
        try {
            String body = "<p>Hi " + safeName(memberName) + ",</p>"
                    + "<p>Your bank-transfer payment has been validated and your order is now placed. "
                    + "Thank you for your business.</p>"
                    + "<p style=\"font-size:13px;color:#6b6b6b;\"><strong>Order:</strong> " + escapeHtml(nullToEmpty(orderRef))
                    + " &nbsp;·&nbsp; <strong>Quote:</strong> " + escapeHtml(nullToEmpty(quoteRef)) + "</p>"
                    + "<p style=\"margin:16px 0 4px;font-weight:bold;color:#1f1b3a;\">Order summary</p>"
                    + b2bItemsTableHtml(currency, total, lines)
                    + b2bTermsHtml(paymentTerms, termsAndConditions)
                    + b2bPartiesHtml(buyerCompany, buyerWebsite, buyerEmail);

            send(toEmail, "Your Buyology B2B order is placed", accountEmailHtml("Order placed", body));
            log.info("B2B order-placed email sent to {}", toEmail);
        } catch (IOException e) {
            log.warn("Could not send B2B order-placed email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── B2B product-sourcing requests ──────────────────────────────────────────

    /** Member confirmation: their product-sourcing request was received. */
    @Async
    public void sendB2bProductRequestReceivedEmail(String toEmail, String memberName, String requestRef,
                                                   String productName, int quantity) {
        try {
            String html = loadTemplate("static/b2b-product-request-received.html")
                    .replace("{{MEMBER_NAME}}", safeName(memberName))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)))
                    .replace("{{QUANTITY}}", String.valueOf(quantity));
            send(toEmail, "We received your product request", html);
            log.info("B2B product-request received email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send B2B product-request received email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Internal notification to procurement: a member submitted a product-sourcing request. */
    @Async
    public void sendB2bProductRequestNotificationEmail(String procurementEmail, String memberName,
                                                       String requestRef, String productName,
                                                       int quantity, String message, String reviewUrl) {
        try {
            String html = loadTemplate("static/b2b-product-request-notification.html")
                    .replace("{{MEMBER_NAME}}", escapeHtml(nullToEmpty(memberName)))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)))
                    .replace("{{QUANTITY}}", String.valueOf(quantity))
                    .replace("{{MESSAGE}}", escapeHtml(nullToEmpty(message)))
                    .replace("{{REVIEW_URL}}", nullToEmpty(reviewUrl));
            send(procurementEmail, "New B2B product request: " + nullToEmpty(productName), html);
            log.info("B2B product-request notification sent to {}", procurementEmail);
        } catch (Exception e) {
            log.warn("Could not send B2B product-request notification to {}: {}", procurementEmail, e.getMessage());
        }
    }

    /**
     * Member notification for a product-request update: a status transition (with a stage
     * label + optional note) OR a free-form "send update" message (stageLabel = "Update",
     * the message passed as note). Silently swallows failures.
     */
    @Async
    public void sendB2bProductRequestStatusEmail(String toEmail, String memberName, String requestRef,
                                                 String productName, String stageLabel, String note) {
        try {
            String html = loadTemplate("static/b2b-product-request-status-update.html")
                    .replace("{{MEMBER_NAME}}", safeName(memberName))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)))
                    .replace("{{STAGE_LABEL}}", escapeHtml(nullToEmpty(stageLabel)))
                    .replace("{{NOTE}}", escapeHtml(nullToEmpty(note)));
            send(toEmail, "Update on your product request: " + nullToEmpty(productName), html);
            log.info("B2B product-request status email ({}) sent to {}", stageLabel, toEmail);
        } catch (Exception e) {
            log.warn("Could not send B2B product-request status email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Device repair requests ─────────────────────────────────────────────────

    /** Customer confirmation: their repair request was received. */
    @Async
    public void sendRepairReceivedEmail(String toEmail, String customerName, String requestRef, String productName) {
        try {
            String html = loadTemplate("static/repair-request-received.html")
                    .replace("{{CUSTOMER_NAME}}", safeName(customerName))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)));
            send(toEmail, "We received your repair request", html);
            log.info("Repair received email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send repair received email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Internal notification to the repair team: a customer submitted a repair request. */
    @Async
    public void sendRepairTeamNotificationEmail(String teamEmail, String customerName, String requestRef,
                                                String productName, String brand, String model,
                                                String description, String reviewUrl) {
        try {
            String html = loadTemplate("static/repair-request-notification.html")
                    .replace("{{CUSTOMER_NAME}}", escapeHtml(nullToEmpty(customerName)))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)))
                    .replace("{{BRAND}}", escapeHtml(nullToEmpty(brand)))
                    .replace("{{MODEL}}", escapeHtml(nullToEmpty(model)))
                    .replace("{{DESCRIPTION}}", escapeHtml(nullToEmpty(description)))
                    .replace("{{REVIEW_URL}}", nullToEmpty(reviewUrl));
            send(teamEmail, "New repair request: " + nullToEmpty(productName), html);
            log.info("Repair team notification sent to {}", teamEmail);
        } catch (Exception e) {
            log.warn("Could not send repair team notification to {}: {}", teamEmail, e.getMessage());
        }
    }

    /**
     * Customer notification for a repair-status change (with a stage label + optional note).
     * Silently swallows failures.
     */
    @Async
    public void sendRepairStatusEmail(String toEmail, String customerName, String requestRef,
                                      String productName, String stageLabel, String note) {
        try {
            String html = loadTemplate("static/repair-status-update.html")
                    .replace("{{CUSTOMER_NAME}}", safeName(customerName))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)))
                    .replace("{{STAGE_LABEL}}", escapeHtml(nullToEmpty(stageLabel)))
                    .replace("{{NOTE}}", escapeHtml(nullToEmpty(note)));
            send(toEmail, "Update on your repair: " + nullToEmpty(productName), html);
            log.info("Repair status email ({}) sent to {}", stageLabel, toEmail);
        } catch (Exception e) {
            log.warn("Could not send repair status email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Customer notification: the repair team quoted a fixing price awaiting their approval. */
    @Async
    public void sendRepairPriceEmail(String toEmail, String customerName, String requestRef,
                                     String productName, String currency, BigDecimal price,
                                     String estimatedTime, String note) {
        try {
            String html = loadTemplate("static/repair-price-estimate.html")
                    .replace("{{CUSTOMER_NAME}}", safeName(customerName))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)))
                    .replace("{{PRICE}}", escapeHtml(money(currency, price)))
                    .replace("{{ESTIMATED_TIME}}", escapeHtml(nullToEmpty(estimatedTime)))
                    .replace("{{NOTE}}", escapeHtml(nullToEmpty(note)));
            send(toEmail, "Your repair estimate is ready: " + nullToEmpty(productName), html);
            log.info("Repair price email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send repair price email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Support tickets ────────────────────────────────────────────────────────

    /** Customer confirmation: their support ticket was received. */
    @Async
    public void sendSupportReceivedEmail(String toEmail, String customerName, String requestRef, String subject) {
        try {
            String html = loadTemplate("static/support-request-received.html")
                    .replace("{{CUSTOMER_NAME}}", escapeHtml(safeName(customerName)))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{SUBJECT}}", escapeHtml(nullToEmpty(subject)));
            send(toEmail, "We received your support ticket", html);
            log.info("Support received email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send support received email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Support-team notification with a dashboard link — used both for new tickets and for
     * customer replies; {@code heading} says which ("New support ticket" / "New customer reply").
     */
    @Async
    public void sendSupportTeamEmail(String teamEmail, String heading, String customerName, String requestRef,
                                     String subject, String category, String body, String reviewUrl) {
        try {
            String html = loadTemplate("static/support-request-notification.html")
                    .replace("{{HEADING}}", escapeHtml(nullToEmpty(heading)))
                    .replace("{{CUSTOMER_NAME}}", escapeHtml(safeName(customerName)))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{SUBJECT}}", escapeHtml(nullToEmpty(subject)))
                    .replace("{{CATEGORY}}", escapeHtml(nullToEmpty(category)))
                    .replace("{{BODY}}", escapeHtml(nullToEmpty(body)))
                    .replace("{{REVIEW_URL}}", nullToEmpty(reviewUrl));
            send(teamEmail, nullToEmpty(heading) + ": " + nullToEmpty(requestRef), html);
            log.info("Support team email ({}) sent to {}", heading, teamEmail);
        } catch (Exception e) {
            log.warn("Could not send support team email to {}: {}", teamEmail, e.getMessage());
        }
    }

    /** Customer status update: the team moved their ticket (with an optional note). */
    @Async
    public void sendSupportStatusEmail(String toEmail, String customerName, String requestRef,
                                       String subject, String statusLabel, String note) {
        try {
            String html = loadTemplate("static/support-status-update.html")
                    .replace("{{CUSTOMER_NAME}}", escapeHtml(safeName(customerName)))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{SUBJECT}}", escapeHtml(nullToEmpty(subject)))
                    .replace("{{STATUS_LABEL}}", escapeHtml(nullToEmpty(statusLabel)))
                    .replace("{{NOTE}}", escapeHtml(nullToEmpty(note)));
            send(toEmail, "Update on your support ticket: " + nullToEmpty(requestRef), html);
            log.info("Support status email ({}) sent to {}", statusLabel, toEmail);
        } catch (Exception e) {
            log.warn("Could not send support status email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Customer notification: the team replied on their ticket. */
    @Async
    public void sendSupportReplyEmail(String toEmail, String customerName, String requestRef,
                                      String subject, String reply) {
        try {
            String html = loadTemplate("static/support-reply.html")
                    .replace("{{CUSTOMER_NAME}}", escapeHtml(safeName(customerName)))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{SUBJECT}}", escapeHtml(nullToEmpty(subject)))
                    .replace("{{REPLY}}", escapeHtml(nullToEmpty(reply)));
            send(toEmail, "Support replied to your ticket: " + nullToEmpty(requestRef), html);
            log.info("Support reply email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send support reply email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Sell (trade-in) requests ───────────────────────────────────────────────
    // Mirrors the repair emails above. Each one no-ops on a blank recipient so callers don't have
    // to null-check a contact address that was snapshotted at submit time.

    /** Customer confirmation: their sell request was received. */
    @Async
    public void sendSellReceivedEmail(String toEmail, String customerName, String requestRef, String productName) {
        if (toEmail == null || toEmail.isBlank()) return;
        try {
            String html = loadTemplate("static/sell-request-received.html")
                    .replace("{{CUSTOMER_NAME}}", safeName(customerName))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)));
            send(toEmail, "We received your sell request", html);
            log.info("Sell received email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send sell received email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Internal notification to procurement: a customer wants to sell a device. */
    @Async
    public void sendSellTeamNotificationEmail(String teamEmail, String customerName, String requestRef,
                                              String productName, String brand, String model,
                                              String condition, String description, String reviewUrl) {
        if (teamEmail == null || teamEmail.isBlank()) return;
        try {
            String html = loadTemplate("static/sell-request-notification.html")
                    .replace("{{CUSTOMER_NAME}}", escapeHtml(nullToEmpty(customerName)))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)))
                    .replace("{{BRAND}}", escapeHtml(nullToEmpty(brand)))
                    .replace("{{MODEL}}", escapeHtml(nullToEmpty(model)))
                    .replace("{{CONDITION}}", escapeHtml(nullToEmpty(condition)))
                    .replace("{{DESCRIPTION}}", escapeHtml(nullToEmpty(description)))
                    .replace("{{REVIEW_URL}}", nullToEmpty(reviewUrl));
            send(teamEmail, "New sell request: " + nullToEmpty(productName), html);
            log.info("Sell team notification sent to {}", teamEmail);
        } catch (Exception e) {
            log.warn("Could not send sell team notification to {}: {}", teamEmail, e.getMessage());
        }
    }

    /**
     * Customer notification for a sell-request status change (with a stage label + optional note).
     * Silently swallows failures.
     */
    @Async
    public void sendSellStatusEmail(String toEmail, String customerName, String requestRef,
                                    String productName, String stageLabel, String note) {
        if (toEmail == null || toEmail.isBlank()) return;
        try {
            String html = loadTemplate("static/sell-status-update.html")
                    .replace("{{CUSTOMER_NAME}}", safeName(customerName))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)))
                    .replace("{{STAGE_LABEL}}", escapeHtml(nullToEmpty(stageLabel)))
                    .replace("{{NOTE}}", escapeHtml(nullToEmpty(note)));
            send(toEmail, "Update on your sell request: " + nullToEmpty(productName), html);
            log.info("Sell status email ({}) sent to {}", stageLabel, toEmail);
        } catch (Exception e) {
            log.warn("Could not send sell status email to {}: {}", toEmail, e.getMessage());
        }
    }

    /** Customer notification: procurement quoted what Buyology will pay, awaiting their approval. */
    @Async
    public void sendSellOfferEmail(String toEmail, String customerName, String requestRef,
                                   String productName, String currency, BigDecimal price,
                                   String validFor, String note) {
        if (toEmail == null || toEmail.isBlank()) return;
        try {
            String html = loadTemplate("static/sell-offer.html")
                    .replace("{{CUSTOMER_NAME}}", safeName(customerName))
                    .replace("{{REQUEST_REF}}", nullToEmpty(requestRef))
                    .replace("{{PRODUCT_NAME}}", escapeHtml(nullToEmpty(productName)))
                    .replace("{{PRICE}}", escapeHtml(money(currency, price)))
                    .replace("{{VALID_FOR}}", escapeHtml(nullToEmpty(validFor)))
                    .replace("{{NOTE}}", escapeHtml(nullToEmpty(note)));
            send(toEmail, "Your Buyology offer is ready: " + nullToEmpty(productName), html);
            log.info("Sell offer email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send sell offer email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── Order confirmation & status updates ────────────────────────────────────

    /** A single line on the order-confirmation email. */
    public record OrderEmailItem(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}

    @Async
    public void sendOrderConfirmationEmail(
            String toEmail, String recipientName, String orderNumber, String orderDate,
            List<OrderEmailItem> items, String currency,
            BigDecimal subtotal, BigDecimal shipping, BigDecimal discount, BigDecimal total,
            String deliveryAddress, String estimatedDelivery, int deviceCount, String orderUrl) {
        try {
            final int co2PerDevice = 300; // good-faith estimate: kg CO2e avoided per refurbished device vs new
            long co2Total = (long) co2PerDevice * Math.max(deviceCount, 1);

            String discountRow = (discount != null && discount.signum() > 0)
                    ? "<tr><td style=\"padding:4px 0;color:#16a34a;font-size:14px;\">Discount</td>"
                      + "<td align=\"right\" style=\"padding:4px 0;color:#16a34a;font-size:14px;\">-"
                      + money(currency, discount) + "</td></tr>"
                    : "";
            String shippingDisplay = (shipping == null || shipping.signum() == 0)
                    ? "FREE" : money(currency, shipping);

            String html = loadTemplate("static/order-confirmation.html")
                    .replace("{{RECIPIENT_NAME}}", safeName(recipientName))
                    .replace("{{ORDER_NUMBER}}", nullToEmpty(orderNumber))
                    .replace("{{ORDER_DATE}}", nullToEmpty(orderDate))
                    .replace("{{ITEMS_ROWS}}", buildOrderItemRows(items, currency))
                    .replace("{{DISCOUNT_ROW}}", discountRow)
                    .replace("{{SUBTOTAL}}", money(currency, subtotal))
                    .replace("{{SHIPPING}}", shippingDisplay)
                    .replace("{{TOTAL}}", money(currency, total))
                    .replace("{{DELIVERY_ADDRESS}}", nullToEmpty(deliveryAddress))
                    .replace("{{ESTIMATED_DELIVERY}}", nullToEmpty(estimatedDelivery))
                    .replace("{{CO2_TOTAL}}", String.valueOf(co2Total))
                    .replace("{{CO2_PER_DEVICE}}", String.valueOf(co2PerDevice))
                    .replace("{{ORDER_URL}}", nullToEmpty(orderUrl));
            send(toEmail, "Your Buyology order " + nullToEmpty(orderNumber) + " is confirmed", html);
            log.info("Order confirmation email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Could not send order confirmation email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Per-status customer email for fulfilment milestones. Takes the OrderStatus name as a
     * String to keep this common service decoupled from the order module. Silently no-ops for
     * statuses without a customer-facing milestone (PAID and CANCELLED are emailed elsewhere).
     */
    @Async
    public void sendOrderStatusEmail(String toEmail, String recipientName, String orderNumber,
                                     String statusName, String orderUrl) {
        sendOrderStatusEmail(toEmail, recipientName, orderNumber, statusName, orderUrl, null);
    }

    /**
     * Per-status fulfilment email. {@code pickupLocation} (store name/address) is only used for the
     * READY_FOR_PICKUP status, so the customer knows where to collect their order.
     */
    public void sendOrderStatusEmail(String toEmail, String recipientName, String orderNumber,
                                     String statusName, String orderUrl, String pickupLocation) {
        try {
            String title, message, badge, color;
            switch (statusName == null ? "" : statusName) {
                case "PACKAGING" -> {
                    badge = "Packing"; color = "#7c5cff";
                    title = "We're packing your order";
                    message = "your items are being carefully prepared for dispatch.";
                }
                case "READY_FOR_PICKUP" -> {
                    badge = "Ready for pickup"; color = "#ea580c";
                    title = "Your order is ready to collect";
                    message = (pickupLocation != null && !pickupLocation.isBlank())
                            ? "your order is packed and ready for collection at " + escapeHtml(pickupLocation)
                                + ". Please bring your order number when you come to pick it up."
                            : "your order is packed and ready for collection at the store. "
                                + "Please bring your order number when you come to pick it up.";
                }
                case "IN_COURIER" -> {
                    badge = "With courier"; color = "#2563eb";
                    title = "Your order is with the courier";
                    message = "your order has been handed to our delivery partner and is on its way to you.";
                }
                case "IN_TRANSIT" -> {
                    badge = "In transit"; color = "#0891b2";
                    title = "Your order is on the way";
                    message = "your order is in transit and heading to your address.";
                }
                case "DELIVERED" -> {
                    badge = "Delivered"; color = "#16a34a";
                    title = "Your order has been delivered";
                    message = "your order has been delivered. We hope you love it — enjoy!";
                }
                default -> { return; }
            }
            String html = loadTemplate("static/order-status-update.html")
                    .replace("{{RECIPIENT_NAME}}", safeName(recipientName))
                    .replace("{{STATUS_TITLE}}", title)
                    .replace("{{STATUS_MESSAGE}}", message)
                    .replace("{{STATUS_BADGE}}", badge)
                    .replace("{{STATUS_COLOR}}", color)
                    .replace("{{ORDER_NUMBER}}", nullToEmpty(orderNumber))
                    .replace("{{ORDER_URL}}", nullToEmpty(orderUrl));
            send(toEmail, "Order " + nullToEmpty(orderNumber) + " — " + title, html);
            log.info("Order status ({}) email sent to {}", statusName, toEmail);
        } catch (Exception e) {
            log.warn("Could not send order status email to {}: {}", toEmail, e.getMessage());
        }
    }

    private static String buildOrderItemRows(List<OrderEmailItem> items, String currency) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (OrderEmailItem it : items) {
            sb.append("<tr>")
              // Item name + a "Qty x Unit price" line beneath it (invoice detail).
              .append("<td style=\"padding:10px 0;border-bottom:1px solid #f1f0f8;\">")
              .append("<span style=\"color:#333;font-size:14px;\">").append(escapeHtml(it.name())).append("</span>")
              .append("<br/><span style=\"color:#999;font-size:12px;\">")
              .append(it.quantity()).append(" &times; ").append(money(currency, it.unitPrice()))
              .append("</span>")
              .append("</td>")
              .append("<td align=\"right\" valign=\"top\" style=\"padding:10px 0;color:#333;font-size:14px;border-bottom:1px solid #f1f0f8;white-space:nowrap;\">")
              .append(money(currency, it.lineTotal()))
              .append("</td></tr>");
        }
        return sb.toString();
    }

    private static String money(String currency, BigDecimal amount) {
        BigDecimal a = (amount == null ? BigDecimal.ZERO : amount).setScale(2, java.math.RoundingMode.HALF_UP);
        return nullToEmpty(currency) + " " + a.toPlainString();
    }

    /**
     * Sends an admin's own message to a customer about a payment that did not complete.
     *
     * <p>Deliberately synchronous and returning whether it sent, unlike the @Async notifications
     * around it. An admin clicked send and is watching, and the outreach log records "email sent"
     * against this result — a fire-and-forget call would make that record a guess, and a log that
     * claims we contacted someone we didn't is worse than a slower button.
     */
    public boolean sendOrderPaymentMessageEmail(String toEmail, String customerName, String subject,
                                                String messageBody, String orderNumber,
                                                String orderTotal, String repayUrl) {
        try {
            // The body is typed into a plain-text box by an admin. Escaping here rather than
            // trusting the caller means a stray "<" in "under <100 AED" renders as text instead of
            // silently swallowing the rest of the sentence.
            String bodyHtml = escapeHtml(messageBody).replace("\n", "<br />");
            String html = loadTemplate("static/order-payment-message.html")
                    .replace("{{SUBJECT}}", escapeHtml(subject))
                    .replace("{{CUSTOMER_NAME}}", escapeHtml(safeName(customerName)))
                    .replace("{{MESSAGE_BODY}}", bodyHtml)
                    .replace("{{ORDER_NUMBER}}", escapeHtml(nullToEmpty(orderNumber)))
                    .replace("{{ORDER_TOTAL}}", escapeHtml(nullToEmpty(orderTotal)))
                    .replace("{{REPAY_URL}}", nullToEmpty(repayUrl));
            send(toEmail, subject, html);
            return true;
        } catch (Exception e) {
            log.warn("Could not send order payment message to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String safeName(String name) {
        return (name == null || name.isBlank()) ? "there" : name;
    }

    /** Minimal branded HTML wrapper for transactional account emails. */
    private static String accountEmailHtml(String heading, String bodyHtml) {
        return "<!DOCTYPE html><html><body style=\"margin:0;background:#f5f4fb;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\" style=\"padding:24px;\">"
                + "<table width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#ffffff;border-radius:16px;overflow:hidden;\">"
                + "<tr><td style=\"background:#402F75;padding:24px 32px;\">"
                + "<span style=\"color:#FBBB14;font-size:22px;font-weight:bold;letter-spacing:1px;\">BUYOLOGY</span></td></tr>"
                + "<tr><td style=\"padding:28px 32px;color:#2b2b2b;font-size:15px;line-height:1.6;\">"
                + "<h2 style=\"margin:0 0 12px;color:#402F75;font-size:20px;\">" + heading + "</h2>"
                + bodyHtml
                + "<p style=\"margin-top:24px;color:#888;font-size:12px;\">Buyology FZ Trading LLC · United Arab Emirates · support@buyology.com</p>"
                + "</td></tr></table></td></tr></table></body></html>";
    }

    private String loadTemplate(String classpathPath) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathPath);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
