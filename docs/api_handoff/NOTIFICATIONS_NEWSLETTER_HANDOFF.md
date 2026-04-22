# Notifications & Newsletters Integration Guide

This document provides a comprehensive overview of how notifications (Signup, Promo, Newsletter) are handled in the Buyology backend and how to integrate them.

---

## 1. User Signup & Verification
The signup flow uses email-based OTP for verification.

- **Initiate Signup**: `POST /auth/signup`
  - Sends a 6-digit verification code to the user's email.
- **Verify OTP**: `POST /auth/verify-otp`
  - On success, creates the user account and sends a **Welcome Email** (Template: `email.html`).
- **Data Flow**:
  - Verification Email (SendGrid) -> Welcome Email (SendGrid) -> Sign-in tokens returned.

---

## 2. Newsletter System
Used for marketing updates and news articles.

### Subscriber Management
- **Subscribe**: `POST /api/newsletter/subscribe?email=user@example.com`
  - Sends a **Subscription Confirmation Email** (Template: `newsletter-subscription-confirmation.html`).
- **Unsubscribe**: `GET /api/newsletter/unsubscribe?token={uuid}`
  - Deactivates the subscription.

### News & Broadcasting
- **Create Article**: `POST /api/admin/newsletter/articles` (Admin Only)
- **Broadcast**: `POST /api/admin/newsletter/articles/{id}/publish?send=true`
  - Publishes the article and sends it to all active subscribers via email (Template: `newsletter-email.html`).

---

## 3. Promo Code Notifications
Admins can push promo codes to specific users or all customers.

- **Send Promo**: `POST /api/admin/promo/{id}/send`
  - **Body**:
    ```json
    {
      "targetUserIds": ["uuid1", "uuid2"], // null for all customers
      "sendEmail": true,
      "sendPush": true
    }
    ```
- **Delivery Channels**:
  - **Email**: Sends a localized offer email (Template: `sendPromoCodeEmail` in `EmailService`).
  - **Push Notification**: Sends a standard FCM push with the promo code in the data payload.

---

## 4. Push Notification History (New)
As per recent updates, all push notifications (including Promo and Chat) are now stored in the **Notification History** for the user's in-app inbox.

- **Fetch Inbox**: `GET /api/v1/notifications/history`
- **Fields**:
  - `title`: e.g., "Exclusive offer for you!"
  - `body`: e.g., "Use code SAVE20 to get 20% off..."
  - `type`: `PROMO`, `CHAT_MESSAGE`, `ORDER_STATUS`, etc.
  - `isRead`: Status of the notification.

---

## 5. Summary of Email Templates
All email templates are located in `src/main/resources/static/`:
- `email.html`: General welcome/success.
- `otp-email.html`: Verification codes.
- `newsletter-email.html`: Broadcasted news articles.
- `newsletter-subscription-confirmation.html`: Welcome for subscribers.
- `streak-reminder-email.html`: Daily game reminders.

---

## 6. Checklist for Developers
- [ ] Ensure `SPRING_SENDGRID_API_KEY` is set in the environment for emails to work.
- [ ] Ensure `FIREBASE_CONFIG_PATH` is set for push notifications.
- [ ] Use `GET /api/v1/notifications/unread-count` to show a red dot/badge on the notification icon.
- [ ] On the Notification Screen, show items from `/history` and call `/read` when an item is tapped.
