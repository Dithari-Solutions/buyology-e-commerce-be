# Newsletter Subscription Integration Guide

This document describes how to integrate the newsletter subscription and unsubscription features on the web frontend.

---

## 1. Subscribe to Newsletter

Use this endpoint to add a new email address to the newsletter distribution list.

**Endpoint:** `POST /api/newsletter/subscribe`  
**Auth:** None (Public endpoint).

### Request Body
The request expects a JSON object containing the `email` field.

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `email` | string | Yes | Valid email address |

**Example Request:**
```json
{
  "email": "customer@example.com"
}
```

### Response
- **200 OK:** Returns "Subscribed successfully" or "Already subscribed".
- **400 Bad Request:** If the email format is invalid.

**Example Response:**
```json
{
  "status": 200,
  "message": "OK",
  "data": "Subscribed successfully"
}
```

---

## 2. Unsubscribe from Newsletter

The unsubscribe link is typically sent within the newsletter emails. The frontend should have a dedicated route (e.g., `/unsubscribe`) that handles the token from the URL.

**Endpoint:** `GET /api/newsletter/unsubscribe`  
**Auth:** None (Token-based).

### Query Parameters
| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `token` | UUID | Yes | The unique unsubscribe token sent in the email |

**Example Request:**
`GET /api/newsletter/unsubscribe?token=550e8400-e29b-41d4-a716-446655440000`

---

## 3. Recommended Frontend Implementation

### Footer Subscription Form
1.  Add an input field and a "Subscribe" button in the website footer.
2.  On button click, validate the email format client-side.
3.  Send the `POST` request to the backend.
4.  Display a "Thank you for subscribing!" toast or message on success.

### Unsubscribe Page
1.  Create a route: `/unsubscribe?token=...`
2.  On component mount, extract the `token` from the URL parameters.
3.  Call the `GET /api/newsletter/unsubscribe` endpoint.
4.  Show a confirmation message: "You have been successfully removed from our mailing list."

---

## 4. Admin Notes
Articles and newsletters are managed via the Admin Dashboard. These endpoints are available under `/api/admin/newsletter/**` and require `ADMIN` roles.
