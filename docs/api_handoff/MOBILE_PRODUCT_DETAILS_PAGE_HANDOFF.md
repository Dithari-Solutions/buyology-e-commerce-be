# Product Details Page — Mobile Handoff

This document outlines the API integration requirements for the Customer Mobile App's Product Details Page. This page includes product information, specifications, reviews, stats, and Q&A.

## 1. Get Product Core Details
Fetches title, description, images, videos, variants, colors, and accessories.

- **Endpoint:** `GET /api/product/{productId}`
- **Query Params:**
  - `lang`: `AZ` | `EN` | `AR` (Required)
  - `countryCode`: `UAE`, `AZE`, etc. (Optional - returns store prices)
  - `currency`: `AZN`, `AED`, etc. (Optional)
  - `lat`/`lng`: (Optional - for express delivery badge)

### Response Snippet (`ProductResponse`)
```json
{
  "id": "uuid",
  "title": "Product Title",
  "description": "HTML or Plain Text description",
  "brandName": "Apple",
  "media": [
    { "mediaType": "IMAGE", "url": "...", "isPrimary": true },
    { "mediaType": "VIDEO", "url": "..." }
  ],
  "specs": [
    {
      "groupName": "Display",
      "options": [ { "label": "Screen Size", "value": "6.7", "unit": "INCH" } ]
    }
  ],
  "colors": [ { "name": "Space Gray", "hexCode": "#535150" } ],
  "variants": [ { "sku": "IPH-15-128", "specOptionIds": ["uuid"] } ],
  "storePrice": 1299.00,
  "currency": "AED",
  "expressDelivery": true
}
```

---

## 2. Product Reviews & Stats
Reviews are fetched separately from the core product details.

### 2.1 Get Stats (Rating Overview)
- **Endpoint:** `GET /api/reviews/product/{productId}/stats`
- **Response:**
  ```json
  {
    "averageRating": 4.8,
    "totalReviews": 150,
    "ratingDistribution": { "5": 120, "4": 20, "3": 5, "2": 3, "1": 2 }
  }
  ```

### 2.2 Get Approved Reviews
- **Endpoint:** `GET /api/reviews/product/{productId}`
- **Query Params:** `page`, `size`
- **Note:** Includes verified purchase badges and admin replies.

---

## 3. Product Questions (Q&A)
- **Endpoint:** `GET /api/questions/product/{productId}`
- **Query Params:** `page`, `size`
- **Response:** List of questions and their official admin answers.

---

## 4. User Interactions
- **Submit Review:** `POST /api/reviews` (Multipart - image validation enforced)
- **Ask Question:** `POST /api/questions`
- **Vote (Helpful):** `POST /api/reviews/{id}/vote` or `POST /api/questions/{id}/vote`
