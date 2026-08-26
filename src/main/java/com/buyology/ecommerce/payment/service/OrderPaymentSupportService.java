package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.payment.domain.OrderPaymentMessage;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.dto.PaymentStallDiagnosis;
import com.buyology.ecommerce.payment.dto.PaymentSupportDtos.MessageTemplate;
import com.buyology.ecommerce.payment.dto.PaymentSupportDtos.PaymentAttempt;
import com.buyology.ecommerce.payment.dto.PaymentSupportDtos.PaymentMessage;
import com.buyology.ecommerce.payment.dto.PaymentSupportDtos.PaymentSupportView;
import com.buyology.ecommerce.payment.dto.PaymentSupportDtos.SendMessageRequest;
import com.buyology.ecommerce.payment.repository.OrderPaymentMessageRepository;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The admin's side of a payment that did not complete: why it stalled, what has been tried, and
 * the record of anything we said to the customer about it.
 *
 * <p>Nothing here fires on a schedule. The customer is contacted only when an admin decides to,
 * from the order in front of them — which is the whole point. An automatic reminder cannot know
 * that the Tabby customer it is about to chase has in fact already paid and is simply waiting on a
 * settlement webhook; an admin looking at a panel that says so in the first line can.
 */
@Service
public class OrderPaymentSupportService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentSupportService.class);

    private final OrderRepository orderRepo;
    private final PaymentTransactionRepository transactionRepo;
    private final OrderPaymentMessageRepository messageRepo;
    private final PaymentStallDiagnoser diagnoser;
    private final EmailService emailService;
    private final PushNotificationService pushService;
    private final AuthCredentialRepository authCredentialRepo;
    private final UserRepository userRepo;
    private final String webBaseUrl;

    public OrderPaymentSupportService(OrderRepository orderRepo,
                                      PaymentTransactionRepository transactionRepo,
                                      OrderPaymentMessageRepository messageRepo,
                                      PaymentStallDiagnoser diagnoser,
                                      EmailService emailService,
                                      PushNotificationService pushService,
                                      AuthCredentialRepository authCredentialRepo,
                                      UserRepository userRepo,
                                      @Value("${app.web-base-url:https://buyology.online}") String webBaseUrl) {
        this.orderRepo = orderRepo;
        this.transactionRepo = transactionRepo;
        this.messageRepo = messageRepo;
        this.diagnoser = diagnoser;
        this.emailService = emailService;
        this.pushService = pushService;
        this.authCredentialRepo = authCredentialRepo;
        this.userRepo = userRepo;
        this.webBaseUrl = webBaseUrl;
    }

    // =========================================================================
    // Read
    // =========================================================================

    @Transactional(readOnly = true)
    public PaymentSupportView getSupportView(UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        List<PaymentTransaction> transactions = transactionRepo.findAllByAppOrderId(orderId);
        PaymentStallDiagnosis diagnosis = diagnoser.diagnose(transactions);

        List<PaymentAttempt> attempts = transactions.stream()
                .sorted(Comparator.comparing(
                        (PaymentTransaction t) -> t.getCreatedAt() == null
                                ? java.time.Instant.EPOCH : t.getCreatedAt()).reversed())
                .map(t -> new PaymentAttempt(
                        t.getId(),
                        t.getMethodType() == null ? null : t.getMethodType().name(),
                        t.getStatus() == null ? null : t.getStatus().name(),
                        t.getAmount(), t.getCurrency(),
                        t.getFailureReason(), t.getFailureCode(),
                        t.getPaymobTransactionId(),
                        // The single most diagnostic bit: did they ever get as far as the gateway?
                        t.getIntentionId() != null,
                        t.getCreatedAt()))
                .toList();

        List<PaymentMessage> messages = messageRepo.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(m -> new PaymentMessage(m.getId(), m.getTemplateKey(), m.getSubject(), m.getBody(),
                        m.getDiagnosisCode(), m.getSentByName(), m.isEmailSent(),
                        m.isNotificationSent(), m.getCreatedAt()))
                .toList();

        String email = customerEmail(order);
        // Contacting is offered only where it could help: an unpaid order, a customer we can
        // actually reach, and a stall that is not "they already paid, we're waiting on Paymob".
        boolean canContact = email != null
                && order.getStatus() == OrderStatus.PENDING_PAYMENT
                && !diagnosis.customerHasPaid();

        return new PaymentSupportView(diagnosis, attempts, messages, templates(order),
                email, canContact, repayUrl(orderId));
    }

    // =========================================================================
    // Contact
    // =========================================================================

    /**
     * Sends the admin's message to the customer and records it against the order.
     *
     * <p>The email and the in-app notification are both best-effort and recorded separately: a
     * customer with no push tokens still gets the email, and the log says which of the two
     * actually happened rather than implying both did.
     */
    @Transactional
    public PaymentMessage sendMessage(UUID orderId, SendMessageRequest req, UUID adminUserId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        String email = customerEmail(order);
        if (email == null) {
            throw new IllegalStateException(
                    "This order has no contactable email address, so there is nobody to send to.");
        }

        List<PaymentTransaction> transactions = transactionRepo.findAllByAppOrderId(orderId);
        PaymentStallDiagnosis diagnosis = diagnoser.diagnose(transactions);
        // The guard that makes admin-only outreach worth having. Refusing here means a settled
        // instalment payment cannot be chased even by a misclick.
        if (diagnosis.customerHasPaid()) {
            throw new IllegalStateException(
                    "This customer has already paid (" + diagnosis.stage() + "). "
                            + "Sending a payment message would be wrong — re-check the payment instead.");
        }

        String adminName = adminDisplayName(adminUserId);
        String subject = req.subject().trim();
        String body = req.body().trim();

        boolean emailSent = emailService.sendOrderPaymentMessageEmail(
                email, order.getRecipientFirstName(), subject, body,
                displayNumber(order), money(order), repayUrl(orderId));

        boolean notified = false;
        if (order.getUserId() != null) {
            try {
                // Puts the same message in the storefront notification bell, so a customer who
                // never opens the email still sees it where they already look.
                pushService.sendToUser(order.getUserId(), subject, body, "PAYMENT_HELP",
                        Map.of("orderId", orderId.toString(), "type", "PAYMENT_HELP"));
                notified = true;
            } catch (Exception e) {
                log.warn("[PAYMENT-SUPPORT] In-app notification failed for order {}: {}",
                        orderId, e.getMessage());
            }
        }

        OrderPaymentMessage saved = new OrderPaymentMessage();
        saved.setOrderId(orderId);
        saved.setTemplateKey(req.templateKey());
        saved.setSubject(subject);
        saved.setBody(body);
        saved.setDiagnosisCode(diagnosis.code());
        saved.setSentBy(adminUserId);
        saved.setSentByName(adminName);
        saved.setEmailSent(emailSent);
        saved.setNotificationSent(notified);
        saved = messageRepo.save(saved);

        log.info("[PAYMENT-SUPPORT] Order {} — {} contacted the customer about {} (email={}, bell={})",
                orderId, adminName, diagnosis.code(), emailSent, notified);

        return new PaymentMessage(saved.getId(), saved.getTemplateKey(), saved.getSubject(),
                saved.getBody(), saved.getDiagnosisCode(), saved.getSentByName(),
                saved.isEmailSent(), saved.isNotificationSent(), saved.getCreatedAt());
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Starting points, not finished messages. Each is written to be sendable as-is but expects
     * editing — the admin is the one who knows what happened on the phone call.
     */
    private List<MessageTemplate> templates(Order order) {
        String no = displayNumber(order);
        return List.of(
                new MessageTemplate("PAYMENT_NOT_COMPLETED",
                        "Payment wasn't completed",
                        "Your order " + no + " is waiting for payment",
                        "We're holding your order " + no + ", but the payment wasn't completed so "
                                + "nothing has been charged.\n\nYou can finish it from your account "
                                + "whenever you're ready — everything is exactly as you left it."),
                new MessageTemplate("CARD_DECLINED",
                        "Card was declined",
                        "We couldn't take payment for order " + no,
                        "Your bank declined the payment for order " + no + ", so nothing was "
                                + "charged.\n\nThis usually clears up by trying again, using a "
                                + "different card, or approving the payment in your banking app. "
                                + "Your order is still saved."),
                new MessageTemplate("VERIFICATION_INCOMPLETE",
                        "Bank verification unfinished",
                        "One step left on your order " + no,
                        "Your payment for order " + no + " stopped at your bank's verification "
                                + "step, so it didn't go through and nothing was charged.\n\nWhen "
                                + "you try again, keep your phone nearby for the code your bank "
                                + "sends — that's the step that needs finishing."),
                new MessageTemplate("OFFER_HELP",
                        "Offer to help directly",
                        "Can we help you complete order " + no + "?",
                        "We noticed the payment for order " + no + " hasn't gone through, and we'd "
                                + "rather help than leave you trying.\n\nJust reply to this message "
                                + "and we'll sort it out with you.")
        );
    }

    private String repayUrl(UUID orderId) {
        return webBaseUrl + "/account/orders/" + orderId;
    }

    private static String displayNumber(Order order) {
        return "BUY-" + order.getId().toString().substring(0, 8).toUpperCase();
    }

    private static String money(Order order) {
        if (order.getTotalAmount() == null) return "";
        return (order.getCurrency() == null ? "" : order.getCurrency() + " ") + order.getTotalAmount();
    }

    /** Snapshotted onto the log row: admins leave, and "sent by &lt;deleted&gt;" answers nothing. */
    private String adminDisplayName(UUID adminUserId) {
        if (adminUserId == null) return "Admin";
        return userRepo.findById(adminUserId)
                .map(u -> ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                        + (u.getLastName() == null ? "" : u.getLastName())).trim())
                .filter(n -> !n.isBlank())
                .orElse("Admin");
    }

    private String customerEmail(Order order) {
        if (order.getUserId() == null) return null;
        return authCredentialRepo.findByUserId(order.getUserId()).stream()
                .map(AuthCredentials::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst().orElse(null);
    }
}
