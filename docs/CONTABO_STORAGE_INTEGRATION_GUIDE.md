# Contabo Object Storage Integration Guide

This document outlines the changes and integration steps for the Frontend (Website/Mobile) and Admin Dashboard following the migration from local filesystem storage to Contabo Object Storage (S3-compatible).

---

## 1. Overview of Changes

The backend now stores all product-related media in **Contabo Object Storage**.
- **Old Path Format:** `/product/{product_uuid}/{filename}` (served by the backend)
- **New Path Format:** `https://eu2.contabostorage.com/ecommerce-storage/products/{product_uuid}/{filename}` (served directly by Contabo S3)

---

## 2. Frontend Integration (Website & Mobile App)

### Image URL Handling
The `ProductResponse` DTO now returns **absolute URLs** instead of relative paths in the `url` and `thumbnail_url` fields.

**Example Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "media": [
    {
      "id": "...",
      "mediaType": "IMAGE",
      "url": "https://eu2.contabostorage.com/ecommerce-storage/products/550e8400.../product_0.jpg",
      "isPrimary": true
    }
  ]
}
```

**Required Changes:**
- **No Path Prefixing:** Ensure the frontend **does not** prefix image URLs with the Backend API base URL. If you were doing `API_BASE_URL + media.url`, you must remove the prefix.
- **Direct S3 Access:** The frontend now fetches images directly from Contabo's infrastructure. This reduces load on the backend server and improves image loading speeds via their global edge.

---

## 3. Admin Dashboard Integration

### Product Creation (POST)
The API endpoint for creating products remains the same. You still send the `CreateProductRequest` as a `multipart/form-data` request with a list of files.

**Workflow:**
1. Admin uploads files via the dashboard.
2. Backend receives files, generates a UUID for the product.
3. Backend creates a folder `products/{uuid}/` in Contabo S3.
4. Backend uploads files with clean naming (e.g., `product_0.jpg`, `color_{id}_0.jpg`).
5. Backend returns the full S3 URLs in the success response.

### Image Management
- **Naming Convention:** Images are automatically named by the backend to ensure consistency.
  - Product-level images: `product_{index}.{ext}`
  - Color-specific images: `color_{color_option_id}_{index}.{ext}`
- **Automatic Cleanup:** When a product is **Hard Deleted** (purged from trash), the backend automatically deletes the entire `products/{uuid}/` folder from Contabo S3. No manual cleanup is required by the admin.

---

## 4. Technical Details for Developers

| Feature | Detail |
|---------|--------|
| **S3 Provider** | Contabo Object Storage |
| **Region** | EU2 (or as configured) |
| **ACL** | `public-read` (All uploaded images are publicly accessible) |
| **Base URL** | `${CONTABO_S3_PUBLIC_URL}` |
| **Folder Structure** | `products/{product_uuid}/` |

---

## 5. Troubleshooting

If images are not appearing:
1. **Check the URL:** Copy the URL from the API response and paste it into a browser. If it doesn't open, verify the `CONTABO_S3_PUBLIC_URL` environment variable.
2. **CORS:** If you see CORS errors in the browser console, ensure that CORS is enabled in the Contabo Control Panel for your bucket, allowing your frontend domain (e.g., `https://dithari.com`).
3. **Permissions:** Ensure the `CONTABO_S3_ACCESS_KEY` used by the backend has `PutObject` and `PutObjectAcl` permissions.
