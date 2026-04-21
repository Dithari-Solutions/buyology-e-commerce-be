# File Upload Validation & Error Handling

This document describes the security measures and error responses for file uploads in the Buyology E-Commerce system.

## 1. Validation Logic
All file uploads are processed through `FileValidationUtils` before being saved to storage. The validation involves four layers:
1.  **Extension Whitelisting**: Only specific extensions are allowed (e.g., `.jpg`, `.png`, `.mp4`).
2.  **MIME-Type Verification**: The `Content-Type` header is checked against allowed media types.
3.  **Script Scanning**: The first 200 lines of every file are scanned for malicious signatures (PHP tags, Bash shebangs, Javascript `<script>` tags, etc.).
4.  **Integrity Check**: For images, `ImageIO` is used to verify that the file is a valid, uncorrupted image.

## 2. Error Status Codes

| Status Code | Error Message | Description |
| :--- | :--- | :--- |
| **400 Bad Request** | `Invalid file type. Only images are allowed.` | Occurs when a non-image file (e.g., `.pdf`, `.zip`) is uploaded to an image-only field. |
| **400 Bad Request** | `File contains potentially malicious content: <signature>` | Occurs when a script signature (like `<?php` or `#!/bin/bash`) is detected inside the file. |
| **400 Bad Request** | `Invalid image file or corrupted content` | Occurs when a file has an image extension but its binary structure is not a valid image. |
| **400 Bad Request** | `File must have an extension` | Occurs when the uploaded filename is missing an extension. |
| **413 Payload Too Large** | `Upload size exceeds the maximum allowed limit` | Occurs when the file exceeds the system's `max-file-size` (configured in `application.properties`). |

## 3. Feature-Specific Rules

### Stories Feature
- **Thumbnails**: Strict **Image Only**.
- **Media Files**: Allows **Images and Videos** (`mp4`, `mov`, `avi`, `webm`).

### All Other Features (Products, Profile, Reviews, etc.)
- Strict **Image Only** policy. Videos and other file formats are rejected.

## 4. Implementation Details
The errors are caught by the `GlobalExceptionHandler`, which converts the internal `FileValidationException` into a standard `ApiResponse` format:

```json
{
  "success": false,
  "message": "File contains potentially malicious content: <?php",
  "data": null
}
```
