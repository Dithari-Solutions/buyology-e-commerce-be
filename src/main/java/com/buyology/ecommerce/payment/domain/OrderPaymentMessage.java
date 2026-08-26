package com.buyology.ecommerce.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One message an admin sent a customer about a payment that did not complete.
 *
 * <p>Nothing here is sent automatically. The customer is contacted only when an admin decides to,
 * from the order they are looking at — which is what keeps the system from ever chasing someone
 * whose instalment payment had in fact already gone through.
 */
@Entity
@Table(name = "order_payment_messages")
public class OrderPaymentMessage {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** Null when the admin wrote the message themselves rather than picking a template. */
    @Column(name = "template_key", length = 60)
    private String templateKey;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    /** Stored exactly as the admin typed it. Escaping happens at render, so the log stays truthful. */
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    /** What the system believed was wrong at the moment of contact — context the log would lose. */
    @Column(name = "diagnosis_code", length = 60)
    private String diagnosisCode;

    @Column(name = "sent_by")
    private UUID sentBy;

    /** Snapshotted: admins leave, and a log that says "sent by <deleted>" answers nothing. */
    @Column(name = "sent_by_name", length = 150)
    private String sentByName;

    @Column(name = "email_sent", nullable = false)
    private boolean emailSent = false;

    @Column(name = "notification_sent", nullable = false)
    private boolean notificationSent = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getDiagnosisCode() { return diagnosisCode; }
    public void setDiagnosisCode(String diagnosisCode) { this.diagnosisCode = diagnosisCode; }
    public UUID getSentBy() { return sentBy; }
    public void setSentBy(UUID sentBy) { this.sentBy = sentBy; }
    public String getSentByName() { return sentByName; }
    public void setSentByName(String sentByName) { this.sentByName = sentByName; }
    public boolean isEmailSent() { return emailSent; }
    public void setEmailSent(boolean emailSent) { this.emailSent = emailSent; }
    public boolean isNotificationSent() { return notificationSent; }
    public void setNotificationSent(boolean notificationSent) { this.notificationSent = notificationSent; }
    public Instant getCreatedAt() { return createdAt; }
}
